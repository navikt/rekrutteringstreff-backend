package no.nav.toi.aktivitetskort

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.toi.Repository
import no.nav.toi.log
import org.apache.kafka.clients.producer.Producer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun scheduler(
    hour: Int,
    minute: Int,
    second: Int,
    nano: Int,
    repository: Repository,
    producer: Producer<String, String>,
) = runBlocking {
    val scheduledExecutor = Executors.newScheduledThreadPool(1)
    val myJob = AktivitetsJobb(repository, producer)

    val now = ZonedDateTime.now().toInstant().atOslo()
    val nextRun = now.withHour(hour).withMinute(minute).withSecond(second).withNano(nano)
        .let { if (it <= now) it.plusDays(1) else it }
    val delay = MILLIS.between(now, nextRun)

    val task = Runnable {
        runBlocking {
            launch {
                myJob.run()
            }
        }
    }

    scheduledExecutor.scheduleAtFixedRate(task, delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS)
}

class AktivitetsJobb(private val repository: Repository, private val producer: Producer<String, String>) {
    fun run() {
        log.info("Kjører AktivitetsJobb")
        repository.hentUsendteHendelser().forEach { usendtHendelse ->
            try {
                usendtHendelse.send(producer)
            } catch (e: Exception) {
                secureLog.error("Feil ved sending av Aktivitetskorthendelse", e)
            }
        }
    }

    /*fun sendMelding(it: FritattOgStatus) {
        val fnr = it.fritatt.fnr
        val melding = it.fritatt.tilJsonMelding(it.gjeldendestatus().erFritatt())
        secureLog.info("Sender melding for fnr $fnr ${it.status} $melding ")
        rapidsConnection.publish(it.fritatt.fnr, melding)
        val blelagret = repository.markerSomSendt(it.fritatt, it.gjeldendestatus())
        if (!blelagret) {
            secureLog.error("Konflikt ved lagring for fnr $fnr: ${blelagret} ${it.fritatt} ${it.gjeldendestatus()}")
            log.info("Konflikt ved lagring, se securelog")
        }
        secureLog.info("Resultat markerSomSendt for $fnr: ${blelagret} ${it.fritatt} ${it.gjeldendestatus()}")
    }*/

}

private val secureLog = LoggerFactory.getLogger("secureLog")

fun Instant.atOslo(): ZonedDateTime = this.atZone(of("Europe/Oslo")).truncatedTo(MILLIS)