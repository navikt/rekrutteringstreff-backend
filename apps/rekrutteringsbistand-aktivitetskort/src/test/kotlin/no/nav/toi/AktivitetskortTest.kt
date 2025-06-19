package no.nav.toi

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetskortFeilJobb
import no.nav.toi.aktivitetskort.AktivitetskortJobb
import no.nav.toi.aktivitetskort.EndretAvType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
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
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.temporal.ChronoUnit

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

        AktivitetskortJobb(repository, producer).run()
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

        AktivitetskortJobb(repository, producer).run()
        AktivitetskortJobb(repository, producer).run()

        assertThat(producer.history()).hasSize(1)
    }

    @Test
    fun `AktivitetsJobb skal ikke feile ved tom database`() {
        val producer = MockProducer(true, null, StringSerializer(), StringSerializer())
        AktivitetskortJobb(repository, producer).run()
        assertThat(producer.history()).isEmpty()
    }

    @Test
    fun `feil ved bestilling av aktivitetskort skal logges`() {
        val logger = LoggerFactory.getLogger(AktivitetskortFeilJobb::class.java.name) as Logger
        val listAppender = ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)

        val consumer = MockConsumer<String, String>(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST)
        val topicPartition = org.apache.kafka.common.TopicPartition("feil-kø", 0)
        consumer.assign(listOf(topicPartition))
        consumer.updateBeginningOffsets(mapOf(topicPartition to 0L))

        val jobb = AktivitetskortFeilJobb(consumer)
        val value = """
            {
                "feil": "skriv feil her"
            }
        """.trimIndent()
        consumer.addRecord(ConsumerRecord(topicPartition.topic(), topicPartition.partition(), 0, UUID.randomUUID().toString(), value))
        jobb.run()
        assertThat(listAppender.list).hasSize(3)
        assertThat(listAppender.list[0].message).contains("Kjører AktivitetskortFeilJobb")
        assertThat(listAppender.list[1].message).contains("Feil ved bestilling av aktivitetskort: (se securelog)")
        assertThat(listAppender.list[2].message).contains("Feil ved bestilling av aktivitetskort: $value")
    }
}