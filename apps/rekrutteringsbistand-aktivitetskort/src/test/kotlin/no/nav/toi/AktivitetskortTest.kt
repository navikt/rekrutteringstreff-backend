package no.nav.toi

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetsJobb
import no.nav.toi.aktivitetskort.EndretAvType
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortTest {
    private val localEnv = mutableMapOf<String, String>(
        "DB_DATABASE" to "test",
        "DB_USERNAME" to "test",
        "DB_PASSWORD" to "test"
    )
    private val localPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }
        .also { localConfig ->
            localEnv["DB_HOST"] = localConfig.host
            localEnv["DB_PORT"] = localConfig.getMappedPort(5432).toString()
        }
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val databaseConfig = DatabaseConfig(localEnv, meterRegistry)
    private val testRepository = TestRepository(databaseConfig)
    private val repository = Repository(databaseConfig)
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @BeforeEach
    fun setup() {
        testRepository.slettAlt()
    }

    @AfterAll
    fun teardown() {
        localPostgres.close()
    }

    @Test
    fun `bestill aktivitetskort`() {
        val producer = MockProducer(true, null, StringSerializer(), StringSerializer())
        val expectedFnr = "12345678910"
        val expectedRekrutteringstreffId = UUID.randomUUID()
        val expectedTittel = "Test Rekrutteringstreff"
        val expectedBeskrivelse = "Dette er en testbeskrivelse for rekrutteringstreff."
        val expectedStartDato = LocalDate.now().plusDays(1)
        val expectedSluttDato = LocalDate.now().plusDays(2)
        val expectedEndretAv = "testuser"
        val expectedEndretTidspunkt = ZonedDateTime.now()
        val expectedEndretAvType = EndretAvType.PERSONBRUKER
        val expectedAktivitetskortId = repository.opprettRekrutteringstreffInvitasjon(
            fnr = expectedFnr,
            rekrutteringstreffId = expectedRekrutteringstreffId,
            tittel = expectedTittel,
            beskrivelse = expectedBeskrivelse,
            startDato = expectedStartDato,
            sluttDato = expectedSluttDato,
            endretAv = expectedEndretAv,
            endretAvType = expectedEndretAvType,
            endretTidspunkt = expectedEndretTidspunkt
        )

        AktivitetsJobb(repository, producer).run()
        assertThat(producer.history()).hasSize(1)
        val record = producer.history().first()
        record.value().let (objectMapper::readTree).apply {
            assertThat(this["messageId"].asText()).isNotBlank()
            assertThat(this["source"].asText()).isEqualTo("REKRUTTERINGSBISTAND")
            assertThat(this["aktivitetskortType"].asText()).isEqualTo("REKRUTTERINGSTREFF")
            assertThat(this["actionType"].asText()).isEqualTo("UPSERT_AKTIVITETSKORT_V1")
            assertThat(this["aktivitetskort"]["id"].asText()).isEqualTo(expectedAktivitetskortId.toString())
            assertThat(this["aktivitetskort"]["personIdent"].asText()).isEqualTo(expectedFnr)
            assertThat(this["aktivitetskort"]["tittel"].asText()).isEqualTo(expectedTittel)
            assertThat(this["aktivitetskort"]["aktivitetStatus"].asText()).isEqualTo("FORSLAG")
            assertThat(this["aktivitetskort"]["startDato"].asText()).isEqualTo(expectedStartDato.toString())
            assertThat(this["aktivitetskort"]["sluttDato"].asText()).isEqualTo(expectedSluttDato.toString())
            assertThat(this["aktivitetskort"]["beskrivelse"].asText()).isEqualTo(expectedBeskrivelse)
            assertThat(this["aktivitetskort"]["endretAv"]["ident"].asText()).isEqualTo(expectedEndretAv)
            assertThat(this["aktivitetskort"]["endretAv"]["identType"].asText()).isEqualTo(expectedEndretAvType.name)
            assertThat(this["aktivitetskort"]["endretTidspunkt"].asText().let(ZonedDateTime::parse))
                .isCloseTo(expectedEndretTidspunkt, within (1, ChronoUnit.SECONDS))
            assertThat(this["aktivitetskort"]["avtaltMedNav"].asBoolean()).isFalse
            assertThat(this["aktivitetskort"]["detaljer"].isArray).isTrue()
            assertThat(this["aktivitetskort"]["detaljer"]).isEmpty()
            assertThat(this["aktivitetskort"]["etiketter"].isArray).isTrue()
            assertThat(this["aktivitetskort"]["etiketter"]).isEmpty()
        }
    }

    @Test
    fun `To kjøringer av AktivitetsJobb skal ikke gi duplikater`() {
        val producer = MockProducer(true, null, StringSerializer(), StringSerializer())

        repository.opprettRekrutteringstreffInvitasjon(
            fnr = "12345678910",
            rekrutteringstreffId = UUID.randomUUID(),
            tittel = "Test Rekrutteringstreff",
            beskrivelse = "Dette er en testbeskrivelse for rekrutteringstreff.",
            startDato = LocalDate.now().plusDays(1),
            sluttDato = LocalDate.now().plusDays(2),
            endretAv = "testuser",
            endretAvType = EndretAvType.NAVIDENT,
            endretTidspunkt = ZonedDateTime.now()
        )

        AktivitetsJobb(repository, producer).run()
        AktivitetsJobb(repository, producer).run()

        assertThat(producer.history()).hasSize(1)
    }

    @Test
    fun `AktivitetsJobb skal ikke feile ved tom database`() {
        val producer = MockProducer(true, null, StringSerializer(), StringSerializer())
        AktivitetsJobb(repository, producer).run()
        assertThat(producer.history()).isEmpty()
    }
}