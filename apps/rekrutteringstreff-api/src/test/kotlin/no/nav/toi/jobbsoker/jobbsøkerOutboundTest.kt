package no.nav.toi.jobbsoker

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.HttpURLConnection
import java.util.*
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.ApplicationContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerOutboundTest {

    private val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
    private val db = TestDatabase()
    private val appPort = ubruktPortnrFra10000.ubruktPortnr()

    private lateinit var infra: TestInfrastructureContext

    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun startApp(wmInfo: WireMockRuntimeInfo) {

        infra = TestInfrastructureContext(dataSource = db.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl, kandidatsøkKlientUrl = wmInfo.httpBaseUrl)
        infra.start()
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
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
        infra.stop()
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

        ctx.eierRepository.leggTil(treffId, listOf("A000001"))

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

        val token = infra.authServer
            .lagToken(
                authPort = infra.authPort,
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
