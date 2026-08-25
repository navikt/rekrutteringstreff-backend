package no.nav.toi.treffgjennomføring

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.App
import no.nav.toi.ApplicationContext
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.httpClient
import no.nav.toi.lagToken
import no.nav.toi.lagTokenBorger
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.ubruktPortnrFra10000
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/**
 * Dekker tilgangsstyringa for treffgjennomføring og oppfølging samlet, slik at
 * ingen av endepunkta kan legges til uten at rollekravet blir prøvd.
 *
 * [TreffgjennomføringKomponentTest] dekker forretningslogikken. Her handler alt
 * om hvem som slipper inn, og om steget krever kategorien WORKOP.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class TreffgjennomføringAutorisasjonsTest {

    private val db = TestDatabase()
    private val appPort = ubruktPortnrFra10000.ubruktPortnr()

    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    private val eier = "A100001"
    private val ikkeEier = "A200002"

    private val personTreffId = "11111111-1111-1111-1111-111111111111"
    private val arbeidsgiverTreffId = "22222222-2222-2222-2222-222222222222"

    enum class Metode { GET, PUT, POST }

    /**
     * `kunWorkOp` speiler tabellen i docs/9-planer/workop: møteplanen og
     * intervjufordelinga finnes bare på WorkOp, mens oppmøte, interesse,
     * vurdering og steg gjelder alle treff.
     */
    enum class Endepunkt(
        val metode: Metode,
        val sti: String,
        val body: String,
        val kunWorkOp: Boolean,
    ) {
        Hent(Metode.GET, "/treffgjennomforing-og-oppfolging", "", false),
        Oppmøte(
            Metode.PUT,
            "/treffgjennomforing/oppmote",
            """{"personTreffId":"11111111-1111-1111-1111-111111111111","møtt":true}""",
            false,
        ),
        Møteoppsett(
            Metode.PUT,
            "/treffgjennomforing/moteoppsett",
            """{"starttidspunkt":"09:00","varighetPerMøteMinutter":15}""",
            true,
        ),
        Romfordeling(
            Metode.PUT,
            "/treffgjennomforing/romfordeling",
            """[{"romnummer":1,"jobbsøkere":[]}]""",
            true,
        ),
        Interesse(
            Metode.PUT,
            "/treffgjennomforing/interesse",
            """{"personTreffId":"11111111-1111-1111-1111-111111111111","arbeidsgiverTreffId":"22222222-2222-2222-2222-222222222222","interessert":true}""",
            false,
        ),
        Intervjufordeling(
            Metode.PUT,
            "/treffgjennomforing/intervjufordeling",
            """{"arbeidsgiverTreffId":"22222222-2222-2222-2222-222222222222","inkludertePersonTreffIder":[],"ekskludertePersonTreffIder":[]}""",
            true,
        ),
        Fordel(Metode.POST, "/treffgjennomforing/intervjufordeling/fordel", "{}", true),
        Steg(Metode.PUT, "/treffgjennomforing/steg", """{"steg":"OPPSUMMERING"}""", false),
        Vurdering(
            Metode.PUT,
            "/oppfolging/vurderinger",
            """{"personTreffId":"11111111-1111-1111-1111-111111111111","arbeidsgiverTreffId":"22222222-2222-2222-2222-222222222222","vurderingsstatus":"AKTUELL"}""",
            false,
        ),
    }

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        infra = TestInfrastructureContext(dataSource = db.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl)
            .also { it.start() }
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
    }

    @BeforeEach
    fun stubModia() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"aktivEnhet": "1234"}""")
            )
        )
    }

    @AfterAll
    fun tearDown() {
        infra.stop()
        app.close()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    @ParameterizedTest(name = "{0} avviser den som ikke eier treffet")
    @EnumSource(Endepunkt::class)
    fun `arbeidsgiverrettet uten eierskap får 403`(endepunkt: Endepunkt) {
        val treff = workOpTreff()

        val respons = kall(endepunkt, treff, ikkeEier, listOf(arbeidsgiverrettet))

        assertThat(respons.statusCode()).isEqualTo(403)
    }

    @ParameterizedTest(name = "{0} avviser jobbsøkerrettet rolle")
    @EnumSource(Endepunkt::class)
    fun `jobbsøkerrettet rolle alene gir ikke tilgang selv for eieren`(endepunkt: Endepunkt) {
        val treff = workOpTreff()

        val respons = kall(endepunkt, treff, eier, listOf(jobbsøkerrettet))

        assertThat(respons.statusCode()).isEqualTo(403)
    }

    @ParameterizedTest(name = "{0} avviser modia-rolle uten arbeidsgiverrettet")
    @EnumSource(Endepunkt::class)
    fun `tilgang via treffkontor gir ikke tilgang til treffgjennomføringen`(endepunkt: Endepunkt) {
        val treff = workOpTreff()

        val respons = kall(endepunkt, treff, ikkeEier, listOf(modiaGenerell))

        assertThat(respons.statusCode()).isEqualTo(403)
    }

    @ParameterizedTest(name = "{0} slipper utvikleren gjennom tilgangssjekken")
    @EnumSource(Endepunkt::class)
    fun `utvikler slipper gjennom uten å være eier`(endepunkt: Endepunkt) {
        val treff = workOpTreff()

        val respons = kall(endepunkt, treff, ikkeEier, listOf(arbeidsgiverrettet, utvikler))

        assertThat(respons.statusCode())
            .withFailMessage("Forventet at utvikleren slapp forbi tilgangssjekken, men fikk 403. Body: %s", respons.body())
            .isNotEqualTo(403)
    }

    /**
     * `erEnAvRollene` legger `UTVIKLER` til de godkjente rollene implisitt, så
     * utvikleren kommer inn uten arbeidsgiverrettet. Testen holder fast på det,
     * fordi det ellers er lett å lese `krevEierEllerUtvikler` som at
     * arbeidsgiverrettet alltid kreves.
     */
    @ParameterizedTest(name = "{0} slipper utvikleren gjennom uten arbeidsgiverrettet")
    @EnumSource(Endepunkt::class)
    fun `utvikler alene er nok, arbeidsgiverrettet kreves ikke i tillegg`(endepunkt: Endepunkt) {
        val treff = workOpTreff()

        val respons = kall(endepunkt, treff, ikkeEier, listOf(utvikler))

        assertThat(respons.statusCode())
            .withFailMessage("Forventet at utvikleren slapp forbi tilgangssjekken, men fikk 403. Body: %s", respons.body())
            .isNotEqualTo(403)
    }

    @ParameterizedTest(name = "{0} avviser borgertoken")
    @EnumSource(Endepunkt::class)
    fun `borger slipper aldri inn i treffgjennomføringen`(endepunkt: Endepunkt) {
        val treff = workOpTreff()

        val respons = kallMedBorgertoken(endepunkt, treff)

        assertThat(respons.statusCode()).isEqualTo(403)
    }

    @ParameterizedTest(name = "{0} er stengt på et vanlig treff")
    @EnumSource(Endepunkt::class, names = ["Møteoppsett", "Romfordeling", "Intervjufordeling", "Fordel"])
    fun `WorkOp-steg avvises på et vanlig treff`(endepunkt: Endepunkt) {
        val treff = vanligTreff()

        val respons = kall(endepunkt, treff, eier, listOf(arbeidsgiverrettet))

        assertThat(respons.statusCode())
            .withFailMessage("Forventet 400 for %s på et vanlig treff. Body: %s", endepunkt.name, respons.body())
            .isEqualTo(400)
        assertThat(respons.body()).contains("WORKOP")
    }

    @ParameterizedTest(name = "{0} er åpent på et vanlig treff")
    @EnumSource(Endepunkt::class, names = ["Hent", "Oppmøte", "Interesse", "Steg", "Vurdering"])
    fun `steg som gjelder alle treff blir ikke stengt av kategorien`(endepunkt: Endepunkt) {
        val treff = vanligTreff()

        val respons = kall(endepunkt, treff, eier, listOf(arbeidsgiverrettet))

        assertThat(respons.body()).doesNotContain("WORKOP")
    }

    @ParameterizedTest(name = "{0} svarer 404 på et treff som ikke finnes")
    @EnumSource(Endepunkt::class)
    fun `ukjent treff gir 404 først etter at rollekravet er prøvd`(endepunkt: Endepunkt) {
        val ukjentTreff = TreffId(UUID.randomUUID())

        assertThat(kall(endepunkt, ukjentTreff, eier, listOf(arbeidsgiverrettet)).statusCode())
            .isEqualTo(404)
        assertThat(kall(endepunkt, ukjentTreff, eier, listOf(jobbsøkerrettet)).statusCode())
            .isEqualTo(403)
    }

    private fun workOpTreff(): TreffId = treff(RekrutteringstreffKategori.WORKOP)

    private fun vanligTreff(): TreffId = treff(RekrutteringstreffKategori.REKRUTTERINGSTREFF)

    private fun treff(kategori: RekrutteringstreffKategori): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = eier, kategori = kategori)
        ctx.eierRepository.leggTil(treffId, listOf(eier))
        db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("999999991"), Orgnavn("Testbedrift"), emptyList(), null, null, null),
            treffId,
        )
        return treffId
    }

    private fun kall(
        endepunkt: Endepunkt,
        treffId: TreffId,
        navIdent: String,
        grupper: List<UUID>,
    ): HttpResponse<String> = send(
        endepunkt,
        treffId,
        infra.authServer.lagToken(infra.authPort, navIdent = navIdent, groups = grupper).serialize(),
    )

    private fun kallMedBorgertoken(endepunkt: Endepunkt, treffId: TreffId): HttpResponse<String> =
        send(endepunkt, treffId, infra.authServer.lagTokenBorger(infra.authPort).serialize())

    private fun send(endepunkt: Endepunkt, treffId: TreffId, token: String): HttpResponse<String> {
        val builder = when (endepunkt.metode) {
            Metode.GET -> HttpRequest.newBuilder().GET()
            Metode.PUT -> HttpRequest.newBuilder().PUT(HttpRequest.BodyPublishers.ofString(endepunkt.body))
            Metode.POST -> HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(endepunkt.body))
        }
        val request = builder
            .uri(URI.create("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somString}${endepunkt.sti}"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
