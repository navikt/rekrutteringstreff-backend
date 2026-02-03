package no.nav.toi.jobbsoker

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.HttpURLConnection
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerOutboundTest {

    private val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

    private val authServer = MockOAuth2Server()
    private val db = TestDatabase()
    private val authPort = ubruktPortnrFra10000.ubruktPortnr()
    private val appPort = ubruktPortnrFra10000.ubruktPortnr()
    private val issuerId = "http://localhost:$authPort/default"
    private val audience = "rekrutteringstreff-audience"
    private val jwksUri = "http://localhost:$authPort/default/jwks"

    private val eierRepository = EierRepository(db.dataSource)

    private lateinit var app: App

    @BeforeAll
    fun startApp(wmInfo: WireMockRuntimeInfo) {
        authServer.start(authPort)
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
                    audience = audience,
                    issuer = issuerId,
                    jwksUri = jwksUri
                )
            ),
            dataSource = db.dataSource,
            jobbsøkerrettet = jobbsøkerrettet,
            arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
            utvikler = AzureAdRoller.utvikler,
            kandidatsokApiUrl = wmInfo.httpBaseUrl,
            kandidatsokScope = "scope",
            rapidsConnection = TestRapid(),
            accessTokenClient = accessTokenClient,
            modiaKlient = ModiaKlient(
                modiaContextHolderUrl = wmInfo.httpBaseUrl,
                modiaContextHolderScope = "",
                accessTokenClient = accessTokenClient,
                httpClient = httpClient
            ),
            pilotkontorer = listOf("1234"),
            httpClient = httpClient
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
        app.close()
        authServer.shutdown()
    }

    @AfterEach
    fun cleanup() {
        db.slettAlt()
        resetAllRequests()
        resetToDefault()
    }

    @Test
    fun `GET kandidatnummer returnerer forventet kandidatnummer`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("12345678910")
        val forventetKandidatnummer = "K123456"

        eierRepository.leggTil(treffId, listOf("A000001"))

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    personTreffId = PersonTreffId(UUID.randomUUID()),
                    treffId = treffId,
                    fødselsnummer = fnr,
                    fornavn = Fornavn("Test"),
                    etternavn = Etternavn("Bruker"),
                    navkontor = null,
                    veilederNavn = null,
                    veilederNavIdent = null,
                    status = JobbsøkerStatus.LAGT_TIL,
                )
            )
        )

        val personTreffId = db.hentAlleJobbsøkere().first().personTreffId

        stubFor(
            post("/api/arena-kandidatnr")
                .withHeader("Authorization", matching("Bearer .*"))
                .withRequestBody(equalToJson("""{"fodselsnummer":"${fnr.asString}"}"""))
                .willReturn(
                    okJson("""{"arenaKandidatnr":"$forventetKandidatnummer"}""")
                )
        )

        val token = authServer
            .lagToken(
                authPort = authPort,
                issuerId = issuerId,
                audience = audience,
                groups = listOf(AzureAdRoller.arbeidsgiverrettet)
            )
            .serialize()

        val response = httpGet(
            "http://localhost:$appPort$endepunktRekrutteringstreff/$treffId/jobbsoker/$personTreffId/kandidatnummer",
            token
        )

        assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK)
        val kandidatnummerDto = JacksonConfig.mapper.readValue(response.body(), KandidatnummerDto::class.java)
        assertThat(kandidatnummerDto.kandidatnummer).isEqualTo(forventetKandidatnummer)

        verify(1, postRequestedFor(urlEqualTo("/api/arena-kandidatnr")))
    }
}
