package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.eier

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
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
class RekrutteringstreffEierAutorisasjonsTest {

    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
    }
    private val database = TestDatabase()
    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        infra = TestInfrastructureContext(dataSource = database.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl).also { it.start() }
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
        infra.stop()
        app.close()
    }

    @BeforeEach
    fun setup() {
        ctx.rekrutteringstreffService.opprett(
            OpprettRekrutteringstreffInternalDto(
                "Tittel",
                RekrutteringstreffKategori.REKRUTTERINGSTREFF,
                "A000001",
                "Kontor",
                ZonedDateTime.now()
            )
        )
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id
        ctx.eierRepository.leggTil(gyldigRekrutteringstreff, listOf("A1234"))

    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Endepunkt(
        val url: () -> String,
        val metode: () -> HttpRequest.Builder
    ) {
        HentEiere(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/eiere" },
            {
                HttpRequest.newBuilder().GET()
            }),
        SlettEier(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/eiere/A1234" },
            {
                HttpRequest.newBuilder().DELETE()
            })
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler)),
        Jobbsøkerrettet(listOf(jobbsøkerrettet))
    }

    private fun autorisasjonsCases() = listOf(
        Arguments.of(Endepunkt.HentEiere, Gruppe.Jobbsøkerrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentEiere, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentEiere, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentEiere, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.SlettEier, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettEier, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.SlettEier, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.SlettEier, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
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