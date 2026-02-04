package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.innlegg

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
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
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.lagToken
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.innlegg.InnleggRepository
import no.nav.toi.rekrutteringstreff.innlegg.OpprettInnleggRequestDto
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
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NO_CONTENT
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class InnleggAutorisasjonsTest {

    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
        private lateinit var gyldigInnleggId: UUID
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val rekrutteringstreffRepository = RekrutteringstreffRepository(database.dataSource)
    private val innleggRepository = InnleggRepository(database.dataSource)
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
    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @BeforeEach
    fun setup() {
        rekrutteringstreffService.opprett(
            OpprettRekrutteringstreffInternalDto(
                "Tittel",
                "A000001",
                "Kontor",
                ZonedDateTime.now()
            )
        )
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id

        innleggRepository.opprett(
            gyldigRekrutteringstreff,
            OpprettInnleggRequestDto(
                tittel = "Innlegg Tittel",
                opprettetAvPersonNavn = "Navn Navnesen",
                opprettetAvPersonBeskrivelse = "Beskrivelse",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = ""
            ),
            "A000001"
        )
        gyldigInnleggId = innleggRepository.hentForTreff(gyldigRekrutteringstreff).first().id
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Endepunkt(
        val url: () -> String,
        val metode: () -> HttpRequest.Builder,
    ) {
        HentAlleInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg" },
            { HttpRequest.newBuilder().GET() }),
        HentInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg/$gyldigInnleggId" },
            { HttpRequest.newBuilder().GET() }),
        OpprettInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg" },
            {
                HttpRequest.newBuilder().POST(
                    HttpRequest.BodyPublishers.ofString(
                        JacksonConfig.mapper.writeValueAsString(
                            OpprettInnleggRequestDto(
                                tittel = "Nytt Innlegg",
                                opprettetAvPersonNavn = "Navn Navnesen",
                                opprettetAvPersonBeskrivelse = "Beskrivelse",
                                sendesTilJobbsokerTidspunkt = null,
                                htmlContent = ""
                            )
                        )
                    )
                )
            }
        ),
        OppdaterInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg/$gyldigInnleggId" },
            {
                HttpRequest.newBuilder().PUT(
                    HttpRequest.BodyPublishers.ofString(
                        JacksonConfig.mapper.writeValueAsString(
                            OpprettInnleggRequestDto(
                                tittel = "Oppdatert Tittel",
                                opprettetAvPersonNavn = "Navn Navnesen",
                                opprettetAvPersonBeskrivelse = "Beskrivelse",
                                sendesTilJobbsokerTidspunkt = null,
                                htmlContent = ""
                            )
                        )
                    )
                )
            }),
        SlettInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg/$gyldigInnleggId" },
            { HttpRequest.newBuilder().DELETE() }
        )
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler)),
        Jobbsøkerrettet(listOf(jobbsøkerrettet))
    }

    private fun autorisasjonsCases() = listOf(
        Arguments.of(Endepunkt.HentAlleInnlegg, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentAlleInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentAlleInnlegg, Gruppe.Jobbsøkerrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentAlleInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.HentInnlegg, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentInnlegg, Gruppe.Jobbsøkerrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Utvikler, HTTP_CREATED),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_CREATED),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Utvikler, HTTP_NO_CONTENT),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_NO_CONTENT),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
    ).stream()

    @ParameterizedTest
    @MethodSource("autorisasjonsCases")
    fun testEndepunkt(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, expectedStatus: Int) {
        val request = endepunkt.metode()
            .uri(URI(endepunkt.url()))
            .header(
                "Authorization",
                "Bearer ${authServer.lagToken(authPort, groups = gruppetilhørighet.somStringListe).serialize()}"
            )
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(expectedStatus, response.statusCode())
    }
}