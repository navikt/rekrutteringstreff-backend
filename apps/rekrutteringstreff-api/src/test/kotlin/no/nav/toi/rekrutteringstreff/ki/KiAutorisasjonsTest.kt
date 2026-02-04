package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.ki

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.ki.KiLoggInsert
import no.nav.toi.rekrutteringstreff.ki.KiLoggRepository
import no.nav.toi.rekrutteringstreff.ki.TEST_OPENAI_PATH
import no.nav.toi.rekrutteringstreff.ki.ValiderMedLoggRequestUtenTreffIdDto
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class KiAutorisasjonsTest {
    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
        private lateinit var gyldigLoggId: UUID

        @JvmStatic
        @RegisterExtension
        val wireMockServer: WireMockExtension = WireMockExtension.newInstance()
            .options(WireMockConfiguration.options().port(9955))
            .build()
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val rekrutteringstreffRepository = RekrutteringstreffRepository(database.dataSource)
    private val kiLoggRepository = KiLoggRepository(database.dataSource)
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

        val responseBody = """
        {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "{ \"bryterRetningslinjer\": false, \"begrunnelse\": \"OK\" }"
              }
            }
          ]
        }
        """.trimIndent()

        wireMockServer.stubFor(
            post(
                urlEqualTo(TEST_OPENAI_PATH)
            )
                .withRequestBody(containing("Dette er en testmelding som skal valideres av KI."))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
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

        kiLoggRepository.insert(
            KiLoggInsert(
                treffId = gyldigRekrutteringstreff.somUuid,
                feltType = "tittel",
                spørringFraFrontend = "bla bal bal",
                spørringFiltrert = "bla bal bal",
                systemprompt = "en prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "en begrunnelse",
                kiNavn = "navn",
                kiVersjon = "versjon",
                svartidMs = 10,
            )
        )
        gyldigLoggId = kiLoggRepository.list(gyldigRekrutteringstreff.somUuid, "tittel", 10, 0).first().id
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Endepunkt(
        val url: () -> String,
        val metode: () -> HttpRequest.Builder,
    ) {
        Valider({"http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/ki/valider" }, {HttpRequest.newBuilder().POST(
            HttpRequest.BodyPublishers.ofString(
                JacksonConfig.mapper.writeValueAsString(
                    ValiderMedLoggRequestUtenTreffIdDto(
                        tekst = "Dette er en testmelding som skal valideres av KI.",
                        feltType = "tittel"
                    )
                )
             )
        )}),
        OppdaterLagret({"http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/ki/logg/$gyldigLoggId/lagret" }, {HttpRequest.newBuilder().PUT(
            HttpRequest.BodyPublishers.ofString(
                JacksonConfig.mapper.writeValueAsString(
                    mapOf("lagret" to true)
                )
            )

        )}),
        OppdaterManuell(
            {"http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/ki/logg/$gyldigLoggId/manuell" },
            {HttpRequest.newBuilder().PUT(
                HttpRequest.BodyPublishers.ofString(
                    JacksonConfig.mapper.writeValueAsString(
                        mapOf("bryterRetningslinjer" to true)
                    )
                )
            )}
        ),
        HentLogg(
            {"http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/ki/logg/$gyldigLoggId" },
            {
                HttpRequest.newBuilder().GET()
            }),
        HentEnLogg(
            {"http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/ki/logg/" },
            {HttpRequest.newBuilder().GET()}
        )
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler)),
        Jobbsøkerrettet(listOf(jobbsøkerrettet))
    }

    private fun autorisasjonsCases() = listOf(
        Arguments.of(Endepunkt.Valider, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.Valider, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.Valider, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.Valider, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.OppdaterLagret, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OppdaterLagret, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterLagret, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterLagret, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.OppdaterManuell, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OppdaterManuell, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterManuell, Gruppe.Arbeidsgiverrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OppdaterManuell, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.HentLogg, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.HentLogg, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentLogg, Gruppe.Arbeidsgiverrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.HentLogg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.HentEnLogg, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.HentEnLogg, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentEnLogg, Gruppe.Arbeidsgiverrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.HentEnLogg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
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