package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.DeleCvMedArbeidsgiverType
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.rekrutteringsbistand.IdentType
import no.nav.toi.ubruktPortnrFra11000.ubruktPortnr
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.internals.AutoOffsetResetStrategy.StrategyType
import org.apache.kafka.clients.producer.MockProducer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
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
    private val repository = Repository(databaseConfig, "http://url", "http://url/stilling")
    private val app = App(
        port = appPort,
        rapidsConnection = rapid,
        repository = repository,
        dabAktivitetskortTopic = "topic",
        producer = MockProducer(),
        consumer = MockConsumer(StrategyType.EARLIEST.toString()),
        dabAktivitetskortFeilTopic = "topic",
        leaderElection = LeaderElectionMock(),
        meterRegistry = meterRegistry,
        isRunning = {true},
        isReady = {true}
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
        val svarfrist = ZonedDateTime.parse("2026-11-27T00:59:59.999+01:00[Europe/Oslo]")

        rapid.sendTestMessage(
            samtykkeForespurtMelding(
                fnr = fnr,
                stillingId = stillingId,
                stillingsTittel = tittel,
                forespurtAvIdent = opprettetAv,
                svarfrist = svarfrist,
            )
        )
        val rekrutteringsbistandStillinger = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(rekrutteringsbistandStillinger).hasSize(1)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)

        val expectedDetaljer = "[{\"label\":\"Svarfrist\",\"verdi\":\"26.\u00A0november\u00A02026\"}]"
        rekrutteringsbistandStillinger.apply {
            assertThat(this[0].tittel).isEqualTo(tittel)
            assertThat(this[0].stillingId).isEqualTo(stillingId)
            assertThat(this[0].beskrivelse).isEqualTo("Nav hjelper en arbeidsgiver med å finne kandidater til en stilling, og tror den kan passe for deg.")
            assertThat(this[0].detaljer).isEqualToIgnoringWhitespace( expectedDetaljer)
            assertThat(this[0].aktivitetskortId).isEqualTo(inspektør.message(0)["aktivitetskortuuid"].asString().toUUID())
            assertThat(this[0].aktivitetsStatus).isEqualTo(AktivitetsStatus.FORSLAG.name)
            assertEtikett(this[0], "Venter svar fra deg", "NEUTRAL")
            assertThat(this[0].aktivitetsType).isEqualTo(DeleCvMedArbeidsgiverType.dbType)
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
            messageId = UUID.randomUUID(),
            svarfrist = ZonedDateTime.now().plusDays(2),
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
            messageId = UUID.randomUUID(),
            svarfrist = ZonedDateTime.now().plusDays(2),
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
            assertThat(hendelse.etiketter).isEqualTo("[]")
        }
    }

    @Test
    fun `utløpt svarfrist skal flytte aktivitetskort til avbrutt`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId)

        rapid.sendTestMessage(samtykkeSvarfristUtløptMelding(fnr, stillingId))

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(2)
        hendelser.last().also { hendelse ->
            assertThat(hendelse.aktivitetsStatus).isEqualTo(AktivitetsStatus.AVBRUTT.name)
            assertThat(hendelse.opprettetAv).isEqualTo(EndretAvType.SYSTEM.name)
            assertThat(hendelse.opprettetAvType).isEqualTo(EndretAvType.SYSTEM.name)
            assertThat(hendelse.stillingId).isEqualTo(stillingId)
            assertThat(hendelse.fnr).isEqualTo(fnr)
            assertThat(hendelse.etiketter).isEqualTo("[]")
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
                navIdent = navIdent,
            )
        )

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)
        assertThat(repository.hentSisteAktivitetsstatus(checkNotNull(repository.hentAktivitetskortIdForDeltStilling(fnr, stillingId))))
            .isEqualTo(AktivitetsStatus.GJENNOMFORES)
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val behov = rapid.inspektør.message(0)
        assertThat(behov["@behov"].toList().map { it.asString() }).containsExactly("fodselsnummer")
        assertThat(behov["aktørId"].asString()).isEqualTo("1234567890123")
        assertThat(behov.path("fnr").isMissingNode).isTrue()
        assertThat(behov.path("fodselsnummer").isMissingNode).isTrue()
        assertThat(rapid.inspektør.key(0)).isEqualTo(stillingId.toString())

        val løsning = (behov as ObjectNode).deepCopy().put("fodselsnummer", fnr).toString()
        rapid.reset()
        rapid.sendTestMessage(løsning)

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger().filter { it.fnr == fnr }
        assertThat(hendelser).hasSize(3)
        hendelser.last().also { hendelse ->
            assertThat(hendelse.aktivitetsStatus).isEqualTo(AktivitetsStatus.FULLFORT.name)
            assertThat(hendelse.opprettetAv).isEqualTo(navIdent)
            assertThat(hendelse.opprettetAvType).isEqualTo(EndretAvType.NAVIDENT.name)
            assertEtikett(hendelse, "Fått jobben 🎉", "NEUTRAL")
        }
        assertThat(rapid.inspektør.size).isZero()

        rapid.sendTestMessage(løsning)
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(3)
    }

    @Test
    fun `registrert fått jobben skal legge fødselsnummerbehov foran eksisterende behov`() {
        val stillingId = UUID.randomUUID()

        rapid.sendTestMessage(
            registrertFattJobbenMelding(
                stillingId = stillingId,
                navIdent = "Z123456",
                behov = listOf("annetBehov"),
            )
        )

        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@behov"].toList().map { it.asString() }).containsExactly("fodselsnummer", "annetBehov")
        assertThat(melding["stillingsId"].asString()).isEqualTo(stillingId.toString())
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).isEmpty()
    }

    @Test
    fun `registrert fått jobben skal vente når fødselsnummerbehov allerede er lagt på`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId, AktivitetsStatus.GJENNOMFORES)

        rapid.sendTestMessage(
            registrertFattJobbenMelding(
                stillingId = stillingId,
                navIdent = "Z123456",
                behov = listOf("fodselsnummer"),
            )
        )

        assertThat(rapid.inspektør.size).isZero()
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)
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
                navIdent = navIdent,
            )
        )

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger().filter { it.fnr == fnr }
        assertThat(hendelser).hasSize(2)
        assertThat(hendelser.last().aktivitetsStatus).isEqualTo(AktivitetsStatus.AVBRUTT.name)
    }

    @Test
    fun `registrert fått jobben skal ikke endre status før kandidaten har svart ja`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId)

        rapid.sendTestMessage(
            registrertFattJobbenMelding(
                stillingId = stillingId,
                fodselsnummer = fnr,
                navIdent = "Z123456",
            )
        )

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(1)
        assertThat(hendelser.single().aktivitetsStatus).isEqualTo(AktivitetsStatus.FORSLAG.name)
    }

    @Test
    fun `lukket kandidatliste skal fullføre bare kandidater som har svart ja`() {
        val stillingId = UUID.randomUUID()
        val navIdent = "Z999999"
        val kandidatSomHarSvartJa = "01010012345"
        val kandidatSomHarSvartNei = "02020012345"
        val kandidatSomFikkJobben = "03030012345"
        val aktørIdJa = "1000000000001"
        val aktørIdNei = "1000000000002"
        val aktørIdFikkJobben = "1000000000003"

        opprettDeltStilling(kandidatSomHarSvartJa, stillingId, AktivitetsStatus.GJENNOMFORES)
        opprettDeltStilling(kandidatSomHarSvartNei, stillingId, AktivitetsStatus.AVBRUTT)
        opprettDeltStilling(kandidatSomFikkJobben, stillingId, AktivitetsStatus.GJENNOMFORES)

        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = stillingId,
                navIdent = navIdent,
                aktørIderFikkJobben = listOf(aktørIdFikkJobben),
                aktørIderFikkIkkeJobben = listOf(aktørIdJa, aktørIdNei),
                behov = listOf("annetBehov"),
            )
        )

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(6)
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val behov = rapid.inspektør.message(0)
        assertThat(behov["@behov"].toList().map { it.asString() }).containsExactly("fodselsnummerliste", "annetBehov")
        assertThat(behov["aktørIder"].toList().map { it.asString() }).containsExactly(aktørIdJa, aktørIdNei)
        assertThat(behov["aktørIderFikkJobben"].toList().map { it.asString() }).containsExactly(aktørIdFikkJobben)
        assertThat(rapid.inspektør.key(0)).isEqualTo(stillingId.toString())

        val løsning = (behov as ObjectNode).deepCopy().apply {
            putObject("fodselsnummerliste")
                .put(aktørIdJa, kandidatSomHarSvartJa)
                .put(aktørIdNei, kandidatSomHarSvartNei)
                .put(aktørIdFikkJobben, kandidatSomFikkJobben)
        }.toString()
        rapid.reset()
        rapid.sendTestMessage(løsning)

        val jaKandidatHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
            .filter { it.fnr == kandidatSomHarSvartJa }
        val neiKandidatHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
            .filter { it.fnr == kandidatSomHarSvartNei }

        assertThat(jaKandidatHendelser).hasSize(3)
        assertThat(jaKandidatHendelser.last().aktivitetsStatus).isEqualTo(AktivitetsStatus.FULLFORT.name)
        assertThat(jaKandidatHendelser.last().opprettetAv).isEqualTo(navIdent)
        assertThat(jaKandidatHendelser.last().opprettetAvType).isEqualTo(EndretAvType.NAVIDENT.name)
        assertEtikett(jaKandidatHendelser.last(), "Ikke fått jobben", "NEUTRAL")

        // Kandidat med NEI skal ikke oppdateres pa nytt ved lukking av liste.
        assertThat(neiKandidatHendelser).hasSize(2)

        // Kandidat som fikk jobben trenger ikke oppdateres pa nytt ved lukking av liste.
        val fikkJobbenHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
            .filter { it.fnr == kandidatSomFikkJobben }
        assertThat(fikkJobbenHendelser).hasSize(2)
        assertThat(fikkJobbenHendelser.last().aktivitetsStatus).isEqualTo(AktivitetsStatus.GJENNOMFORES.name)
        assertThat(rapid.inspektør.size).isZero()

        rapid.sendTestMessage(løsning)
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(7)
    }

    @Test
    fun `lukket kandidatliste skal fullføre flere kandidater samlet og være idempotent`() {
        val stillingId = UUID.randomUUID()
        val fnr1 = "01010012345"
        val fnr2 = "02020012345"
        val aktørId1 = "1000000000001"
        val aktørId2 = "1000000000002"
        val aktørIdUtenKort = "1000000000003"
        val fødselsnumre = mapOf(aktørId1 to fnr1, aktørId2 to fnr2, aktørIdUtenKort to "03030012345")

        listOf(fnr1, fnr2).forEach { fnr ->
            opprettDeltStilling(fnr, stillingId, AktivitetsStatus.GJENNOMFORES)
        }

        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = stillingId,
                navIdent = "Z999999",
                aktørIderFikkJobben = emptyList(),
                aktørIderFikkIkkeJobben = listOf(aktørId1, aktørId1, aktørId2, aktørIdUtenKort),
                fodselsnummerliste = fødselsnumre,
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
                aktørIderFikkJobben = emptyList(),
                aktørIderFikkIkkeJobben = listOf(aktørId1, aktørId2),
                fodselsnummerliste = fødselsnumre,
            )
        )

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger())
            .filteredOn { it.aktivitetsStatus == AktivitetsStatus.FULLFORT.name }
            .hasSize(2)
            .filteredOn { it.opprettetTidspunkt == opprettetTidspunkt }
            .hasSize(2)
    }

    @Test
    fun `lukket kandidatliste skal vente når fødselsnummerliste allerede er etterspurt`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId, AktivitetsStatus.GJENNOMFORES)

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)

        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = stillingId,
                navIdent = "Z999999",
                aktørIderFikkJobben = emptyList(),
                aktørIderFikkIkkeJobben = listOf("1000000000001"),
                behov = listOf("fodselsnummerliste", "annetBehov"),
            )
        )

        assertThat(rapid.inspektør.size).isZero()
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)
    }

    @Test
    fun `lukket kandidatliste skal ignorere andre hendelser`() {
        val melding = jacksonObjectMapper().readTree(
            lukketKandidatlisteMelding(
                stillingId = UUID.randomUUID(),
                navIdent = "Z999999",
                aktørIderFikkJobben = emptyList(),
                aktørIderFikkIkkeJobben = listOf("1000000000001"),
            )
        ) as ObjectNode

        rapid.sendTestMessage(melding.deepCopy().put("@event_name", "annenHendelse").toString())

        assertThat(rapid.inspektør.size).isZero()
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).isEmpty()
    }

    @Test
    fun `lukket kandidatliste uten kandidater skal ikke oppdatere databasen eller publisere videre`() {
        val stillingId = UUID.randomUUID()
        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = stillingId,
                navIdent = "Z999999",
                aktørIderFikkJobben = emptyList(),
                aktørIderFikkIkkeJobben = emptyList(),
                fodselsnummerliste = emptyMap(),
            )
        )

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).isEmpty()
        assertThat(rapid.inspektør.size).isZero()
    }

    @Test
    fun `selvbetjent samtykke gitt skal opprette aktivitetskort i gjennomføres`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val før = ZonedDateTime.now()

        rapid.sendTestMessage(selvbetjentSamtykkeGittMelding(fnr = fnr, stillingId = stillingId))

        val aktivitetskort = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(aktivitetskort).hasSize(1)
        assertThat(rapid.inspektør.size).isZero()

        aktivitetskort.single().apply {
            assertThat(this.fnr).isEqualTo(fnr)
            assertThat(this.stillingId).isEqualTo(stillingId)
            assertThat(tittel).isEqualTo("Test Stilling")
            assertThat(beskrivelse).isEqualTo(DeleCvMedArbeidsgiverType.beskrivelse)
            assertThat(detaljer).isEqualToIgnoringWhitespace("[]")
            assertThat(aktivitetsStatus).isEqualTo(AktivitetsStatus.GJENNOMFORES.name)
            assertThat(aktivitetsType).isEqualTo(DeleCvMedArbeidsgiverType.dbType)
            assertThat(opprettetAv).isEqualTo(fnr)
            assertThat(opprettetAvType).isEqualTo(EndretAvType.PERSONBRUKERIDENT.name)
            assertThat(opprettetTidspunkt).isBetween(før, ZonedDateTime.now())
            assertThat(feil).isNull()

            val etikett = jacksonObjectMapper().readTree(etiketter).single()
            assertThat(etikett["tekst"].asString()).isEqualTo("Venter på å bli kontaktet")
            assertThat(etikett["sentiment"].asString()).isEqualTo("POSITIVE")

            val handling = jacksonObjectMapper().readTree(handlinger).single()
            assertThat(handling["tekst"].asString()).isEqualTo(DeleCvMedArbeidsgiverType.handlingTittel)
            assertThat(handling["subtekst"].asString()).isEqualTo("Sjekk ut stillingen og status på samtykke")
            assertThat(handling["url"].asString()).isEqualTo("http://url/stilling/$stillingId")
            assertThat(handling["lenkeType"].asString()).isEqualTo("FELLES")
        }
    }

    @Test
    fun `selvbetjent samtykke gitt for samme kandidat og stilling skal ignoreres`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()

        rapid.sendTestMessage(selvbetjentSamtykkeGittMelding(fnr = fnr, stillingId = stillingId))
        val første = testRepository.hentAlleRekrutteringsbistandStillinger()
        rapid.sendTestMessage(selvbetjentSamtykkeGittMelding(fnr = fnr, stillingId = stillingId))

        val etterpå = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(etterpå).hasSize(1)
        assertThat(etterpå.single()).usingRecursiveComparison().isEqualTo(første.single())
    }

    @Test
    fun `selvbetjent samtykke gitt skal flytte eksisterende aktivitetskort fra veileder til gjennomføres`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId)

        rapid.sendTestMessage(selvbetjentSamtykkeGittMelding(fnr = fnr, stillingId = stillingId))

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(2)
        assertThat(hendelser.map { it.aktivitetskortId }.distinct()).hasSize(1)
        hendelser.last().apply {
            assertThat(aktivitetsStatus).isEqualTo(AktivitetsStatus.GJENNOMFORES.name)
            assertThat(opprettetAv).isEqualTo(fnr)
            assertThat(opprettetAvType).isEqualTo(EndretAvType.PERSONBRUKERIDENT.name)
            val etikett = jacksonObjectMapper().readTree(etiketter).single()
            assertThat(etikett["tekst"].asString()).isEqualTo("Venter på å bli kontaktet")
            assertThat(etikett["sentiment"].asString()).isEqualTo("POSITIVE")
            val handling = jacksonObjectMapper().readTree(handlinger).single()
            assertThat(handling["subtekst"].asString()).isEqualTo("Sjekk ut stillingen og status på samtykke")
            assertThat(handling["url"].asString()).isEqualTo("http://url/stilling/$stillingId")
        }

        rapid.sendTestMessage(selvbetjentSamtykkeGittMelding(fnr = fnr, stillingId = stillingId))
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)
    }

    @Test
    fun `selvbetjent samtykke gitt skal flytte avbrutt aktivitetskort til gjennomføres`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId, AktivitetsStatus.AVBRUTT)

        rapid.sendTestMessage(selvbetjentSamtykkeGittMelding(fnr = fnr, stillingId = stillingId))

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(3)
        assertThat(hendelser.map { it.aktivitetskortId }.distinct()).hasSize(1)
        hendelser.last().apply {
            assertThat(aktivitetsStatus).isEqualTo(AktivitetsStatus.GJENNOMFORES.name)
            assertThat(opprettetAv).isEqualTo(fnr)
            assertThat(opprettetAvType).isEqualTo(EndretAvType.PERSONBRUKERIDENT.name)
        }
    }

    @Test
    fun `selvbetjent samtykke gitt med ugyldige felter skal ikke opprette aktivitetskort`() {
        val gyldig = jacksonObjectMapper().readTree(
            selvbetjentSamtykkeGittMelding(fnr = "01010012345", stillingId = UUID.randomUUID())
        ) as ObjectNode

        rapid.sendTestMessage(gyldig.deepCopy().put("stillingsId", "ikke-en-uuid").toString())
        rapid.sendTestMessage(gyldig.deepCopy().put("samtykkeGittTidspunkt", "ikke-en-dato").toString())
        rapid.sendTestMessage(gyldig.deepCopy().apply { remove("fnr") }.toString())
        rapid.sendTestMessage(gyldig.deepCopy().apply { remove("stillingsinfo") }.toString())

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).isEmpty()
        assertThat(rapid.inspektør.size).isZero()
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
            if (samtykkeGitt) {
                assertEtikett(hendelse, "Venter på å bli kontaktet", "POSITIVE")
            } else {
                assertThat(hendelse.etiketter).isEqualTo("[]")
            }
        }
    }

    @Test
    fun `CV-delt skal publisere eget behov og oppdatere etiketten idempotent med samme status og veileder`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val navIdent = "Z123456"
        opprettDeltStilling(fnr, stillingId)
        rapid.sendTestMessage(
            samtykkeBesvartMelding(fnr, stillingId, true, navIdent, IdentType.NAV_IDENT)
        )
        val melding = cvDeltMelding(stillingId, behov = listOf("annetBehov"))

        rapid.sendTestMessage(melding)

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val behov = rapid.inspektør.message(0) as ObjectNode
        assertThat(behov["@behov"].toList().map { it.asString() })
            .containsExactly("fodselsnummerliste", "annetBehov")
        assertThat(behov["aktørIder"].toList().map { it.asString() }).containsExactly("1000000000001")
        assertThat(behov["@event_name"].asString()).isEqualTo("kandidat_v2.DelCvMedArbeidsgiver")
        assertThat(rapid.inspektør.key(0)).isEqualTo(stillingId.toString())
        val løsning = behov.deepCopy().apply {
            putObject("fodselsnummerliste").put("1000000000001", fnr)
        }.toString()
        rapid.reset()

        rapid.sendTestMessage(løsning)
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(3)
        rapid.sendTestMessage(løsning)

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(hendelser).hasSize(3)
        assertThat(hendelser.last().aktivitetsStatus).isEqualTo(AktivitetsStatus.GJENNOMFORES.name)
        assertEtikett(hendelser.last(), "CV er delt med arbeidsgiver", "NEUTRAL")
        assertThat(rapid.inspektør.size).isZero()
    }

    @Test
    fun `CV-delt skal bare oppdatere riktig kandidat og stilling med samtykke`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val annenStilling = UUID.randomUUID()
        opprettDeltStilling(fnr, stillingId, AktivitetsStatus.GJENNOMFORES)
        opprettDeltStilling("02020012345", stillingId, AktivitetsStatus.GJENNOMFORES)
        opprettDeltStilling(fnr, annenStilling, AktivitetsStatus.GJENNOMFORES)
        val før = testRepository.hentAlleRekrutteringsbistandStillinger()

        rapid.sendTestMessage(
            cvDeltMelding(
                stillingId,
                kandidater = listOf("1000000000001", "1000000000003"),
                fodselsnummerliste = mapOf("1000000000001" to fnr, "1000000000003" to "03030012345"),
            )
        )

        val nye = testRepository.hentAlleRekrutteringsbistandStillinger().filter { it.id !in før.map { it.id } }
        assertThat(nye).hasSize(1)
        assertThat(nye.single().fnr).isEqualTo(fnr)
        assertThat(nye.single().stillingId).isEqualTo(stillingId)
        assertEtikett(nye.single(), "CV er delt med arbeidsgiver", "NEUTRAL")
    }

    @Test
    fun `CV-delt skal ikke endre forslag eller avbrutte eller fullførte kort`() {
        val fnr = "01010012345"
        listOf(AktivitetsStatus.FORSLAG, AktivitetsStatus.AVBRUTT, AktivitetsStatus.FULLFORT).forEach { status ->
            val stillingId = UUID.randomUUID()
            opprettDeltStilling(fnr, stillingId, status)
            val før = testRepository.hentAlleRekrutteringsbistandStillinger().map { it.messageId }

            rapid.sendTestMessage(cvDeltMelding(stillingId, fodselsnummerliste = mapOf("1000000000001" to fnr)))

            assertThat(testRepository.hentAlleRekrutteringsbistandStillinger().map { it.messageId }).isEqualTo(før)
        }
    }

    @Test
    fun `CV-delt skal ikke publisere fødselsnummerbehov når det allerede er etterspurt`() {
        val stillingId = UUID.randomUUID()
        rapid.sendTestMessage(cvDeltMelding(stillingId, behov = listOf("fodselsnummerliste")))
        assertThat(rapid.inspektør.size).isZero()
    }

    private fun assertEtikett(kort: RekrutteringsbistandStilling, tekst: String, sentiment: String) {
        val etiketter = jacksonObjectMapper().readTree(kort.etiketter)
        assertThat(etiketter).hasSize(1)
        assertThat(etiketter.single()["tekst"].asString()).isEqualTo(tekst)
        assertThat(etiketter.single()["sentiment"].asString()).isEqualTo(sentiment)
    }

    private fun cvDeltMelding(
        stillingId: UUID,
        kandidater: List<String> = listOf("1000000000001"),
        fodselsnummerliste: Map<String, String?>? = null,
        behov: List<String>? = null,
    ): String = jacksonObjectMapper().writeValueAsString(
        buildMap<String, Any> {
            put("@event_name", "kandidat_v2.DelCvMedArbeidsgiver")
            put("@id", UUID.randomUUID().toString())
            put("stillingsId", stillingId.toString())
            put("stillingsinfo", mapOf("stillingsid" to stillingId.toString()))
            put("utførtAvNavIdent", "Z123456")
            put("tidspunkt", ZonedDateTime.now().toString())
            put("kandidater", kandidater.associateWith { mapOf("kandidatnummer" to "test") })
            fodselsnummerliste?.let { put("fodselsnummerliste", it) }
            behov?.let { put("@behov", it) }
        }
    )

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
            messageId = UUID.randomUUID(),
            svarfrist = ZonedDateTime.now().plusDays(2),
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
        svarfrist: ZonedDateTime = ZonedDateTime.now().plusDays(7).truncatedTo(ChronoUnit.MILLIS),
    ) = """
        {
            "@event_name": "samtykke-forespurt-om-deling-av-cv",
            "fnr": "$fnr",
            "stillingsId": "$stillingId",
            "stillingsTittel": "$stillingsTittel",
            "svarfrist": "$svarfrist",
            "forespurtAvIdent": "$forespurtAvIdent",
            "forespurtTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "stillingsinfo": {
              "stillingsinfoid": "3a436047-7886-4b97-b2b3-a780554a9bcd",
              "stillingsid": "$stillingId",
              "eier": {
                "navident": "$forespurtAvIdent",
                "navn": "F_$forespurtAvIdent E_$forespurtAvIdent",
                "navKontorEnhetId": "0300"
              },
              "stillingskategori": "STILLING",
              "rekrutteringstreffId": null
            },
            "stilling": {
              "stillingstittel": "Jobbanalytiker",
              "erDirektemeldt": true,
              "stillingOpprettetTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
              "antallStillinger": 1,
              "organisasjonsnummer": "936337651",
              "stillingensPubliseringstidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
            }
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
            "besvartTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "stillingsinfo": {
              "stillingsinfoid": "3a436047-7886-4b97-b2b3-a780554a9bcd",
              "stillingsid": "$stillingId",
              "eier": {
                "navident": "$besvartAvIdent",
                "navn": "F_$besvartAvIdent E_$besvartAvIdent",
                "navKontorEnhetId": "0300"
              },
              "stillingskategori": "STILLING",
              "rekrutteringstreffId": null
            },
            "stilling": {
              "stillingstittel": "Jobbanalytiker",
              "erDirektemeldt": true,
              "stillingOpprettetTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
              "antallStillinger": 1,
              "organisasjonsnummer": "936337651",
              "stillingensPubliseringstidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
            }
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
            "trukketTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "stillingsinfo": {
              "stillingsinfoid": "3a436047-7886-4b97-b2b3-a780554a9bcd",
              "stillingsid": "$stillingId",
              "eier": {
                "navident": "$trukketAvIdent",
                "navn": "F_$trukketAvIdent E_$trukketAvIdent",
                "navKontorEnhetId": "0300"
              },
              "stillingskategori": "STILLING",
              "rekrutteringstreffId": null
            },
            "stilling": {
              "stillingstittel": "Jobbanalytiker",
              "erDirektemeldt": true,
              "stillingOpprettetTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
              "antallStillinger": 1,
              "organisasjonsnummer": "936337651",
              "stillingensPubliseringstidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
            }
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
            "samtykkeGittTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "stillingsinfo": {
              "stillingsinfoid": "3a436047-7886-4b97-b2b3-a780554a9bcd",
              "stillingsid": "$stillingId",
              "eier": {
                "navident": "Z01234567",
                "navn": "F_Z01234567 E_Z01234567",
                "navKontorEnhetId": "0300"
              },
              "stillingskategori": "STILLING",
              "rekrutteringstreffId": null
            },
            "stilling": {
              "stillingstittel": "Jobbanalytiker",
              "erDirektemeldt": true,
              "stillingOpprettetTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
              "antallStillinger": 1,
              "organisasjonsnummer": "936337651",
              "stillingensPubliseringstidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
            }
        }
    """.trimIndent()

    private fun samtykkeSvarfristUtløptMelding(
        fnr: String,
        stillingId: UUID,
    ) = """
        {
            "@event_name": "samtykke-forespørsel-deling-av-cv-svarfrist-utløpt",
            "stillingsId": "$stillingId",
            "fnr": "$fnr",
            "svarfrist": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "stillingsinfo": {
              "stillingsinfoid": "3a436047-7886-4b97-b2b3-a780554a9bcd",
              "stillingsid": "$stillingId",
              "eier": {
                "navident": "Z012334",
                "navn": "F_Z012334 E_Z012334",
                "navKontorEnhetId": "0300"
              },
              "stillingskategori": "STILLING",
              "rekrutteringstreffId": null
            },
            "stilling": {
              "stillingstittel": "Jobbanalytiker",
              "erDirektemeldt": true,
              "stillingOpprettetTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
              "antallStillinger": 1,
              "organisasjonsnummer": "936337651",
              "stillingensPubliseringstidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
            }
        }
    """.trimIndent()

    private fun registrertFattJobbenMelding(
        stillingId: UUID,
        navIdent: String,
        fodselsnummer: String? = null,
        behov: List<String>? = null,
    ) = """
        {
            "@event_name": "kandidat_v2.RegistrertFåttJobben",
            "stillingsId": "$stillingId",
            "aktørId": "1234567890123",
            ${behov?.let { "\"@behov\": [${it.joinToString(",") { behov -> "\"$behov\"" }}]," } ?: ""}
            ${fodselsnummer?.let { "\"fodselsnummer\": \"$it\"," } ?: ""}
            "utførtAvNavIdent": "$navIdent",
            "tidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "stillingsinfo": {
              "stillingsinfoid": "3a436047-7886-4b97-b2b3-a780554a9bcd",
              "stillingsid": "$stillingId",
              "eier": {
                "navident": "$navIdent",
                "navn": "F_$navIdent E_$navIdent",
                "navKontorEnhetId": "0300"
              },
              "stillingskategori": "STILLING",
              "rekrutteringstreffId": null
            },
            "stilling": {
              "stillingstittel": "Jobbanalytiker",
              "erDirektemeldt": true,
              "stillingOpprettetTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
              "antallStillinger": 1,
              "organisasjonsnummer": "936337651",
              "stillingensPubliseringstidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
            }
        }
    """.trimIndent()

    private fun lukketKandidatlisteMelding(
        stillingId: UUID,
        navIdent: String,
        aktørIderFikkJobben: List<String>,
        aktørIderFikkIkkeJobben: List<String>,
        fodselsnummerliste: Map<String, String?>? = null,
        behov: List<String>? = null,
    ) = """
        {
            "@event_name": "kandidat_v2.LukketKandidatliste",
            "stillingsId": "$stillingId",
            "utførtAvNavIdent": "$navIdent",
            "tidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "aktørIderFikkJobben": [${aktørIderFikkJobben.joinToString(",") { "\"$it\"" }}],
            "aktørIderFikkIkkeJobben": [${aktørIderFikkIkkeJobben.joinToString(",") { "\"$it\"" }}],
            ${fodselsnummerliste?.let { "\"fodselsnummerliste\": ${jacksonObjectMapper().writeValueAsString(it)}," } ?: ""}
            ${behov?.let { "\"@behov\": ${jacksonObjectMapper().writeValueAsString(it)}," } ?: ""}
            "stillingsinfo": {
              "stillingsinfoid": "3a436047-7886-4b97-b2b3-a780554a9bcd",
              "stillingsid": "$stillingId",
              "eier": {
                "navident": "$navIdent",
                "navn": "F_$navIdent E_$navIdent",
                "navKontorEnhetId": "0300"
              },
              "stillingskategori": "STILLING",
              "rekrutteringstreffId": null
            },
            "stilling": {
              "stillingstittel": "Jobbanalytiker",
              "erDirektemeldt": true,
              "stillingOpprettetTidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
              "antallStillinger": 1,
              "organisasjonsnummer": "936337651",
              "stillingensPubliseringstidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
            }
        }
    """.trimIndent()
}
