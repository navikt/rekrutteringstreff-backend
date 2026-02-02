package no.nav.toi

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class AppExceptionHandlingTest {
    private val port = ubruktPortnr()
    private val authServer = MockOAuth2Server()
    private val authPort = ubruktPortnr()

    private lateinit var app: App
    private lateinit var treffId: UUID

    @BeforeAll
    fun start(wmInfo: WireMockRuntimeInfo) {
        val accessTokenClient = AccessTokenClient(
            clientId = "clientId",
            secret = "clientSecret",
            azureUrl = "http://localhost:$authPort/token",
            httpClient = httpClient
        )
        app = App(
            port = port,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer = "http://localhost:$authPort/default",
                    jwksUri = "http://localhost:$authPort/default/jwks",
                    audience = "rekrutteringstreff-audience"
                )
            ),
            dataSource = TestDatabase().dataSource,
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
            pilotkontorer = listOf("1234")
        ).also { it.start() }

        authServer.start(port = authPort)
        // Opprett et treff via API for å ha en gyldig id å PUT'e mot

        setupStubs()
        val token = authServer.lagToken(authPort).serialize()
        val response = httpPost(
            "http://localhost:$port/api/rekrutteringstreff",
            """{"opprettetAvNavkontorEnhetId":"0313"}""",
            token
        )
        assertThat(response.statusCode()).isEqualTo(201)
        val body = response.body()
        // body expected like: {"id":"uuid"}
        val idRegex = Regex(""""id"\s*:\s*"([a-f0-9\-]+)"""")
        val match = idRegex.find(body) ?: error("Fikk ikke id fra opprett-respons: $body")
        treffId = UUID.fromString(match.groupValues[1])
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
    fun stop() { authServer.shutdown(); app.close() }

    @Test
    fun `ugyldig JSON (JsonParseException) gir 400`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$port/api/rekrutteringstreff/$treffId"))
            .header("Authorization", "Bearer ${authServer.lagToken(authPort).serialize()}")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{tittel: uten quotes}"))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(400)
        val body = response.body()
        assertThat(body).contains("feil")
        assertThat(body).contains("hint")
    }

    @Test
    fun `gyldig JSON men feil mapping (JsonMappingException) gir 400`() {
        // tittel er påkrevd, mangler -> mapping-feil
        val json = """{
            "beskrivelse":"hei",
            "fraTid":"2025-09-10T08:00:00",
            "tilTid":"2025-09-10T10:00:00",
            "svarfrist":"2025-09-10T00:00:00",
            "gateadresse":"Malmøgata 2",
            "postnummer":"0284",
            "poststed":"Oslo"
        }"""

        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$port/api/rekrutteringstreff/$treffId"))
            .header("Authorization", "Bearer ${authServer.lagToken(authPort).serialize()}")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

        assertThat(response.statusCode()).isEqualTo(400)
        val body = String(response.body())
        assertThat(body).contains("feil")
    }

    @Test
    fun `Oppdatering av treff som ikke finnes og dermed ingen eiere gir 500`() {
        val ikkeEksisterendeId = UUID.randomUUID()
        val json = """{
            "tittel":"Test",
            "beskrivelse":null,
            "fraTid":null,
            "tilTid":null,
            "svarfrist":null,
            "gateadresse":null,
            "postnummer":null,
            "poststed":null
        }"""

        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$port/api/rekrutteringstreff/$ikkeEksisterendeId"))
            .header("Authorization", "Bearer ${authServer.lagToken(authPort).serialize()}")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(500)
    }
}
