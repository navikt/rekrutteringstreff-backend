package no.nav.toi.treffgjennomføring

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.App
import no.nav.toi.ApplicationContext
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.Miljø
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.httpClient
import no.nav.toi.lagToken
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.leggTil
import no.nav.toi.treffgjennomføring.TreffgjennomføringAutorisasjonsTest.Endepunkt
import no.nav.toi.treffgjennomføring.TreffgjennomføringAutorisasjonsTest.Metode
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

/**
 * Utenfor lokal kjøring er treffgjennomføring bare åpnet for WorkOp. Vanlige
 * treff skal avvises i controlleren, også for steg som ellers gjelder alle treff.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class TreffgjennomføringMiljøsperreKomponentTest {

    private val db = TestDatabase()
    private val appPort = ubruktPortnrFra10000.ubruktPortnr()
    private val eier = "A100001"
    private val pilotkontor = "1234"

    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        infra = TestInfrastructureContext(
            dataSource = db.dataSource,
            modiaKlientUrl = wmInfo.httpBaseUrl,
            miljø = Miljø.PROD_GCP,
            workOpPilotkontorer = listOf(pilotkontor),
        ).also { it.start() }
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
    }

    @BeforeEach
    fun stubModia() {
        stubFor(get(urlPathEqualTo("/api/context/v2/aktivenhet")).willReturn(okJson("""{"aktivEnhet": "$pilotkontor"}""")))
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

    @ParameterizedTest(name = "{0} er stengt på et vanlig treff i prod")
    @EnumSource(Endepunkt::class)
    fun `vanlige treff har ikke treffgjennomføring i prod`(endepunkt: Endepunkt) {
        val respons = kall(endepunkt, treff(RekrutteringstreffKategori.REKRUTTERINGSTREFF))

        assertThat(respons.statusCode())
            .withFailMessage("Forventet 400 for %s på et vanlig treff i prod. Body: %s", endepunkt.name, respons.body())
            .isEqualTo(400)
        assertThat(respons.body()).contains("WORKOP")
    }

    @ParameterizedTest(name = "{0} er åpent på WorkOp i prod")
    @EnumSource(Endepunkt::class)
    fun `WorkOp har treffgjennomføring i prod`(endepunkt: Endepunkt) {
        val respons = kall(endepunkt, treff(RekrutteringstreffKategori.WORKOP))

        assertThat(respons.statusCode()).isNotIn(403, 404)
        assertThat(respons.body()).doesNotContain("WORKOP")
    }

    private fun treff(kategori: RekrutteringstreffKategori): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = eier, kategori = kategori)
        ctx.eierRepository.leggTil(treffId, eier, pilotkontor)
        db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("999999991"), Orgnavn("Testbedrift"), emptyList(), null, null, null),
            treffId,
        )
        return treffId
    }

    private fun kall(endepunkt: Endepunkt, treffId: TreffId): HttpResponse<String> {
        val token = infra.authServer.lagToken(infra.authPort, navIdent = eier, groups = listOf(arbeidsgiverrettet)).serialize()
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
