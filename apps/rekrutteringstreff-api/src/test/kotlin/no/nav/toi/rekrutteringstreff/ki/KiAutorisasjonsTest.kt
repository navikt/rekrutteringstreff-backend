package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.ki

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.ki.KiLoggInsert
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
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.ApplicationContext

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
    private val database = TestDatabase()
    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        infra = TestInfrastructureContext(dataSource = database.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl)
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
        infra.stop()
        app.close()
    }

    @BeforeEach
    fun setup() {
        ctx.rekrutteringstreffService.opprett(OpprettRekrutteringstreffInternalDto("Tittel", "A213456", "Kontor", ZonedDateTime.now()))
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id

        ctx.kiLoggRepository.insert(
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
        gyldigLoggId = ctx.kiLoggRepository.list(gyldigRekrutteringstreff.somUuid, "tittel", 10, 0).first().id
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
                "Bearer ${infra.authServer.lagToken(infra.authPort, groups = gruppetilhørighet.somStringListe).serialize()}"
            )
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(expectedStatus, response.statusCode())
    }
}