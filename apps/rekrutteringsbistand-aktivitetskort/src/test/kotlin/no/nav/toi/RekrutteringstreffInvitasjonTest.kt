package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.EndretAvType
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.producer.MockProducer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.also
import kotlin.apply
import kotlin.collections.set
import kotlin.text.trimIndent
import kotlin.to

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffInvitasjonTest {
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
    private val app = App(rapid, Repository(databaseConfig), MockProducer(), MockConsumer(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST))

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
    fun `lesing av rekrutteringstreffinvitasjon fra rapid skal lagres i database`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val tittel = "Test Rekrutteringstreff"
        val beskrivelse = "Beskrivelse av rekrutteringstreff"
        val startTid = ZonedDateTime.now().plusDays(1)
        val sluttTid = startTid.plusHours(2)
        val endretAv = "testuser"
        val endretAvType = EndretAvType.NAVIDENT
        val endretTidspunkt = ZonedDateTime.now()
        val antallPlasser = 50
        val sted = "Test Sted"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                rekrutteringstreffId,
                tittel,
                beskrivelse,
                startTid,
                sluttTid,
                endretAv,
                endretAvType,
                endretTidspunkt,
                antallPlasser,
                sted
            )
        )

        val rekrutteringstreffInvitasjoner = testRepository.hentAlle()
        assertThat(rekrutteringstreffInvitasjoner).hasSize(1)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
        rekrutteringstreffInvitasjoner.apply {
            assertThat(this[0].tittel).isEqualTo(tittel)
            assertThat(this[0].beskrivelse).isEqualTo(beskrivelse)
            assertThat(this[0].fraTid).isEqualTo(startTid.toLocalDate())
            assertThat(this[0].tilTid).isEqualTo(sluttTid.toLocalDate())
            assertThat(this[0].aktivitetskortId).isEqualTo(inspektør.message(0)["aktivitetskortuuid"].asText().toUUID())
            assertThat(this[0].rekrutteringstreffId).isEqualTo(rekrutteringstreffId)
            assertThat(this[0].aktivitetsStatus).isEqualTo(AktivitetsStatus.FORSLAG.name)
            assertThat(this[0].opprettetAv).isEqualTo(endretAv)
            assertThat(this[0].opprettetAvType).isEqualTo(endretAvType.name)
            assertThat(this[0].opprettetTidspunkt).isCloseTo(endretTidspunkt, within(10, ChronoUnit.MILLIS))
        }
    }

    @Test
    fun `lesing av rekrutteringsinvitasjon fra rapid med samme kandidat og stilling skal feile`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val startTid = ZonedDateTime.now().plusDays(1)
        val sluttTid = startTid.plusHours(2)
        val endretAvType = EndretAvType.NAVIDENT
        val endretTidspunkt = ZonedDateTime.now()
        val antallPlasser = 53
        val sted = "Test Ste"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                rekrutteringstreffId,
                "Tittel 1",
                "Beskrivelse 1",
                startTid,
                sluttTid,
                "Z0000001",
                endretAvType,
                endretTidspunkt,
                antallPlasser,
                sted
            )
        )
        assertThrows<PSQLException> {
            rapid.sendTestMessage(
                rapidPeriodeMelding(
                    fnr,
                    rekrutteringstreffId,
                    "Tittel 2",
                    "Beskrivelse 2",
                    startTid.plusDays(1),
                    sluttTid.plusDays(1),
                    "Z0000002",
                    endretAvType,
                    endretTidspunkt.plusHours(1),
                    antallPlasser + 10,
                    sted
                )
            )
        }

        val rekrutteringstreffInvitasjoner = testRepository.hentAlle()
        assertThat(rekrutteringstreffInvitasjoner).hasSize(1)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
    }

    @Test
    fun `lesing av rekrutteringstreffinvitasjon skal returnere aktivitetskortuuid på rapid`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val tittel = "Test Rekrutteringstreff"
        val beskrivelse = "Beskrivelse av rekrutteringstreff"
        val startTid = ZonedDateTime.now().plusDays(1)
        val sluttTid = startTid.plusHours(2)
        val endretAv = "testuser"
        val endretAvType = EndretAvType.PERSONBRUKER
        val endretTidspunkt = ZonedDateTime.now()
        val antallPlasser = 1
        val sted = "Oslo"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                rekrutteringstreffId,
                tittel,
                beskrivelse,
                startTid,
                sluttTid,
                endretAv,
                endretAvType,
                endretTidspunkt,
                antallPlasser,
                sted
            )
        )

        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
        inspektør.message(0).also { message ->
            assertThat(message["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
            assertThat(message["fnr"].asText()).isEqualTo(fnr)
            assertThat(message["rekrutteringstreffId"].asText()).isEqualTo(rekrutteringstreffId.toString())
            assertThat(message["tittel"].asText()).isEqualTo(tittel)
            assertThat(message["beskrivelse"].asText()).isEqualTo(beskrivelse)
            assertThat(message["startTid"].asText()).isEqualTo(startTid.toString())
            assertThat(message["sluttTid"].asText()).isEqualTo(sluttTid.toString())
            assertThat(message["endretAv"].asText()).isEqualTo(endretAv)
            assertThat(message["endretAvType"].asText()).isEqualTo(endretAvType.name)
            assertThat(message["endretTidspunkt"].asText()).isEqualTo(endretTidspunkt.toString())
            assertThat(message["aktivitetskortuuid"].isMissingOrNull()).isFalse
            assertThat(message["antallPlasser"].asInt()).isEqualTo(antallPlasser)
            assertThat(message["sted"].asText()).isEqualTo(sted)
        }
    }
    private fun rapidPeriodeMelding(
        fnr: String,
        rekrutteringstreffId: UUID,
        tittel: String,
        beskrivelse: String,
        startTid: ZonedDateTime,
        sluttTid: ZonedDateTime,
        endretAv: String,
        endretAvType: EndretAvType,
        endretTidspunkt: ZonedDateTime,
        antallPlasser: Int,
        sted: String
    ): String = """
        {
            "@event_name": "rekrutteringstreffinvitasjon",
            "aktørId": "123456789",
            "fnr":"$fnr",
            "rekrutteringstreffId":"$rekrutteringstreffId",
            "tittel": "$tittel",
            "beskrivelse": "$beskrivelse",
            "startTid": "$startTid",
            "sluttTid": "$sluttTid",
            "endretAv": "$endretAv",
            "endretAvType": "${endretAvType.name}",
            "endretTidspunkt": "$endretTidspunkt",
            "antallPlasser": $antallPlasser,
            "sted": "$sted"
        }
        """.trimIndent()
}

