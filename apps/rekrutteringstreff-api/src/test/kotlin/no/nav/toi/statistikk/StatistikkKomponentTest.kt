package no.nav.toi.statistikk

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.App
import no.nav.toi.ApplicationContext
import no.nav.toi.AzureAdRoller
import no.nav.toi.JacksonConfig
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.executeInTransaction
import no.nav.toi.formidling.FormidlingRepository
import no.nav.toi.httpClient
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Innsatsgruppe
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.Kontor
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.lagToken
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.ubruktPortnrFra10000
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class StatistikkKomponentTest {

    companion object {
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()
        private val mapper = JacksonConfig.mapper
        private val OSLO = ZoneId.of("Europe/Oslo")

        private lateinit var infra: TestInfrastructureContext
        private lateinit var ctx: ApplicationContext
        private lateinit var app: App
        private lateinit var formidlingRepository: FormidlingRepository
    }

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        infra = TestInfrastructureContext(
            dataSource = db.dataSource,
            modiaKlientUrl = wmInfo.httpBaseUrl,
        ).also { it.start() }
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
        formidlingRepository = FormidlingRepository(db.dataSource)
    }

    @BeforeEach
    fun setupStubs() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .willReturn(
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

    @Test
    fun `henter fått-jobb-statistikk for kontor i periode`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)

        val ungIkkeStandard = jobbsøker(treffId, "11111111101", alder = 25, innsatsgruppe = "BATT", kontornummer = "1000")
        val eldreStandard = jobbsøker(treffId, "11111111102", alder = 50, innsatsgruppe = "STANDARD_INNSATS", kontornummer = "1000")
        val annetKontor = jobbsøker(treffId, "11111111103", alder = 22, innsatsgruppe = "BATT", kontornummer = "2000")
        db.leggTilJobbsøkere(listOf(ungIkkeStandard, eldreStandard, annetKontor))

        val iPerioden = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, OSLO)
        opprettSendtFormidling(treffId, ungIkkeStandard.personTreffId, arbeidsgiverTreffId, iPerioden)
        opprettSendtFormidling(treffId, eldreStandard.personTreffId, arbeidsgiverTreffId, iPerioden)
        opprettSendtFormidling(treffId, annetKontor.personTreffId, arbeidsgiverTreffId, iPerioden)

        val response = hentStatistikk(navKontor = "1000", fraOgMed = "2026-06-01", tilOgMed = "2026-06-30")

        assertThat(response.statusCode()).isEqualTo(200)
        val statistikk = mapper.readValue<FåttJobbStatistikk>(response.body())
        assertThat(statistikk.totalt).isEqualTo(2)
        assertThat(statistikk.under30år).isEqualTo(1)
        assertThat(statistikk.innsatsgruppeIkkeStandard).isEqualTo(1)
    }

    @Test
    fun `jobbsøkerrettet rolle har tilgang`() {
        val response = hentStatistikk(
            navKontor = "1000",
            fraOgMed = "2026-06-01",
            tilOgMed = "2026-06-30",
            groups = listOf(AzureAdRoller.jobbsøkerrettet),
        )
        assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `manglende navKontor gir 400`() {
        val response = httpGet("/api/rekrutteringstreff/statistikk/fatt-jobben?fraOgMed=2026-06-01&tilOgMed=2026-06-30")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `ugyldig dato gir 400`() {
        val response = httpGet("/api/rekrutteringstreff/statistikk/fatt-jobben?navKontor=1000&fraOgMed=ikkeendato&tilOgMed=2026-06-30")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `tilOgMed før fraOgMed gir 400`() {
        val response = hentStatistikk(navKontor = "1000", fraOgMed = "2026-06-30", tilOgMed = "2026-06-01")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    private fun hentStatistikk(
        navKontor: String,
        fraOgMed: String,
        tilOgMed: String,
        groups: List<UUID> = listOf(AzureAdRoller.arbeidsgiverrettet),
    ): HttpResponse<String> =
        httpGet(
            "/api/rekrutteringstreff/statistikk/fatt-jobben?navKontor=$navKontor&fraOgMed=$fraOgMed&tilOgMed=$tilOgMed",
            groups,
        )

    private fun httpGet(
        path: String,
        groups: List<UUID> = listOf(AzureAdRoller.arbeidsgiverrettet),
        navIdent: String = "A123456",
    ): HttpResponse<String> {
        val token = infra.authServer.lagToken(infra.authPort, navIdent = navIdent, groups = groups)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$appPort$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun leggTilArbeidsgiver(treffId: TreffId) =
        db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )

    private fun jobbsøker(treffId: TreffId, fødselsnummer: String, alder: Int, innsatsgruppe: String, kontornummer: String) =
        Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer(fødselsnummer),
            fornavn = Fornavn("Test"),
            etternavn = Etternavn("Testesen"),
            kontor = Kontor(kontornummer = kontornummer, kontornavn = "Nav Test"),
            veilederNavn = null,
            veilederNavIdent = null,
            status = JobbsøkerStatus.FÅTT_JOBB,
            alder = alder,
            innsatsgruppe = Innsatsgruppe(innsatsgruppe),
        )

    private fun opprettSendtFormidling(
        treffId: TreffId,
        personTreffId: PersonTreffId,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        utfallSendtTidspunkt: ZonedDateTime,
    ): Long = db.dataSource.executeInTransaction { connection ->
        formidlingRepository.opprett(
            connection,
            treffId,
            personTreffId,
            arbeidsgiverTreffId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            utfallSendtTidspunkt = utfallSendtTidspunkt,
        )
    }
}
