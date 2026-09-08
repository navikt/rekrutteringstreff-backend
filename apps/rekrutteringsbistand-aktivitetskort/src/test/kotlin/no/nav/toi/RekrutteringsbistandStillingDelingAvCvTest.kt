package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.AktivitetskortType
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.rekrutteringsbistand.IdentType
import no.nav.toi.ubruktPortnrFra11000.ubruktPortnr
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.internals.AutoOffsetResetStrategy.StrategyType
import org.apache.kafka.clients.producer.MockProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringsbistandStillingDelingAvCvTest {
    private val localEnv = mutableMapOf<String, String>(
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_DATABASE" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_USERNAME" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PASSWORD" to "test"
    )
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val localPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }
        .also { localConfig ->
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_HOST"] = localConfig.host
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PORT"] = localConfig.getMappedPort(5432).toString()
        }

    private val appPort = ubruktPortnr()
    private val rapid = TestRapid()
    private val databaseConfig = DatabaseConfig(localEnv, meterRegistry)
    private val testRepository = TestRepository(databaseConfig)
    private val repository = Repository(databaseConfig, "http://url", "topic")
    private val app = App(
        port = appPort,
        rapidsConnection = rapid,
        repository = repository,
        producer = MockProducer(),
        consumer = MockConsumer(StrategyType.EARLIEST.toString()),
        dabAktivitetskortFeilTopic = "topic",
        leaderElection = LeaderElectionMock(),
        meterRegistry = meterRegistry,
        isRunning = {true},
        isReady = {true},
        workOpLyttereAktivert = false
    )

    @BeforeAll
    fun oppstart() {
        app.start()
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
        testRepository.slettAlt()
    }

    @AfterAll
    fun teardown() {
        localPostgres.close()
        app.stop()
    }

    @Test
    fun `forespurt samtykke skal opprette aktivitetskort i forslag`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val tittel = "Test Stilling"
        val opprettetAv = "testuser"
        val opprettetTidspunkt = ZonedDateTime.now()

        rapid.sendTestMessage(
            samtykkeForespurtMelding(
                fnr = fnr,
                stillingId = stillingId,
                stillingsTittel = tittel,
                forespurtAvIdent = opprettetAv,
            )
        )
        val rekrutteringsbistandStillinger = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(rekrutteringsbistandStillinger).hasSize(1)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)

        val expectedDetaljer = """[]"""
        rekrutteringsbistandStillinger.apply {
            assertThat(this[0].tittel).isEqualTo(tittel)
            assertThat(this[0].stillingId).isEqualTo(stillingId)
            assertThat(this[0].beskrivelse).isEqualTo("Nav hjelper en arbeidsgiver med å finne kandidater til en stilling, og tror den kan passe for deg.")
            assertThat(this[0].detaljer).isEqualToIgnoringWhitespace( expectedDetaljer)
            assertThat(this[0].aktivitetskortId).isEqualTo(inspektør.message(0)["aktivitetskortuuid"].asText().toUUID())
            assertThat(this[0].aktivitetsStatus).isEqualTo(AktivitetsStatus.FORSLAG.name)
            assertThat(this[0].aktivitetsType).isEqualTo(AktivitetskortType.DELTSTILLING.name)
            assertThat(this[0].opprettetAv).isEqualTo(opprettetAv)
            assertThat(this[0].opprettetAvType).isEqualTo(EndretAvType.NAVIDENT.name)
            assertThat(this[0].opprettetTidspunkt).isBetween(opprettetTidspunkt, ZonedDateTime.now())
        }
    }

    @Test
    fun `forespurt samtykke med samme kandidat og stilling skal ignoreres`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val tittel = "Test Stilling"
        val opprettetAv = "testuser"

        rapid.sendTestMessage(
            samtykkeForespurtMelding(
                fnr = fnr,
                stillingId = stillingId,
                stillingsTittel = tittel,
                forespurtAvIdent = opprettetAv,
            )
        )
        val expectedRekrutteringsbistandStillinger = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(expectedRekrutteringsbistandStillinger).hasSize(1)
        rapid.sendTestMessage(
            samtykkeForespurtMelding(
                fnr = fnr,
                stillingId = stillingId,
                stillingsTittel = tittel,
                forespurtAvIdent = opprettetAv,
            )
        )

        val actualRekrutteringsbistandStillinger = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(actualRekrutteringsbistandStillinger).hasSize(1)
        assertThat(actualRekrutteringsbistandStillinger.first()).usingRecursiveComparison().isEqualTo(expectedRekrutteringsbistandStillinger.first())
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
    }

    @Test
    fun `samtykke besvart med ja skal flytte aktivitetskort til gjennomføres`() {
        testSamtykkeBesvart(
            samtykkeGitt = true,
            besvartAvIdent = "01010012345",
            besvartAvIdentType = IdentType.FNR,
            forventetAktivitetsStatus = AktivitetsStatus.GJENNOMFORES,
            forventetEndretAvType = EndretAvType.PERSONBRUKERIDENT,
        )
    }

    @Test
    fun `samtykke besvart med nei skal flytte aktivitetskort til avbrutt`() {
        testSamtykkeBesvart(
            samtykkeGitt = false,
            besvartAvIdent = "01010012345",
            besvartAvIdentType = IdentType.FNR,
            forventetAktivitetsStatus = AktivitetsStatus.AVBRUTT,
            forventetEndretAvType = EndretAvType.PERSONBRUKERIDENT,
        )
    }

    @Test
    fun `samme svar skal bare lagres én gang`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        repository.opprettDeltStilling(
            fnr = fnr,
            stillingId = stillingId.toString(),
            tittel = "Test Stilling",
            opprettetAv = "Z123456",
            arbeidsgiver = "Test Arbeidsgiver",
            arbeidssted = "Oslo",
        )
        val melding = samtykkeBesvartMelding(
            fnr = fnr,
            stillingId = stillingId,
            samtykkeGitt = true,
            besvartAvIdent = fnr,
            besvartAvIdentType = IdentType.FNR,
        )
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(1)

        rapid.sendTestMessage(melding)

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)

        rapid.sendTestMessage(melding)

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)
    }

    @Test
    fun `nytt svar skal lagres når aktivitetsstatus er endret`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val navIdent = "Z123456"
        repository.opprettDeltStilling(
            fnr = fnr,
            stillingId = stillingId.toString(),
            tittel = "Test Stilling",
            opprettetAv = navIdent,
            arbeidsgiver = "Test Arbeidsgiver",
            arbeidssted = "Oslo",
        )

        rapid.sendTestMessage(
            samtykkeBesvartMelding(
                fnr = fnr,
                stillingId = stillingId,
                samtykkeGitt = true,
                besvartAvIdent = fnr,
                besvartAvIdentType = IdentType.FNR,
            )
        )
        rapid.sendTestMessage(
            samtykkeBesvartMelding(
                fnr = fnr,
                stillingId = stillingId,
                samtykkeGitt = false,
                besvartAvIdent = fnr,
                besvartAvIdentType = IdentType.FNR,
            )
        )

        val statusendringer = testRepository.hentAlleRekrutteringsbistandStillinger().map {
            Triple(it.aktivitetsStatus, it.opprettetAv, it.opprettetAvType)
        }
        assertThat(statusendringer).containsExactlyInAnyOrder(
            Triple(AktivitetsStatus.FORSLAG.name, navIdent, EndretAvType.NAVIDENT.name),
            Triple(AktivitetsStatus.GJENNOMFORES.name, fnr, EndretAvType.PERSONBRUKERIDENT.name),
            Triple(AktivitetsStatus.AVBRUTT.name, fnr, EndretAvType.PERSONBRUKERIDENT.name),
        )
    }

    @Test
    fun `selvbetjent samtykke gitt skal kreve eksisterende kort og flytte det til gjennomføres`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val melding = selvbetjentSamtykkeGittMelding(fnr, stillingId)

        rapid.sendTestMessage(melding)

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).isEmpty()

        opprettDeltStilling(fnr, stillingId)
        rapid.sendTestMessage(melding)

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(2)
        hendelser.last().also { hendelse ->
            assertThat(hendelse.aktivitetsStatus).isEqualTo(AktivitetsStatus.GJENNOMFORES.name)
            assertThat(hendelse.opprettetAv).isEqualTo(fnr)
            assertThat(hendelse.opprettetAvType).isEqualTo(EndretAvType.PERSONBRUKERIDENT.name)
        }
    }

    @Test
    fun `samtykke trukket av veileder skal flytte aktivitetskort til avbrutt`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val navIdent = "Z999999"
        opprettDeltStilling(fnr, stillingId)
        repository.oppdaterAktivitetsstatus(
            aktivitetskortId = checkNotNull(repository.hentAktivitetskortIdForDeltStilling(fnr, stillingId)),
            aktivitetsStatus = AktivitetsStatus.GJENNOMFORES,
            endretAv = fnr,
            endretAvType = EndretAvType.PERSONBRUKERIDENT,
        )

        rapid.sendTestMessage(
            samtykkeTrukketMelding(
                fnr = fnr,
                stillingId = stillingId,
                trukketAvIdent = navIdent,
                trukketAvIdentType = IdentType.NAV_IDENT,
            )
        )

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(3)
        hendelser.last().also { hendelse ->
            assertThat(hendelse.aktivitetsStatus).isEqualTo(AktivitetsStatus.AVBRUTT.name)
            assertThat(hendelse.opprettetAv).isEqualTo(navIdent)
            assertThat(hendelse.opprettetAvType).isEqualTo(EndretAvType.NAVIDENT.name)
        }
    }

    @Test
    fun `registrert fått jobben skal flytte aktivitetskort til fullført når kandidat har svart ja`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val navIdent = "Z123456"

        opprettDeltStilling(fnr, stillingId, AktivitetsStatus.GJENNOMFORES)
        rapid.sendTestMessage(
            registrertFattJobbenMelding(
                stillingId = stillingId,
                fnr = fnr,
                navIdent = navIdent,
            )
        )

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger().filter { it.fnr == fnr }
        assertThat(hendelser).hasSize(3)
        hendelser.last().also { hendelse ->
            assertThat(hendelse.aktivitetsStatus).isEqualTo(AktivitetsStatus.FULLFORT.name)
            assertThat(hendelse.opprettetAv).isEqualTo(navIdent)
            assertThat(hendelse.opprettetAvType).isEqualTo(EndretAvType.NAVIDENT.name)
        }
    }

    @Test
    fun `registrert fått jobben skal ikke endre status når kandidat har svart nei`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val navIdent = "Z123456"

        rapid.sendTestMessage(
            samtykkeForespurtMelding(
                fnr = fnr,
                stillingId = stillingId,
                stillingsTittel = "Test Stilling",
                forespurtAvIdent = navIdent,
            )
        )
        rapid.sendTestMessage(
            samtykkeBesvartMelding(
                fnr = fnr,
                stillingId = stillingId,
                samtykkeGitt = false,
                besvartAvIdent = fnr,
                besvartAvIdentType = IdentType.FNR,
            )
        )
        rapid.sendTestMessage(
            registrertFattJobbenMelding(
                stillingId = stillingId,
                fnr = fnr,
                navIdent = navIdent,
            )
        )

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger().filter { it.fnr == fnr }
        assertThat(hendelser).hasSize(2)
        assertThat(hendelser.last().aktivitetsStatus).isEqualTo(AktivitetsStatus.AVBRUTT.name)
    }

    @Test
    fun `registrert fått jobben skal endre status til avbrutt før kandidaten har svart ja`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId)

        rapid.sendTestMessage(
            registrertFattJobbenMelding(
                stillingId = stillingId,
                fnr = fnr,
                navIdent = "Z123456",
            )
        )

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(1)
        assertThat(hendelser.single().aktivitetsStatus).isEqualTo(AktivitetsStatus.FULLFORT.name)
    }

    @Test
    fun `lukket kandidatliste skal fullføre bare kandidater som har svart ja`() {
        val stillingId = UUID.randomUUID()
        val navIdent = "Z999999"
        val kandidatSomHarSvartJa = "01010012345"
        val kandidatSomHarSvartNei = "02020012345"

        opprettDeltStilling(kandidatSomHarSvartJa, stillingId, AktivitetsStatus.GJENNOMFORES)
        opprettDeltStilling(kandidatSomHarSvartNei, stillingId, AktivitetsStatus.AVBRUTT)

        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = stillingId,
                navIdent = navIdent,
                fnrFikkJobben = listOf("9999"),
                fnrFikkIkkeJobben = listOf(kandidatSomHarSvartJa, kandidatSomHarSvartNei),
            )
        )

        val jaKandidatHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
            .filter { it.fnr == kandidatSomHarSvartJa }
        val neiKandidatHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
            .filter { it.fnr == kandidatSomHarSvartNei }

        assertThat(jaKandidatHendelser).hasSize(3)
        assertThat(jaKandidatHendelser.last().aktivitetsStatus).isEqualTo(AktivitetsStatus.FULLFORT.name)
        assertThat(jaKandidatHendelser.last().opprettetAv).isEqualTo(navIdent)
        assertThat(jaKandidatHendelser.last().opprettetAvType).isEqualTo(EndretAvType.NAVIDENT.name)

        // Kandidat med NEI skal ikke oppdateres pa nytt ved lukking av liste.
        assertThat(neiKandidatHendelser).hasSize(2)
    }

    @Test
    fun `lukket kandidatliste skal fullføre flere kandidater samlet og være idempotent`() {
        val stillingId = UUID.randomUUID()
        val fnr1 = "01010012345"
        val fnr2 = "02020012345"

        listOf(fnr1, fnr2).forEach { fnr ->
            opprettDeltStilling(fnr, stillingId, AktivitetsStatus.GJENNOMFORES)
        }

        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = stillingId,
                navIdent = "Z999999",
                fnrFikkJobben = emptyList(),
                fnrFikkIkkeJobben = listOf(fnr1, fnr1, fnr2, "ukjent-fnr"),
            )
        )

        val fullfortHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
            .filter { it.aktivitetsStatus == AktivitetsStatus.FULLFORT.name }
        assertThat(fullfortHendelser).hasSize(2)
        assertThat(fullfortHendelser.map { it.fnr }).containsExactlyInAnyOrder(fnr1, fnr2)
        assertThat(fullfortHendelser.map { it.opprettetAv }).containsOnly("Z999999")
        assertThat(fullfortHendelser.map { it.opprettetAvType }).containsOnly(EndretAvType.NAVIDENT.name)
        assertThat(fullfortHendelser.map { it.opprettetTidspunkt }.distinct()).hasSize(1)
        assertThat(fullfortHendelser.map { it.messageId }).doesNotHaveDuplicates()
        val opprettetTidspunkt = fullfortHendelser.map { it.opprettetTidspunkt }.distinct().first()

        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = stillingId,
                navIdent = "Z888888",
                fnrFikkJobben = emptyList(),
                fnrFikkIkkeJobben = listOf(fnr1, fnr2),
            )
        )

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger())
            .filteredOn { it.aktivitetsStatus == AktivitetsStatus.FULLFORT.name }
            .hasSize(2)
            .filteredOn { it.opprettetTidspunkt == opprettetTidspunkt }
            .hasSize(2)
    }

    @Test
    fun `lukket kandidatliste uten kandidater skal ikke oppdatere databasen men markere hendelseskjeden som ferdig`() {
        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = UUID.randomUUID(),
                navIdent = "Z999999",
                fnrFikkJobben = emptyList(),
                fnrFikkIkkeJobben = emptyList(),
            )
        )

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).isEmpty()
        assertThat(rapid.inspektør.size).isEqualTo(1)
        assertThat(rapid.inspektør.message(0)["@slutt_av_hendelseskjede"].asBoolean()).isTrue()
    }

    private fun testSamtykkeBesvart(
        samtykkeGitt: Boolean,
        besvartAvIdent: String,
        besvartAvIdentType: IdentType,
        forventetAktivitetsStatus: AktivitetsStatus,
        forventetEndretAvType: EndretAvType,
    ) {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId)

        rapid.sendTestMessage(
            samtykkeBesvartMelding(
                fnr = fnr,
                stillingId = stillingId,
                samtykkeGitt = samtykkeGitt,
                besvartAvIdent = besvartAvIdent,
                besvartAvIdentType = besvartAvIdentType,
            )
        )

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(2)
        hendelser.last().also { hendelse ->
            assertThat(hendelse.aktivitetsStatus).isEqualTo(forventetAktivitetsStatus.name)
            assertThat(hendelse.opprettetAv).isEqualTo(besvartAvIdent)
            assertThat(hendelse.opprettetAvType).isEqualTo(forventetEndretAvType.name)
        }
    }

    private fun opprettDeltStilling(
        fnr: String,
        stillingId: UUID,
        aktivitetsStatus: AktivitetsStatus = AktivitetsStatus.FORSLAG,
    ) {
        repository.opprettDeltStilling(
            fnr = fnr,
            stillingId = stillingId.toString(),
            tittel = "Test Stilling",
            opprettetAv = "Z123456",
            arbeidsgiver = "Test Arbeidsgiver",
            arbeidssted = "Oslo",
        )
        if (aktivitetsStatus != AktivitetsStatus.FORSLAG) {
            repository.oppdaterAktivitetsstatus(
                aktivitetskortId = checkNotNull(repository.hentAktivitetskortIdForDeltStilling(fnr, stillingId)),
                aktivitetsStatus = aktivitetsStatus,
                endretAv = fnr,
                endretAvType = EndretAvType.PERSONBRUKERIDENT,
            )
        }
    }

    private fun samtykkeForespurtMelding(
        fnr: String,
        stillingId: UUID,
        stillingsTittel: String,
        forespurtAvIdent: String,
    ) = """
        {
            "@event_name": "samtykke-forespurt-om-deling-av-cv",
            "fnr": "$fnr",
            "stillingsId": "$stillingId",
            "stillingsTittel": "$stillingsTittel",
            "svarfrist": "${ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS)}",
            "forespurtAvIdent": "$forespurtAvIdent",
            "forespurtTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
        }
    """.trimIndent()

    private fun samtykkeBesvartMelding(
        fnr: String,
        stillingId: UUID,
        samtykkeGitt: Boolean,
        besvartAvIdent: String,
        besvartAvIdentType: IdentType,
    ) = """
        {
            "@event_name": "samtykke-forespørsel-om-deling-av-cv-besvart",
            "fnr": "$fnr",
            "stillingsId": "$stillingId",
            "stillingsTittel": "Test Stilling",
            "samtykkeGitt": "$samtykkeGitt",
            "besvartAvIdent": "$besvartAvIdent",
            "besvartAvIdentType": "$besvartAvIdentType",
            "besvartTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
        }
    """.trimIndent()

    private fun samtykkeTrukketMelding(
        fnr: String,
        stillingId: UUID,
        trukketAvIdent: String,
        trukketAvIdentType: IdentType,
    ) = """
        {
            "@event_name": "samtykke-til-deling-av-cv-trukket",
            "fnr": "$fnr",
            "stillingsId": "$stillingId",
            "stillingsTittel": "Test Stilling",
            "trukketAvIdent": "$trukketAvIdent",
            "trukketAvIdentType": "$trukketAvIdentType",
            "trukketTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
        }
    """.trimIndent()

    private fun selvbetjentSamtykkeGittMelding(
        fnr: String,
        stillingId: UUID,
    ) = """
        {
            "@event_name": "selvbetjent-samtykke-til-deling-av-cv-gitt",
            "fnr": "$fnr",
            "stillingsId": "$stillingId",
            "stillingsTittel": "Test Stilling",
            "samtykkeGittTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
        }
    """.trimIndent()

    private fun registrertFattJobbenMelding(
        stillingId: UUID,
        fnr: String,
        navIdent: String,
    ) = """
        {
            "@event_name": "RegistrertFåttJobben",
            "stillingsId": "$stillingId",
            "fnr": "$fnr",
            "utførtAvNavIdent": "$navIdent",
            "tidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
        }
    """.trimIndent()

    private fun lukketKandidatlisteMelding(
        stillingId: UUID,
        navIdent: String,
        fnrFikkJobben: List<String>,
        fnrFikkIkkeJobben: List<String>,
    ) = """
        {
            "@event_name": "LukketKandidatliste",
            "stillingsId": "$stillingId",
            "utførtAvNavIdent": "$navIdent",
            "tidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "fnrFikkJobben": [${fnrFikkJobben.joinToString(",") { "\"$it\"" }}],
            "fnrFikkIkkeJobben": [${fnrFikkIkkeJobben.joinToString(",") { "\"$it\"" }}]
        }
    """.trimIndent()
}
