package no.nav.toi.jobbsoker

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.HttpURLConnection
import java.util.*

private const val WIREMOCK_PORT = 10010
private const val WIREMOCK_BASE = "http://localhost:$WIREMOCK_PORT"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = WIREMOCK_PORT)
class JobbsøkerOutboundTest {

    private val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

    private val authServer = MockOAuth2Server()
    private val db = TestDatabase()
    private val authPort = ubruktPortnrFra10000.ubruktPortnr()
    private val appPort = ubruktPortnrFra10000.ubruktPortnr()
    private val issuerId = "http://localhost:$authPort/default"
    private val audience = "rekrutteringstreff-audience"

    private lateinit var app: App

    @BeforeAll
    fun startApp(wmInfo: WireMockRuntimeInfo) {
        authServer.start(authPort)

        app = App(
            port = appPort,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    audience = audience,
                    issuer = issuerId,
                    jwksUri = authServer.jwksUrl(issuerId.substringAfterLast("/")).toString()
                )
            ),
            dataSource = db.dataSource,
            arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
            utvikler = AzureAdRoller.utvikler,
            kandidatsokApiUrl = WIREMOCK_BASE,
            kandidatsokScope = "scope",
            azureClientId = "client-id",
            azureClientSecret = "secret",
            azureTokenEndpoint = "${wmInfo.httpBaseUrl}/token",
            TestRapid(),
            httpClient = httpClient
        ).also { it.start() }
    }

    @BeforeEach
    fun setupStubs() {
        stubFor(
            post(urlPathEqualTo("/token"))
                .willReturn(
                    aResponse().withBody("""{"access_token": "obo-token", "expires_in": 3600}""")
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

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    personTreffId = PersonTreffId(UUID.randomUUID()),
                    treffId = treffId,
                    fødselsnummer = fnr,
                    kandidatnummer = null,
                    fornavn = Fornavn("Test"),
                    etternavn = Etternavn("Bruker"),
                    navkontor = null,
                    veilederNavn = null,
                    veilederNavIdent = null
                )
            )
        )

        val personTreffId = db.hentAlleJobbsøkere().first().personTreffId

        stubFor(
            post("/api/arena-kandidatnr")
                .withHeader("Authorization", equalTo("Bearer obo-token"))
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

        val (_, response, result) = Fuel
            .get("http://localhost:$appPort$endepunktRekrutteringstreff/jobbsoker/$personTreffId/kandidatnummer")
            .header("Authorization", "Bearer $token")
            .responseObject<KandidatnummerDto>()

        assertStatuscodeEquals(HttpURLConnection.HTTP_OK, response, result)
        assertThat((result as Result.Success).get().kandidatnummer)
            .isEqualTo(forventetKandidatnummer)

        verify(1, postRequestedFor(urlEqualTo("/api/arena-kandidatnr")))
        verify(1, postRequestedFor(urlEqualTo("/token")))
    }
}