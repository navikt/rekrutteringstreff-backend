package no.nav.toi.aktivitetskort

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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun scheduler(
    second: Int,
    nano: Int,
    repository: Repository,
    producer: Producer<String, String>,
    consumer: Consumer<String, String>
) = runBlocking {
    val scheduledExecutor = Executors.newScheduledThreadPool(1)
    val scheduledFeilExecutor = Executors.newScheduledThreadPool(1)
    val myJob = AktivitetskortJobb(repository, producer)
    val myErrorJob = AktivitetskortFeilJobb(consumer)

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

class AktivitetskortJobb(private val repository: Repository, private val producer: Producer<String, String>) {
    fun run() {
        log.info("Kjører AktivitetsJobb")
        repository.hentUsendteHendelser().forEach { usendtHendelse ->
            try {
                usendtHendelse.send(producer)
            } catch (e: Exception) {
                secure(log).error("Feil ved sending av Aktivitetskorthendelse", e)
            }
        }
    }
}

class AktivitetskortFeilJobb(private val consumer: Consumer<String, String>) {
    fun run() {
        log.info("Kjører AktivitetskortFeilJobb")
        consumer.poll(Duration.ofSeconds(1)).forEach { consumerRecord ->
            consumerRecord.value().let {
                log.error("Feil ved bestilling av aktivitetskort: (se securelog)")
                secure(log).error("Feil ved bestilling av aktivitetskort: $it")
            }
        }
    }
}

fun Instant.atOslo(): ZonedDateTime = this.atZone(of("Europe/Oslo")).truncatedTo(MILLIS)