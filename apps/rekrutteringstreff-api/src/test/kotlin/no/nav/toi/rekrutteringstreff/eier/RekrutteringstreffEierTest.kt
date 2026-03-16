package no.nav.toi.rekrutteringstreff.eier

import com.fasterxml.jackson.core.type.TypeReference
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class RekrutteringstreffEierTest {

    val mapper = JacksonConfig.mapper

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18018
        private val database = TestDatabase()
        private val appPort = ubruktPortnr()
        private val rekrutteringstreffRepository = RekrutteringstreffRepository(database.dataSource)
        private val jobbsøkerRepository = JobbsøkerRepository(database.dataSource, JacksonConfig.mapper)
        private val arbeidsgiverRepository = ArbeidsgiverRepository(database.dataSource, JacksonConfig.mapper)
        private val jobbsøkerService = JobbsøkerService(database.dataSource, jobbsøkerRepository)
        private val eierRepository = EierRepository(database.dataSource)
        private val eierService = EierService(eierRepository, rekrutteringstreffRepository, database.dataSource)
        private val rekrutteringstreffService = RekrutteringstreffService(database.dataSource, rekrutteringstreffRepository, jobbsøkerRepository, arbeidsgiverRepository, jobbsøkerService, eierService)

        private val accessTokenClient = AccessTokenClient(
            clientId = "clientId",
            secret = "clientSecret",
            azureUrl = "http://localhost:$authPort/token",
            httpClient = httpClient
        )

        private lateinit var app: App
    }

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        authServer.start(port = authPort)

        app = App(
            port = appPort,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer = "http://localhost:$authPort/default",
                    jwksUri = "http://localhost:$authPort/default/jwks",
                    audience = "rekrutteringstreff-audience"
                )
            ),
            dataSource = database.dataSource,
            jobbsøkerrettet = jobbsøkerrettet,
            arbeidsgiverrettet = arbeidsgiverrettet,
            utvikler = utvikler,
            kandidatsokApiUrl = "",
            kandidatsokScope = "",
            rapidsConnection = TestRapid(),
            accessTokenClient = accessTokenClient,
            modiaKlient = ModiaKlient(
                modiaContextHolderUrl = wmInfo.httpBaseUrl,
                modiaContextHolderScope = "",
                accessTokenClient = accessTokenClient,
                httpClient = httpClient
            ),
            pilotkontorer = listOf("1234"),
            httpClient = httpClient,
            leaderElection = LeaderElectionMock(),
        )


        app.start()
        waitForServerToBeReady()
    }

    @BeforeEach
    fun setupStubs() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "aktivEnhet": "1234"
                            }
                            """.trimIndent()
                        )
                )
        )
    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    @Test
    fun hentEiere() {
        val navIdent = "A123456"
        val eiere = ('0'..'9').map { "Z99999$it" }
        val token = authServer.lagToken(authPort, navIdent = navIdent)

        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        database.oppdaterRekrutteringstreff(eiere, opprettetRekrutteringstreff.id)
        val response = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere",
            token.serialize()
        )
        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue(response.body(), object : TypeReference<List<String>>() {})
        assertThat(dto).containsExactlyInAnyOrder(*eiere.toTypedArray())
    }

    @Test
    fun `Slett eier er lov hvis det er flere eiere`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        eierRepository.leggTil(opprettetRekrutteringstreff.id, listOf("B987654", "A123456"))

        val response = httpDelete(
            "http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere/$navIdent",
            token.serialize()
        )
        assertThat(response.statusCode()).isEqualTo(200)
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).doesNotContain(navIdent)
    }

    @Test
    fun `Slett eier hvis det bare er 1 eier skal gi bad request`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val response = httpDelete(
            "http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere/$navIdent",
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(400)
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).contains(navIdent)
    }

    @Test
    fun slettEierBeholderAndreEiere() {
        val navIdent = "A123456"
        val beholdIdent = "B987654"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        eierRepository.leggTil(opprettetRekrutteringstreff.id, listOf(beholdIdent))
        val response = httpDelete(
            "http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere/$navIdent",
            token.serialize()
        )
        assertThat(response.statusCode()).isEqualTo(200)
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).contains(beholdIdent)
    }

    @Test
    fun `leggTilEierMedKontor gir 200 og legger til bruker som eier`() {
        val navIdent = "Z999001"
        val oppretter = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(oppretter)
        val treff = database.hentAlleRekrutteringstreff().first()
        assertThat(database.hentEiere(treff.id)).doesNotContain(navIdent)

        val response = httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/eiere/meg",
            "",
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(database.hentEiere(treff.id)).contains(navIdent)
    }

    @Test
    fun `leggTilEierMedKontor er idempotent og gir 200 når bruker allerede er eier`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val treff = database.hentAlleRekrutteringstreff().first()
        assertThat(database.hentEiere(treff.id)).contains(navIdent)

        val response = httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/eiere/meg",
            "",
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(database.hentEiere(treff.id)).contains(navIdent)
    }

    @Test
    fun `leggTilEierMedKontor logger EIER_LAGT_TIL-hendelse`() {
        val navIdent = "Z999002"
        val oppretter = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(oppretter)
        val treff = database.hentAlleRekrutteringstreff().first()

        httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/eiere/meg",
            "",
            token.serialize()
        )

        val hendelser = rekrutteringstreffRepository.hentAlleHendelser(treff.id)
        assertThat(hendelser).anyMatch { it.hendelsestype == "EIER_LAGT_TIL" && it.aktørIdentifikasjon == navIdent }
    }

    @Test
    fun `leggTilEierMedKontor legger til kontor fra Modia og logger KONTOR_LAGT_TIL-hendelse`() {
        val navIdent = "Z999003"
        val oppretter = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(oppretter)
        val treff = database.hentAlleRekrutteringstreff().first()
        assertThat(treff.kontorer).doesNotContain("1234")

        httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/eiere/meg",
            "",
            token.serialize()
        )

        val oppdatertTreff = database.hentAlleRekrutteringstreff().first()
        assertThat(oppdatertTreff.kontorer).contains("1234")

        val hendelser = rekrutteringstreffRepository.hentAlleHendelser(treff.id)
        assertThat(hendelser).anyMatch {
            it.hendelsestype == "KONTOR_LAGT_TIL" && it.subjektId == "1234" && it.aktørIdentifikasjon == navIdent
        }
    }

    @Test
    fun `leggTilEierMedKontor legger ikke til duplikat kontor`() {
        val navIdent = "Z999004"
        val oppretter = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(oppretter, kontor = "1234")
        val treff = database.hentAlleRekrutteringstreff().first()
        assertThat(treff.kontorer).contains("1234")

        httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/eiere/meg",
            "",
            token.serialize()
        )

        val oppdatertTreff = database.hentAlleRekrutteringstreff().first()
        assertThat(oppdatertTreff.kontorer.count { it == "1234" }).isEqualTo(1)

        val hendelser = rekrutteringstreffRepository.hentAlleHendelser(treff.id)
        assertThat(hendelser.filter { it.hendelsestype == "KONTOR_LAGT_TIL" && it.subjektId == "1234" && it.aktørIdentifikasjon == navIdent })
            .isEmpty()
    }

    @Test
    fun `slettEier logger EIER_FJERNET-hendelse`() {
        val navIdent = "A123456"
        val skalSlettes = "B654321"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val treff = database.hentAlleRekrutteringstreff().first()
        eierRepository.leggTil(treff.id, listOf(skalSlettes))

        httpDelete(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/eiere/$skalSlettes",
            token.serialize()
        )

        val hendelser = rekrutteringstreffRepository.hentAlleHendelser(treff.id)
        assertThat(hendelser).anyMatch { it.hendelsestype == "EIER_FJERNET" && it.aktørIdentifikasjon == navIdent }
    }

    @Test
    fun `DELETE eier gir 403 når bruker ikke er eier`() {
        val oppretter = "A123456"
        val ikkeEier = "X000000"
        val token = authServer.lagToken(authPort, navIdent = ikkeEier)
        opprettRekrutteringstreffIDatabase(oppretter)
        val treff = database.hentAlleRekrutteringstreff().first()
        eierRepository.leggTil(treff.id, listOf("B654321"))

        val response = httpDelete(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/eiere/B654321",
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(403)
        assertThat(database.hentEiere(treff.id)).contains("B654321")
    }

    @Test
    fun `PUT eiere meg gir 403 for jobbsøkerrettet rolle`() {
        val oppretter = "A123456"
        val jobbsøkerRettetBruker = "J000001"
        val token = authServer.lagToken(authPort, navIdent = jobbsøkerRettetBruker, groups = listOf(jobbsøkerrettet))
        opprettRekrutteringstreffIDatabase(oppretter)
        val treff = database.hentAlleRekrutteringstreff().first()

        val response = httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/eiere/meg",
            "",
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(403)
        assertThat(database.hentEiere(treff.id)).doesNotContain(jobbsøkerRettetBruker)
    }

    private fun opprettRekrutteringstreffIDatabase(
        navIdent: String,
        tittel: String = "Original Tittel",
        kontor: String = "Original Kontor",
    ) {
        val originalDto = OpprettRekrutteringstreffInternalDto(
            tittel = tittel,
            opprettetAvNavkontorEnhetId = kontor,
            opprettetAvPersonNavident = navIdent,
            opprettetAvTidspunkt = nowOslo().minusDays(10),
        )
        rekrutteringstreffService.opprett(originalDto)
    }

    private fun waitForServerToBeReady() {
        val maxAttempts = 30
        val delayMs = 200L
        var attempts = 0

        while (attempts < maxAttempts) {
            try {
                val response = httpGet("http://localhost:$appPort/isready")
                if (response.statusCode() == 200) {
                    return
                }
            } catch (e: Exception) {
                // Server not ready yet, continue waiting
            }
            attempts++
            Thread.sleep(delayMs)
        }
        throw RuntimeException("Server did not become ready within ${maxAttempts * delayMs}ms")
    }

    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringHentEiere(autentiseringstest: UautentifiserendeTestCase) {
        val dummyId = UUID.randomUUID().toString()
        val response = autentiseringstest.utførGet("http://localhost:$appPort/api/rekrutteringstreff/$dummyId/eiere", authServer, authPort)
        assertThat(response.statusCode()).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringSlettEier(autentiseringstest: UautentifiserendeTestCase) {
        val dummyId = UUID.randomUUID().toString()
        val navIdent = "A123456"
        val response = autentiseringstest.utførDelete("http://localhost:$appPort/api/rekrutteringstreff/$dummyId/eiere/$navIdent", authServer, authPort)
        assertThat(response.statusCode()).isEqualTo(401)
    }
}
