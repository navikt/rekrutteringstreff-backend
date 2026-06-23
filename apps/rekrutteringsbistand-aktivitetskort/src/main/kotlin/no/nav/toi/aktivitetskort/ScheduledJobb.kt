package no.nav.toi.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.Repository
import no.nav.toi.SecureLog
import no.nav.toi.log
import no.nav.toi.objectMapper
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.TopicPartition
import org.intellij.lang.annotations.Language
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class SchedulerContext(
    val scheduledExecutor: ScheduledExecutorService,
    val scheduledFeilExecutor: ScheduledExecutorService,
) {
    fun stop() {
        scheduledExecutor.shutdown()
        scheduledFeilExecutor.shutdown()
        if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) scheduledExecutor.shutdownNow()
        if (!scheduledFeilExecutor.awaitTermination(5, TimeUnit.SECONDS)) scheduledFeilExecutor.shutdownNow()
    }
}

fun scheduler(
    second: Int,
    nano: Int,
    repository: Repository,
    producer: Producer<String, String>,
    consumer: Consumer<String, String>,
    rapidsConnection: RapidsConnection,
    dabAktivitetskortFeilTopic: String,
    leaderElection: LeaderElectionInterface,
): SchedulerContext = runBlocking {
    val scheduledExecutor = Executors.newScheduledThreadPool(1)
    val scheduledFeilExecutor = Executors.newScheduledThreadPool(1)
    val myJob = AktivitetskortJobb(repository, producer, leaderElection)
    val myErrorJob = AktivitetskortFeilJobb(repository, consumer, leaderElection, dabAktivitetskortFeilTopic) { key, message ->
        rapidsConnection.publish(key, message)
    }

    val now = ZonedDateTime.now().toInstant().atOslo()
    val nextRun = now.withSecond(second).withNano(nano)
        .let { if (it <= now) it.plusSeconds(10) else it }
    val delay = MILLIS.between(now, nextRun)

    scheduledExecutor.scheduleAtFixedRate(myJob, delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS)
    scheduledFeilExecutor.scheduleAtFixedRate(myErrorJob, delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS)
    SchedulerContext(scheduledExecutor, scheduledFeilExecutor)
}

class AktivitetskortJobb(private val repository: Repository, private val producer: Producer<String, String>, private val leaderElection: LeaderElectionInterface): Runnable {
   private val secureLog = SecureLog(log)

    @WithSpan
    override fun run() {
        try {
            if (!leaderElection.isLeader()) {
                log.info("Kjøring av AktivitetskortJobb skippes, instansen er ikke leader.")
                return
            }
            log.info("Kjører AktivitetsJobb")
            repository.hentUsendteAktivitetskortHendelser().forEach { usendtHendelse ->
                try {
                    usendtHendelse.send(producer)
                } catch (e: Exception) {
                    secureLog.error("Feil ved sending av Aktivitetskorthendelse", e)
                }
            }
        } catch (t: Throwable) {
            log.error("Uventet feil i AktivitetskortJobb – schedulern fortsetter ved neste kjøring. (se securelog)")
            secureLog.error("Uventet feil i AktivitetskortJobb – schedulern fortsetter ved neste kjøring.", t)
        }
    }
}

