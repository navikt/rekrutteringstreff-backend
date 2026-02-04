package no.nav.toi.rekrutteringstreff.no.nav.toi.jobbsoker

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AccessTokenClient
import no.nav.toi.App
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.JacksonConfig
import no.nav.toi.LeaderElectionMock
import no.nav.toi.TestRapid
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.httpClient
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.Navkontor
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
import no.nav.toi.lagToken
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerOutboundControllerAutorisasjonsTest {
    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
        private lateinit var gyldigJobbsøkerId: PersonTreffId
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val rekrutteringstreffRepository = RekrutteringstreffRepository(database.dataSource)
    private val jobbsøkerRepository = JobbsøkerRepository(database.dataSource, JacksonConfig.mapper)
    private val arbeidsgiverRepository = ArbeidsgiverRepository(database.dataSource, JacksonConfig.mapper)
    private val jobbsøkerService = JobbsøkerService(database.dataSource, jobbsøkerRepository)

    private val rekrutteringstreffService = RekrutteringstreffService(
        database.dataSource,
        rekrutteringstreffRepository = rekrutteringstreffRepository,
        jobbsøkerRepository = JobbsøkerRepository(database.dataSource, JacksonConfig.mapper),
        arbeidsgiverRepository = arbeidsgiverRepository,
        jobbsøkerService = jobbsøkerService
    )

    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        authServer.start(port = authPort)
        val accessTokenClient = AccessTokenClient(
            clientId = "client-id",
            secret = "secret",
            azureUrl = "http://localhost:$authPort/token",
            httpClient = httpClient
        )
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
            kandidatsokApiUrl = wmInfo.httpBaseUrl,
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
            leaderElection = LeaderElectionMock(),
        ).also { it.start() }
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
        stubFor(
            post("/api/arena-kandidatnr")
                .withHeader("Authorization", matching("Bearer .*"))
                .withRequestBody(equalToJson("""{"fodselsnummer":"12345678902"}"""))
                .willReturn(
                    okJson("""{"arenaKandidatnr":"K654321"}""")
                )
        )
    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @BeforeEach
    fun setup() {
        rekrutteringstreffService.opprett(OpprettRekrutteringstreffInternalDto("Tittel", "A213456", "Kontor", ZonedDateTime.now()))
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id
        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer = Fødselsnummer("12345678902"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("NAV Oslo"),
            veilederNavn = VeilederNavn("Espen Askeladd"),
            veilederNavIdent = VeilederNavIdent("NAV456")
        )
        jobbsøkerService.leggTilJobbsøkere(
            jobbsøkere = listOf(leggTilJobbsøker),
            treffId = gyldigRekrutteringstreff,
            navIdent = "NAV456"
        )
        gyldigJobbsøkerId = jobbsøkerRepository.hentJobbsøkere(gyldigRekrutteringstreff).first().personTreffId
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler)),
        Jobbsøkerrettet(listOf(jobbsøkerrettet))
    }

    private fun autorisasjonsCases() = listOf(
        Arguments.of(Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
    ).stream()

    @ParameterizedTest
    @MethodSource("autorisasjonsCases")
    fun `test autorisasjon for hentKandidatnummer`(gruppetilhørighet: Gruppe, forventetStatusKode: Int) {
        val request = HttpRequest.newBuilder().GET()
            .uri(URI("http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/jobbsoker/${gyldigJobbsøkerId.somString}/kandidatnummer"))
            .header(
                "Authorization",
                "Bearer ${authServer.lagToken(authPort = authPort, groups = gruppetilhørighet.somStringListe, navIdent = "A213456").serialize()}")
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(forventetStatusKode, response.statusCode())
    }
}