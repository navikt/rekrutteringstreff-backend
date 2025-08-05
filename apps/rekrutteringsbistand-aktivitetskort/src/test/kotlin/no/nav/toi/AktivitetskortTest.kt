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
import no.nav.toi.aktivitetskort.ErrorType
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
import org.assertj.core.api.Assertions.fail
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortTest {
    private val localEnv = mutableMapOf<String, String>(
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_DATABASE" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_USERNAME" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PASSWORD" to "test"
    )
    private val localPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }
        .also { localConfig ->
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_HOST"] = localConfig.host
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PORT"] = localConfig.getMappedPort(5432).toString()
        }
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val databaseConfig = DatabaseConfig(localEnv, meterRegistry)
    private val testRepository = TestRepository(databaseConfig)
    private val repository = Repository(databaseConfig, "http://url")
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
        val expectedGateAdresse = "Test Sted"
        val expectedPostnummer = "1234"
        val expectedPoststed = "Test Poststed"
        val expectedAktivitetskortId = repository.opprettRekrutteringstreffInvitasjon(
            fnr = expectedFnr,
            rekrutteringstreffId = expectedRekrutteringstreffId,
            tittel = expectedTittel,
            beskrivelse = expectedBeskrivelse,
            startDato = expectedStartDato,
            sluttDato = expectedSluttDato,
            endretAv = expectedEndretAv,
            endretTidspunkt = expectedEndretTidspunkt,
            gateAdresse = expectedGateAdresse,
            postnummer = expectedPostnummer,
            poststed = expectedPoststed
        )

        AktivitetskortJobb(repository, producer, {_,_->}).run()
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
            assertThat(this["aktivitetskort"]["endretTidspunkt"].asText().let(ZonedDateTime::parse))
                .isCloseTo(expectedEndretTidspunkt, within (1, ChronoUnit.SECONDS))
            assertThat(this["aktivitetskort"]["avtaltMedNav"].asBoolean()).isFalse
            assertThat(this["aktivitetskort"]["detaljer"].isArray).isTrue()
            val expectedDetaljer = objectMapper.readTree("""[{"label":"Sted","verdi":"$expectedGateAdresse, $expectedPostnummer $expectedPoststed"}]""")
            assertThat(this["aktivitetskort"]["detaljer"]).containsExactlyInAnyOrder(*expectedDetaljer.toList().toTypedArray())
            assertThat(this["aktivitetskort"]["etiketter"].isArray).isTrue()
            assertThat(this["aktivitetskort"]["etiketter"]).isEmpty()
            assertThat(this["aktivitetskort"]["handlinger"].isArray).isTrue()
            assertThat(this["aktivitetskort"]["handlinger"]).hasSize(1)
            assertThat(this["aktivitetskort"]["handlinger"][0]["tekst"].asText()).isEqualTo("Sjekk ut treffet")
            assertThat(this["aktivitetskort"]["handlinger"][0]["subtekst"].asText()).isEqualTo("Sjekk ut treffet og svar")
            assertThat(this["aktivitetskort"]["handlinger"][0]["url"].asText()).isEqualTo("http://url/rekrutteringstreff/$expectedRekrutteringstreffId")
            assertThat(this["aktivitetskort"]["handlinger"][0]["lenkeType"].asText()).isEqualTo("FELLES")
            assertThat(this["aktivitetskort"]["oppgave"].isNull).isTrue()
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
            endretTidspunkt = ZonedDateTime.now(),
            gateAdresse = "Test Sted",
            postnummer = "1234",
            poststed = "Test Poststed"
        )

        AktivitetskortJobb(repository, producer,{_,_->}).run()
        AktivitetskortJobb(repository, producer,{_,_->}).run()

        assertThat(producer.history()).hasSize(1)
    }

    @Test
    fun `AktivitetsJobb skal ikke feile ved tom database`() {
        val producer = MockProducer(true, null, StringSerializer(), StringSerializer())
        AktivitetskortJobb(repository, producer,{_,_->}).run()
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

        repository.opprettTestRekrutteringstreffInvitasjon()
        val messageId = testRepository.hentAlle()[0].messageId

        val jobb = AktivitetskortFeilJobb(consumer, repository)
        val value = """
            {
              "key": "$messageId",
              "source": "REKRUTTERINGSBISTAND",
              "timestamp": "2019-08-24T14:15:22Z",
              "failingMessage": "string",
              "errorMessage": "DuplikatMeldingFeil Melding allerede handtert, ignorer",
              "errorType": "AKTIVITET_IKKE_FUNNET"
            }
        """.trimIndent()
        consumer.addRecord(ConsumerRecord(topicPartition.topic(), topicPartition.partition(), 0, UUID.randomUUID().toString(), value))
        jobb.run()
        assertThat(listAppender.list).hasSize(3)
        assertThat(listAppender.list[0].message).contains("Kjører AktivitetskortFeilJobb")
        assertThat(listAppender.list[1].message).contains("Feil ved bestilling av aktivitetskort: (se securelog)")
        assertThat(listAppender.list[2].message).contains("Feil ved bestilling av aktivitetskort: $value")
    }

    @Test
    fun `ikke-relaterte feil i feil-kø skal ikke logges`() {
        val logger = LoggerFactory.getLogger(AktivitetskortFeilJobb::class.java.name) as Logger
        val listAppender = ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)

        val consumer = MockConsumer<String, String>(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST)
        val topicPartition = org.apache.kafka.common.TopicPartition("feil-kø", 0)
        consumer.assign(listOf(topicPartition))
        consumer.updateBeginningOffsets(mapOf(topicPartition to 0L))

        repository.opprettTestRekrutteringstreffInvitasjon()
        val messageId = testRepository.hentAlle()[0].messageId

        val jobb = AktivitetskortFeilJobb(consumer, repository)
        val value = """
            {
              "key": "$messageId",
              "source": "ARENA_TILTAK_AKTIVITET_ACL",
              "timestamp": "2019-08-24T14:15:22Z",
              "failingMessage": "string",
              "errorMessage": "DuplikatMeldingFeil Melding allerede handtert, ignorer",
              "errorType": "AKTIVITET_IKKE_FUNNET"
            }
        """.trimIndent()
        consumer.addRecord(ConsumerRecord(topicPartition.topic(), topicPartition.partition(), 0, UUID.randomUUID().toString(), value))
        jobb.run()
        assertThat(listAppender.list).hasSize(1)
        assertThat(listAppender.list[0].message).contains("Kjører AktivitetskortFeilJobb")
    }

    @Test
    fun `feil ved bestilling skal lagres i db`() {
        val consumer = MockConsumer<String, String>(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST)
        val topicPartition = org.apache.kafka.common.TopicPartition("feil-kø", 0)
        consumer.assign(listOf(topicPartition))
        consumer.updateBeginningOffsets(mapOf(topicPartition to 0L))
        repository.opprettTestRekrutteringstreffInvitasjon()
        val messageId = testRepository.hentAlle()[0].messageId
        val timestamp = ZonedDateTime.now()
        val failingMessage = "failingMessageString"
        val errorMessage = "DuplikatMeldingFeil Melding allerede handtert, ignorer"
        val errorType = ErrorType.DUPLIKATMELDINGFEIL

        val jobb = AktivitetskortFeilJobb(consumer, repository)
        val value = """
            {
              "key": "$messageId",
              "source": "REKRUTTERINGSBISTAND",
              "timestamp": "$timestamp",
              "failingMessage": "$failingMessage",
              "errorMessage": "$errorMessage",
              "errorType": "$errorType"
            }
        """.trimIndent()
        consumer.addRecord(ConsumerRecord(topicPartition.topic(), topicPartition.partition(), 0, UUID.randomUUID().toString(), value))
        jobb.run()
        val meldinger = testRepository.hentAlle()
        assertThat(meldinger).hasSize(1)
        assertThat(meldinger[0].feil).isNotNull
        meldinger[0].feil!!.apply {
            assertThat(timestamp).isCloseTo(this.timestamp, within(10, ChronoUnit.MILLIS))
            assertThat(failingMessage).isEqualTo(this.failingMessage)
            assertThat(errorMessage).isEqualTo(this.errorMessage)
            assertThat(errorType).isEqualTo(this.errorType)
        }
    }

    @Test
    fun `Urelaterte feil skal ikke lagres i db`() {
        val consumer = MockConsumer<String, String>(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST)
        val topicPartition = org.apache.kafka.common.TopicPartition("feil-kø", 0)
        consumer.assign(listOf(topicPartition))
        consumer.updateBeginningOffsets(mapOf(topicPartition to 0L))
        repository.opprettTestRekrutteringstreffInvitasjon()
        val messageId = testRepository.hentAlle()[0].messageId

        val jobb = AktivitetskortFeilJobb(consumer, repository)
        val value = """
            {
              "key": "$messageId",
              "source": "ARENA_TILTAK_AKTIVITET_ACL",
              "timestamp": "2019-08-24T14:15:22Z",
              "failingMessage": "string",
              "errorMessage": "DuplikatMeldingFeil Melding allerede handtert, ignorer",
              "errorType": "AKTIVITET_IKKE_FUNNET"
            }
        """.trimIndent()
        consumer.addRecord(ConsumerRecord(topicPartition.topic(), topicPartition.partition(), 0, UUID.randomUUID().toString(), value))
        jobb.run()
        val meldinger = testRepository.hentAlle()
        assertThat(meldinger).hasSize(1)
        assertThat(meldinger[0].feil).isNull()
    }

    @Test
    fun `feilkø-hendelse skal føre til melding på rapid`() {
        repository.opprettTestRekrutteringstreffInvitasjon()
        val invitasjon = testRepository.hentAlle().first()
        val errorMessage = "Duplikat prøvd opprettet"
        val errorType = ErrorType.DUPLIKATMELDINGFEIL
        repository.lagreFeilkøHendelse(invitasjon.messageId, "{}", errorMessage, errorType)

        val rapid = TestRapid()
        AktivitetskortJobb(repository, MockProducer(), rapid::publish).run()
        assertThat(rapid.inspektør.size).isEqualTo(1)
        rapid.inspektør.message(0).apply {
            assertThat(this["@event_name"].asText()).isEqualTo("aktivitetskort-feil")
            assertThat(this["fnr"].asText()).isEqualTo(invitasjon.fnr)
            assertThat(this["aktivitetskortId"].asText()).isEqualTo(invitasjon.aktivitetskortId.toString())
            assertThat(this["messageId"].asText()).isEqualTo(invitasjon.messageId.toString())
            assertThat(this["errorMessage"].asText()).isEqualTo(errorMessage)
            assertThat(this["errorType"].asText()).isEqualTo(errorType.name)
            assertThat(this["timestamp"].asText().let(ZonedDateTime::parse)).isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS))
        }
    }

    @Test
    fun `feilkø-hendelse skal bare sendes 1 gang på rapid`() {
        repository.opprettTestRekrutteringstreffInvitasjon()
        val invitasjon = testRepository.hentAlle().first()
        val errorMessage = "Duplikat prøvd opprettet"
        val errorType = ErrorType.DUPLIKATMELDINGFEIL
        repository.lagreFeilkøHendelse(invitasjon.messageId, "{}", errorMessage, errorType)

        val rapid = TestRapid()
        AktivitetskortJobb(repository, MockProducer(), rapid::publish).apply {
            run()
            run()
        }
        assertThat(rapid.inspektør.size).isEqualTo(1)
    }
}

private fun Repository.opprettTestRekrutteringstreffInvitasjon() {
    opprettRekrutteringstreffInvitasjon(
        "12345678910",
        UUID.randomUUID(),
        "Test Rekrutteringstreff",
        "Dette er en testbeskrivelse for rekrutteringstreff.",
        LocalDate.now().plusDays(1),
        LocalDate.now().plusDays(2),
        "testuser",
        ZonedDateTime.now(),
        "Test Sted",
        "1234",
        "Test Poststed"
    )
}
