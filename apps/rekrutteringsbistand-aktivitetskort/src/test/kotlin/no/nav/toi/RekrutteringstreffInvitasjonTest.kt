package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.EndretAvType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.ZonedDateTime
import java.util.*
import kotlin.also
import kotlin.apply
import kotlin.collections.set
import kotlin.text.trimIndent
import kotlin.to

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffInvitasjonTest {
    private val localEnv = mutableMapOf<String, String>(
        "DB_DATABASE" to "test",
        "DB_USERNAME" to "test",
        "DB_PASSWORD" to "test"
    )
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val localPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }
        .also { localConfig ->
            localEnv["DB_HOST"] = localConfig.host
            localEnv["DB_PORT"] = localConfig.getMappedPort(5432).toString()
        }

    private val rapid = TestRapid()
    private val databaseConfig = DatabaseConfig(localEnv, meterRegistry)
    private val testRepository = TestRepository(databaseConfig)
    private val app = App(rapid, Repository(databaseConfig))

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
                endretTidspunkt
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
            assertThat(this[0].opprettetTidspunkt).isEqualTo(endretTidspunkt.toString())
        }
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
                endretTidspunkt
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
        endretTidspunkt: ZonedDateTime
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
            "endretTidspunkt": "$endretTidspunkt"
        }
        """.trimIndent()
}

