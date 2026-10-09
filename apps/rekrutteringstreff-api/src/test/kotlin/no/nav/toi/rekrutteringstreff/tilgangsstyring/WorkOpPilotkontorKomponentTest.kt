package no.nav.toi.rekrutteringstreff.tilgangsstyring

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.HttpURLConnection.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class WorkOpPilotkontorKomponentTest {

    private val pilotkontor = "1234"
    private val ikkePilotkontor = "9999"
    private val appPort = ubruktPortnr()
    private val database = TestDatabase()
    private lateinit var testInfrastructureContext: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun setUp(wireMockRuntimeInfo: WireMockRuntimeInfo) {
        testInfrastructureContext = TestInfrastructureContext(
            dataSource = database.dataSource,
            workOpPilotkontorer = listOf(pilotkontor),
            modiaKlientUrl = wireMockRuntimeInfo.httpBaseUrl,
        ).also { it.start() }
        ctx = ApplicationContext(testInfrastructureContext)
        app = App(ctx = ctx, port = appPort).also { it.start() }
    }

    @AfterAll
    fun tearDown() {
        testInfrastructureContext.stop()
        app.close()
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    private fun stubAktivEnhet(enhet: String) {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .willReturn(okJson("""{"aktivEnhet": "$enhet"}"""))
        )
    }

    private fun opprettTreff(kategori: RekrutteringstreffKategori): TreffId =
        ctx.rekrutteringstreffService.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Tittel",
                kategori = kategori,
                opprettetAvPersonNavident = "A000001",
                opprettetAvNavkontorEnhetId = pilotkontor,
                opprettetAvTidspunkt = ZonedDateTime.now(),
            )
        )

    private fun send(
        builder: HttpRequest.Builder,
        path: String,
        grupper: List<UUID> = listOf(AzureAdRoller.arbeidsgiverrettet),
    ): HttpResponse<String> {
        val token = testInfrastructureContext.authServer.lagToken(testInfrastructureContext.authPort, navIdent = "A000001", groups = grupper)
        val request = builder
            .uri(URI("http://localhost:$appPort$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun hentTreff(treffId: TreffId, grupper: List<UUID> = listOf(AzureAdRoller.arbeidsgiverrettet)) =
        send(HttpRequest.newBuilder().GET(), "/api/rekrutteringstreff/${treffId.somString}", grupper)

    private fun opprettViaApi(kategori: String) =
        send(
            HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"tittel": "Nytt treff", "kategori": "$kategori"}""")),
            "/api/rekrutteringstreff",
        )

    private fun søkTreffIder(): List<String> {
        val respons = send(HttpRequest.newBuilder().GET(), "/api/rekrutteringstreff/sok")
        assertEquals(HTTP_OK, respons.statusCode())
        return JacksonConfig.mapper.readTree(respons.body())["treff"].map { it["id"].asText() }
    }

    @Test
    fun `pilotkontor kan hente WorkOp`() {
        stubAktivEnhet(pilotkontor)
        val treffId = opprettTreff(RekrutteringstreffKategori.WORKOP)
        assertEquals(HTTP_OK, hentTreff(treffId).statusCode())
    }

    @Test
    fun `kontor utenfor piloten får 403 ved forsøk på hent av WorkOp`() {
        stubAktivEnhet(ikkePilotkontor)
        val treffId = opprettTreff(RekrutteringstreffKategori.WORKOP)
        assertEquals(HTTP_FORBIDDEN, hentTreff(treffId).statusCode())
    }

    @Test
    fun `kontor utenfor piloten får 403 på underressurser til WorkOp`() {
        stubAktivEnhet(ikkePilotkontor)
        val id = opprettTreff(RekrutteringstreffKategori.WORKOP).somString
        assertEquals(HTTP_FORBIDDEN, send(HttpRequest.newBuilder().GET(), "/api/rekrutteringstreff/$id/eiere").statusCode())
        assertEquals(HTTP_FORBIDDEN, send(HttpRequest.newBuilder().GET(), "/api/rekrutteringstreff/$id/innlegg").statusCode())
        assertEquals(HTTP_FORBIDDEN, send(HttpRequest.newBuilder().GET(), "/api/rekrutteringstreff/$id/arbeidsgiver").statusCode())
    }

    @Test
    fun `utvikler får hente WorkOp uansett kontor`() {
        stubAktivEnhet(ikkePilotkontor)
        val treffId = opprettTreff(RekrutteringstreffKategori.WORKOP)
        assertEquals(HTTP_OK, hentTreff(treffId, listOf(AzureAdRoller.utvikler)).statusCode())
    }

    @Test
    fun `vanlige treff påvirkes ikke av pilotsjekken`() {
        stubAktivEnhet(ikkePilotkontor)
        val treffId = opprettTreff(RekrutteringstreffKategori.REKRUTTERINGSTREFF)
        assertEquals(HTTP_OK, hentTreff(treffId).statusCode())
    }

    @Test
    fun `kontor utenfor piloten kan ikke opprette WorkOp`() {
        stubAktivEnhet(ikkePilotkontor)
        assertEquals(HTTP_FORBIDDEN, opprettViaApi("WORKOP").statusCode())
        assertEquals(HTTP_CREATED, opprettViaApi("REKRUTTERINGSTREFF").statusCode())
    }

    @Test
    fun `pilotkontor kan opprette WorkOp`() {
        stubAktivEnhet(pilotkontor)
        assertEquals(HTTP_CREATED, opprettViaApi("WORKOP").statusCode())
    }

    @Test
    fun `søk skjuler WorkOp for eier som er utenfor piloten`() {
        val workOp = opprettTreff(RekrutteringstreffKategori.WORKOP).somString

        stubAktivEnhet(pilotkontor)
        assertTrue(workOp in søkTreffIder())

        stubAktivEnhet(ikkePilotkontor)
        assertFalse(workOp in søkTreffIder())
    }
}