package no.nav.toi

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.EndretAvType
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.MockProducer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffSvartJaTreffstatusEndretLytterTest {
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

    private val rapid = TestRapid()
    private val databaseConfig = DatabaseConfig(localEnv, meterRegistry)
    private val testRepository = TestRepository(databaseConfig)
    private val repository = Repository(databaseConfig, "http://url")
    private val app = App(rapid, Repository(databaseConfig, "http://url"), MockProducer(), MockConsumer(
        OffsetResetStrategy.EARLIEST))

    @BeforeEach
    fun setup() {
        rapid.reset()
        testRepository.slettAlt()
        app.start()
    }

    @AfterAll
    fun teardown() {
        localPostgres.close()
        app.stop()
    }

    @Test
    fun `fullfort treffstatus skal flytte aktivitetskort til fullført med NAVIDENT som endrer`() {
        testTreffstatusEndret(
            treffstatus = "fullført",
            forventetAktivitetsStatus = AktivitetsStatus.FULLFORT,
            forventetEndretAvType = EndretAvType.NAVIDENT,
            endretAvPersonbruker = false
        )
    }

    @Test
    fun `avlyst treffstatus skal flytte aktivitetskort til avbrutt med NAVIDENT som endrer`() {
        testTreffstatusEndret(
            treffstatus = "avlyst",
            forventetAktivitetsStatus = AktivitetsStatus.AVBRUTT,
            forventetEndretAvType = EndretAvType.NAVIDENT,
            endretAvPersonbruker = false
        )
    }

    private fun testTreffstatusEndret(
        treffstatus: String,
        forventetAktivitetsStatus: AktivitetsStatus,
        forventetEndretAvType: EndretAvType,
        endretAvPersonbruker: Boolean
    ) {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val tittel = "Test Rekrutteringstreff"
        val fraTid = ZonedDateTime.of(2025, 10, 1, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))
        val tilTid = fraTid.plusHours(2)
        val opprettetAv = "testuser"
        val gateadresse = "Test Sted"
        val postnummer = "1234"
        val poststed = "Test Poststed"
        val endretAv = "Arrangør som endret treffstatus"

        repository.opprettRekrutteringstreffInvitasjon(
            fnr,
            rekrutteringstreffId,
            tittel,
            "Beskrivelse av rekrutteringstreff",
            fraTid.toLocalDate(),
            tilTid.toLocalDate(),
            "formatertTid",
            opprettetAv,
            gateadresse,
            postnummer,
            poststed
        )

        val nowFørSendTestmessage = ZonedDateTime.now()
        rapid.sendTestMessage(
            rapidMelding(
                fnr = fnr,
                rekrutteringstreffId = rekrutteringstreffId,
                treffstatus = treffstatus,
                endretAv = endretAv,
                endretAvPersonbruker = endretAvPersonbruker
            )
        )

        val rekrutteringstreffHendelser = testRepository.hentAlle()
        assertThat(rekrutteringstreffHendelser).hasSize(2)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(0)

        val expectedDetaljer = """[{"label":"Tid","verdi":"formatertTid"},{"label":"Sted","verdi":"Test Sted, 1234 Test Poststed"}]"""
        rekrutteringstreffHendelser.sortedBy { it.opprettetTidspunkt }.apply {
            assertThat(this[1].tittel).isEqualTo(this[0].tittel)
            assertThat(this[1].beskrivelse).isEqualTo(this[0].beskrivelse)
            assertThat(this[1].fnr).isEqualTo(fnr)
            assertThat(this[1].fraTid).isEqualTo(fraTid.toLocalDate())
            assertThat(this[1].tilTid).isEqualTo(tilTid.toLocalDate())
            assertThat(this[1].detaljer).isEqualToIgnoringWhitespace(expectedDetaljer)
            assertThat(this[1].aktivitetskortId).isEqualTo(this[0].aktivitetskortId)
            assertThat(this[1].rekrutteringstreffId).isEqualTo(rekrutteringstreffId)
            assertThat(this[1].aktivitetsStatus).isEqualTo(forventetAktivitetsStatus.name)
            assertThat(this[1].opprettetAv).isEqualTo(endretAv)
            assertThat(this[1].opprettetAvType).isEqualTo(forventetEndretAvType.name)
            assertThat(this[1].opprettetTidspunkt).isCloseTo(nowFørSendTestmessage, within(100, ChronoUnit.MILLIS))
        }
    }

    @Test
    fun `Om treffstatus endres men aktivitetskort ikke finnes skal vi ikke feile`() {
        rapid.sendTestMessage(
            rapidMelding(
                fnr = "12345678910",
                rekrutteringstreffId = UUID.randomUUID(),
                treffstatus = "avlyst",
                endretAv = "Z123456",
                endretAvPersonbruker = false
            )
        )
        val rekrutteringstreffHendelser = testRepository.hentAlle()
        assertThat(rekrutteringstreffHendelser).hasSize(0)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(0)
    }

    private fun rapidMelding(
        fnr: String,
        rekrutteringstreffId: UUID,
        treffstatus: String,
        endretAv: String,
        endretAvPersonbruker: Boolean
    ): String = """
        {
            "@event_name": "svartJaTreffstatusEndret",
            "fnr":"$fnr",
            "rekrutteringstreffId":"$rekrutteringstreffId",
            "treffstatus": "$treffstatus",
            "endretAv": "$endretAv",
            "endretAvPersonbruker": $endretAvPersonbruker
        }
        """.trimIndent()
}