class AktivitetskortFeilJobb(
    private val repository: Repository,
    private val consumer: Consumer<String, String>,
    private val leaderElection: LeaderElectionInterface,
    private val dabAktivitetskortFeilTopic: String,
    private val rapidPublish: (String, String) -> Unit
): Runnable {
    private val secureLog = SecureLog(log)

    @WithSpan
    override fun run() {
        try {
            if(!leaderElection.isLeader()) {
                if (consumer.subscription().isNotEmpty()) {
                    log.info("Instansen er ikke leader lenger – melder consumer ut av gruppa.")
                    consumer.unsubscribe()
                }
                log.info("Kjøring av AktivitetskortFeilJobb skippes, instansen er ikke leader.")
                return
            }

            if (consumer.subscription().isEmpty() && consumer.assignment().isEmpty()) {
                log.info("Instansen er leader – starter consumering på $dabAktivitetskortFeilTopic.")
                consumer.subscribe(listOf(dabAktivitetskortFeilTopic))
            }
            log.info("Kjører AktivitetskortFeilJobb")
            lagreFeilKøHendelser()
            sendFeilKøHendelserPåRapid()
        } catch (t: Throwable) {
            log.error("Uventet feil i AktivitetskortFeilJobb – schedulern fortsetter ved neste kjøring. (se securelog)")
            secureLog.error("Uventet feil i AktivitetskortFeilJobb – schedulern fortsetter ved neste kjøring.", t)
        }
    }


    fun lagreFeilKøHendelser() {
        var currentPositions = mutableMapOf<TopicPartition, Long>()
        var records: ConsumerRecords<String?, String?>?

        try {
            records = consumer.poll(Duration.ofSeconds(10))
            log.info("Mottok ${records.count()} meldinger fra $dabAktivitetskortFeilTopic")
            currentPositions = records.groupBy { TopicPartition(it.topic(), it.partition()) }
                .mapValues { it.value.minOf { it.offset() } }
                .toMutableMap()

            records.forEach { consumerRecord ->
                consumerRecord.value().let {
                    val hendelse = objectMapper.readValue(it, FeilKøHendelse::class.java)
                    if (hendelse.source == "REKRUTTERINGSBISTAND") {
                        log.error("Feil ved bestilling av aktivitetskort: (se securelog)")
                        secureLog.error("Feil ved bestilling av aktivitetskort: $it")
                        log.info("Skal lagre feil ved bestilling av aktivitetskort i databasen")

                        val failingMessageUtenEscaping = hendelse.failingMessage.replace("\\n", "").replace("\\\"", "\"")

                        repository.lagreFeilkøHendelse(
                            messageId = failingMessageUtenEscaping.hentMessageId(),
                            failingMessage = hendelse.failingMessage,
                            errorMessage = hendelse.errorMessage,
                            errorType = hendelse.errorType
                        )
                        log.info("Lagret feil med bestilling av aktivitetskort")
                    } else log.info("Hendelse med source ${hendelse.source} ignoreres.")
                }
                currentPositions[TopicPartition(consumerRecord.topic(), consumerRecord.partition())] = consumerRecord.offset() + 1
            }
        } catch (e: Exception) {
            log.error("Feil ved kjøring av AktivitetskortFeilJobb: (se securelog)")
            secureLog.error("Feil ved kjøring av AktivitetskortFeilJobb", e)
        } finally {
            consumer.commitSync(currentPositions.mapValues { (_, offset) -> offsetMetadata(offset) })
            currentPositions.clear()
        }
    }
    fun sendFeilKøHendelserPåRapid() {
        log.info("Skal sende usendte feilKøHendelser på rapid")
        repository.hentUsendteFeilkøHendelser().forEach { usendtFeil ->
            usendtFeil.sendTilRapid(rapidPublish)
        }
    }

    private fun offsetMetadata(offset: Long): OffsetAndMetadata {
        val clientId = consumer.groupMetadata().groupInstanceId().map { "\"$it\"" }.orElse("null")
        @Language("JSON")
        val metadata = """{"time": "${LocalDateTime.now()}","groupInstanceId": $clientId}"""
        return OffsetAndMetadata(offset, metadata)
    }
}

data class FeilKøHendelse(
    val source: String,
    val failingMessage: String,
    val errorMessage: String,
    val errorType: ErrorType
)

private fun String.hentMessageId() = objectMapper.readTree(this)["messageId"]?.asText()?.let {
    UUID.fromString(it)
} ?: error("Kunne ikke hente messageId fra hendelse: $this")

fun Instant.atOslo(): ZonedDateTime = this.atZone(of("Europe/Oslo")).truncatedTo(MILLIS)
