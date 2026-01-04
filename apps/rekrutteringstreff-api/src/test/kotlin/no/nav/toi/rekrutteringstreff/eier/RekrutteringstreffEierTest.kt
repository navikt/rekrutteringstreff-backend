package no.nav.toi.rekrutteringstreff.eier

import com.fasterxml.jackson.core.type.TypeReference
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
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
import java.net.HttpURLConnection.HTTP_CREATED
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
        private val rekrutteringstreffService = RekrutteringstreffService(database.dataSource, rekrutteringstreffRepository, jobbsøkerRepository, arbeidsgiverRepository, jobbsøkerService)
        private val eierRepository = EierRepository(database.dataSource)

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
            database.dataSource,
            arbeidsgiverrettet,
            utvikler,
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
            pilotkontorer = listOf("1234")
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
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readValue(result.get(), object : TypeReference<List<String>>() {})
                assertThat(dto).containsExactlyInAnyOrder(*eiere.toTypedArray())
            }
        }
    }

    @Test
    fun leggTilEier() {
        val bruker = "A123456"
        val nyEier = "B654321"
        val token = authServer.lagToken(authPort, navIdent = bruker)
        opprettRekrutteringstreffIDatabase(bruker)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).doesNotContain(nyEier)

        val (_, updateResponse, updateResult) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .body(mapper.writeValueAsString(listOf(nyEier)))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (updateResult) {
            is Failure -> throw updateResult.error
            is Success -> {
                assertThat(updateResponse.statusCode).isEqualTo(HTTP_CREATED)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).contains(nyEier)
            }
        }
    }

    @Test
    fun leggTilFlereEiereSamtidig() {
        val bruker = "A123456"
        val nyeEiere = listOf("B654321", "C987654")
        val token = authServer.lagToken(authPort, navIdent = bruker)
        opprettRekrutteringstreffIDatabase(bruker)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).doesNotContainAnyElementsOf(nyeEiere)

        val (_, updateResponse, updateResult) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .body(mapper.writeValueAsString(nyeEiere))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (updateResult) {
            is Failure -> throw updateResult.error
            is Success -> {
                assertThat(updateResponse.statusCode).isEqualTo(HTTP_CREATED)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).containsAll(nyeEiere)
            }
        }
    }

    @Test
    fun ikkeFjernGamleEiereNårManLeggerTilNye() {
        val bruker = "A123456"
        val nyeEiere = listOf("B654321", "C987654")
        val token = authServer.lagToken(authPort, navIdent = bruker)
        opprettRekrutteringstreffIDatabase(bruker)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).contains(bruker)

        val (_, updateResponse, updateResult) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .body(mapper.writeValueAsString(nyeEiere))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (updateResult) {
            is Failure -> throw updateResult.error
            is Success -> {
                assertThat(updateResponse.statusCode).isEqualTo(HTTP_CREATED)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).contains(bruker)
            }
        }
    }

    @Test
    fun ikkeLeggTilDuplikaterAvEiere() {
        val bruker = "A123456"
        val token = authServer.lagToken(authPort, navIdent = bruker)
        opprettRekrutteringstreffIDatabase(bruker)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        assertThat(database.hentEiere(opprettetRekrutteringstreff.id)).contains(bruker)

        val (_, response, result) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .body(mapper.writeValueAsString(listOf(bruker)))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(HTTP_CREATED)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).contains(bruker).hasSize(1)
            }
        }
    }

    @Test
    fun `Slett eier er lov hvis det er flere eiere`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        eierRepository.leggTil(opprettetRekrutteringstreff.id, listOf("B987654", "A123456"))

        val (_, response, result) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere/$navIdent")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).doesNotContain(navIdent)
            }
        }
    }

    @Test
    fun `Slett eier hvis det bare er 1 eier skal gi bad request`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val (_, response, result) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere/$navIdent")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(400, response, result)
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
        val (_, response, result) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere/$navIdent")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).contains(beholdIdent)
            }
        }
    }

    private fun opprettRekrutteringstreffIDatabase(
        navIdent: String,
        tittel: String = "Original Tittel",
    ) {
        val originalDto = OpprettRekrutteringstreffInternalDto(
            tittel = tittel,
            opprettetAvNavkontorEnhetId = "Original Kontor",
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
                val (_, response, _) = Fuel.get("http://localhost:$appPort/isready")
                    .timeout(5000)
                    .timeoutRead(5000)
                    .responseString()
                if (response.statusCode == 200) {
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
        val leggPåToken = autentiseringstest.leggPåToken
        val dummyId = UUID.randomUUID().toString()
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/$dummyId/eiere")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringLeggTilEiere(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val dummyId = UUID.randomUUID().toString()
        val (_, response, result) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/$dummyId/eiere")
            .header("Content-Type", "application/json")
            .body(mapper.writeValueAsString(listOf("A123456")))
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringSlettEier(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val dummyId = UUID.randomUUID().toString()
        val navIdent = "A123456"
        val (_, response, result) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/$dummyId/eiere/$navIdent")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }
}
