package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
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
    private val testRepository = TestRepository(DatabaseConfig(localEnv, meterRegistry))

    @BeforeEach
    fun setup() {
        rapid.reset()
        testRepository.slettAlt()
    }

    @AfterAll
    fun teardown() {
        localPostgres.close()
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
        val endretAvType = "bruker"
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

        TODO()
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
        val endretAvType = "bruker"
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
            assertThat(message["startTid"].asText()).isEqualTo(startTid.toLocalDate().toString())
            assertThat(message["sluttTid"].asText()).isEqualTo(sluttTid.toLocalDate().toString())
            assertThat(message["endretAv"].asText()).isEqualTo(endretAv)
            assertThat(message["endretAvType"].asText()).isEqualTo(endretAvType)
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
        endretAvType: String,
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
            "endretAvType": "$endretAvType",
            "endretTidspunkt": "$endretTidspunkt"
        }
        """.trimIndent()
}

