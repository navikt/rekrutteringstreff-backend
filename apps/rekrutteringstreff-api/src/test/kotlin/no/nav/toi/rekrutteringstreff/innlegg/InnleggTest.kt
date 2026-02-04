package no.nav.toi.rekrutteringstreff.innlegg

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.*
import java.net.ServerSocket
import java.time.ZonedDateTime
import java.util.*


@WireMockTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnleggTest {

    private val auth = MockOAuth2Server()
    private val authPort = 18013
    private val db = TestDatabase()
    private val appPort = TestUtils.getFreePort()
    private lateinit var app: App

    @BeforeAll
    fun start(wmInfo: WireMockRuntimeInfo) {
        val accessTokenClient = AccessTokenClient(
            clientId = "clientId",
            secret = "clientSecret",
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
            dataSource = db.dataSource,
            jobbsøkerrettet = jobbsøkerrettet,
            arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
            utvikler = AzureAdRoller.utvikler,
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
        auth.start(port = authPort)
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
    fun stop() {
        auth.shutdown()
        app.close()
    }

    @AfterEach
    fun clean() {
        db.slettAlt()
    }

    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @ParameterizedTest @MethodSource("tokenVarianter")
    fun `ukorrekt token GET alle`(tc: UautentifiserendeTestCase) {
        val response = tc.utførGet("http://localhost:${appPort}/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg", auth, authPort)
        assertThat(response.statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @ParameterizedTest @MethodSource("tokenVarianter")
    fun `ukorrekt token GET ett`(tc: UautentifiserendeTestCase) {
        val response = tc.utførGet("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}", auth, authPort)
        assertThat(response.statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @ParameterizedTest @MethodSource("tokenVarianter")
    fun `ukorrekt token PUT`(tc: UautentifiserendeTestCase) {
        val body = OppdaterInnleggRequestDto("T", "", "", null, "")
        val response = tc.utførPut("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}", JacksonConfig.mapper.writeValueAsString(body), auth, authPort)
        assertThat(response.statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @ParameterizedTest @MethodSource("tokenVarianter")
    fun `ukorrekt token DELETE`(tc: UautentifiserendeTestCase) {
        val response = tc.utførDelete("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}", auth, authPort)
        assertThat(response.statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `PUT oppdaterer innlegg`() {
        val token  = auth.lagToken(authPort, navIdent = "C123456")
        val treff  = db.opprettRekrutteringstreffIDatabase()
        val repo   = InnleggRepository(db.dataSource)
        val id     = repo.opprett(treff, sampleOpprett(), "C123456").id

        val body = OppdaterInnleggRequestDto(
            tittel = "Ny Tittel",
            opprettetAvPersonNavn = "Kari Oppdatert",
            opprettetAvPersonBeskrivelse = "Oppdatert Rådgiver",
            sendesTilJobbsokerTidspunkt = null,
            htmlContent = "<p>x</p>"
        )

        val resp = httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg/$id",
            JacksonConfig.mapper.writeValueAsString(body),
            token.serialize()
        )

        assertThat(resp.statusCode()).isEqualTo(HTTP_OK)
        val dto = JacksonConfig.mapper.readValue(resp.body(), InnleggResponseDto::class.java)
        assertThat(dto.tittel).isEqualTo(body.tittel)
        assertThat(dto.opprettetAvPersonNavn).isEqualTo(body.opprettetAvPersonNavn)
        assertThat(repo.hentById(id)!!.tittel).isEqualTo(body.tittel)
    }

    @Test
    fun `GET liste returnerer innlegg for treff`() {
        val token = auth.lagToken(authPort, navIdent = "C123456")
        val treff = db.opprettRekrutteringstreffIDatabase()
        val repo = InnleggRepository(db.dataSource)
        val id = repo.opprett(treff, sampleOpprett(), "C123456").id

        val resp = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg",
            token.serialize()
        )

        assertThat(resp.statusCode()).isEqualTo(HTTP_OK)
        val type = JacksonConfig.mapper.typeFactory.constructCollectionType(List::class.java, InnleggResponseDto::class.java)
        val liste: List<InnleggResponseDto> = JacksonConfig.mapper.readValue(resp.body(), type)
        assertThat(liste).anySatisfy { it.id == id }
    }

    @Test
    fun `GET ett returnerer innlegg`() {
        val token = auth.lagToken(authPort, navIdent = "C123456")
        val treff = db.opprettRekrutteringstreffIDatabase()
        val repo = InnleggRepository(db.dataSource)
        val id = repo.opprett(treff, sampleOpprett(), "C123456").id

        val resp = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg/$id",
            token.serialize()
        )

        assertThat(resp.statusCode()).isEqualTo(HTTP_OK)
        val dto = JacksonConfig.mapper.readValue(resp.body(), InnleggResponseDto::class.java)
        assertThat(dto.id).isEqualTo(id)
    }

    @Test
    fun `POST oppretter innlegg`() {
        val token = auth.lagToken(authPort, navIdent = "C123456")
        val treff = db.opprettRekrutteringstreffIDatabase()
        val body = sampleOpprett()

        val resp = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg",
            JacksonConfig.mapper.writeValueAsString(body),
            token.serialize()
        )

        assertThat(resp.statusCode()).isEqualTo(HTTP_CREATED)
        val dto = JacksonConfig.mapper.readValue(resp.body(), InnleggResponseDto::class.java)
        assertThat(dto.tittel).isEqualTo(body.tittel)
    }

    @Test
    fun `DELETE fjerner innlegg`() {
        val token = auth.lagToken(authPort, navIdent = "C123456")
        val treff = db.opprettRekrutteringstreffIDatabase()
        val repo = InnleggRepository(db.dataSource)
        val id = repo.opprett(treff, sampleOpprett(), "C123456").id

        val resp = httpDelete(
            "http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg/$id",
            token.serialize()
        )

        assertThat(resp.statusCode()).isEqualTo(HTTP_NO_CONTENT)
        assertThat(repo.hentById(id)).isNull()
    }

    @Test
    fun `PUT mot ukjent treff gir 404`() {
        val token = auth.lagToken(authPort, navIdent = "C123456") // Use a valid navIdent
        val resp = httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}",
            JacksonConfig.mapper.writeValueAsString(OppdaterInnleggRequestDto("t", "", "", null, "")),
            token.serialize()
        )
        assertThat(resp.statusCode()).isEqualTo(HTTP_NOT_FOUND)
    }

    private fun sampleOpprett() = OpprettInnleggRequestDto(
        tittel = "Tittel",
        opprettetAvPersonNavn = "Ola",
        opprettetAvPersonBeskrivelse = "Veileder",
        sendesTilJobbsokerTidspunkt = ZonedDateTime.now().plusHours(1),
        htmlContent = "<p>x</p>"
    )
}

object TestUtils {
    fun getFreePort(): Int = ServerSocket(0).use { it.localPort }
}
