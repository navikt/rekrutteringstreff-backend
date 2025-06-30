package no.nav.toi.aktivitetskort

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.toi.Repository
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.log
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun scheduler(
    second: Int,
    nano: Int,
    repository: Repository,
    producer: Producer<String, String>,
    consumer: Consumer<String, String>,
    rapidsConnection: RapidsConnection
) = runBlocking {
    val scheduledExecutor = Executors.newScheduledThreadPool(1)
    val scheduledFeilExecutor = Executors.newScheduledThreadPool(1)
    val myJob = AktivitetskortJobb(repository, producer) { key, message ->
        rapidsConnection.publish(key, message)
    }
    val myErrorJob = AktivitetskortFeilJobb(consumer, repository)

    val now = ZonedDateTime.now().toInstant().atOslo()
    val nextRun = now.withSecond(second).withNano(nano)
        .let { if (it <= now) it.plusMinutes(1) else it }
    val delay = MILLIS.between(now, nextRun)

    val task = Runnable {
        runBlocking {
            launch {
                myJob.run()

            }
        }
    }
    val feilTask = Runnable {
        runBlocking {
            launch {
                myErrorJob.run()
            }
        }
    }

    scheduledExecutor.scheduleAtFixedRate(task, delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS)
    scheduledFeilExecutor.scheduleAtFixedRate(feilTask, delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS)
}

class AktivitetskortJobb(private val repository: Repository, private val producer: Producer<String, String>, private val rapidPublish: (String, String) -> Unit) {
    fun run() {
        log.info("Kjører AktivitetsJobb")
        repository.hentUsendteAktivitetskortHendelser().forEach { usendtHendelse ->
            try {
                usendtHendelse.send(producer)
            } catch (e: Exception) {
                secure(log).error("Feil ved sending av Aktivitetskorthendelse", e)
            }
        }
        repository.hentUsendteFeilkøHendelser().forEach { usendtFeil ->
            usendtFeil.sendTilRapid(rapidPublish)
        }
    }
}

class AktivitetskortFeilJobb(private val consumer: Consumer<String, String>, private val repository: Repository) {
    fun run() {
        log.info("Kjører AktivitetskortFeilJobb")
        consumer.poll(Duration.ofSeconds(1)).forEach { consumerRecord ->
            consumerRecord.value().let {
                class FeilKøHendelse(
                    val key: UUID,
                    val source: String,
                    val failingMessage: String,
                    val errorMessage: String,
                    val errorType: ErrorType
                )

                val objectMapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                val hendelse = objectMapper.readValue(it, FeilKøHendelse::class.java)
                if(hendelse.source == "REKRUTTERINGSBISTAND") {
                    log.error("Feil ved bestilling av aktivitetskort: (se securelog)")
                    secure(log).error("Feil ved bestilling av aktivitetskort: $it")
                    repository.lagreFeilkøHendelse(
                        messageId = hendelse.key,
                        failingMessage = hendelse.failingMessage,
                        errorMessage = hendelse.errorMessage,
                        errorType = hendelse.errorType
                    )
                }
            }
        }
    }
}

fun Instant.atOslo(): ZonedDateTime = this.atZone(of("Europe/Oslo")).truncatedTo(MILLIS)