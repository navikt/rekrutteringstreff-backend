package no.nav.toi.aktivitetskort

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
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
    val scheduledOppdateringExecutor = Executors.newScheduledThreadPool(1)
    val myJob = AktivitetskortJobb(repository, producer)
    consumer.subscribe(listOf("dab.aktivitetskort-feil-v1"))
    val myErrorJob = AktivitetskortFeilJobb(repository, consumer) { key, message ->
        rapidsConnection.publish(key, message)
    }
    val myOppdateringJob = AktivitetskortOppdateringJobb(repository) { key, message ->
        rapidsConnection.publish(key, message)
    }

    val now = ZonedDateTime.now().toInstant().atOslo()
    val nextRun = now.withSecond(second).withNano(nano)
        .let { if (it <= now) it.plusSeconds(10) else it }
    val delay = MILLIS.between(now, nextRun)

    scheduledExecutor.scheduleAtFixedRate(myJob, delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS)
    scheduledFeilExecutor.scheduleAtFixedRate(myErrorJob, delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS)
    scheduledOppdateringExecutor.scheduleAtFixedRate(myOppdateringJob, delay, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS)
}

class AktivitetskortJobb(private val repository: Repository, private val producer: Producer<String, String>): Runnable {
    override fun run() {
        log.info("Kjører AktivitetsJobb")
        repository.hentUsendteAktivitetskortHendelser().forEach { usendtHendelse ->
            try {
                usendtHendelse.send(producer)
            } catch (e: Exception) {
                secure(log).error("Feil ved sending av Aktivitetskorthendelse", e)
            }
        }
    }
}

class AktivitetskortFeilJobb(
    private val repository: Repository,
    private val consumer: Consumer<String, String>,
    private val rapidPublish: (String, String) -> Unit
): Runnable {
    override fun run() {
        log.info("Kjører AktivitetskortFeilJobb")
        lagreFeilKøHendelser()
        sendFeilKøHendelserPåRapid()
    }
    fun lagreFeilKøHendelser() {
        val records = consumer.poll(Duration.ofSeconds(10))
        records.forEach { consumerRecord ->
            consumerRecord.value().let {
                class FeilKøHendelse(
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
                        messageId = hendelse.failingMessage.hentMessageId(),
                        failingMessage = hendelse.failingMessage,
                        errorMessage = hendelse.errorMessage,
                        errorType = hendelse.errorType
                    )
                } else log.info("Hendelse med source ${hendelse.source} ignoreres.")
            }
        }
    }
    fun sendFeilKøHendelserPåRapid() {
        repository.hentUsendteFeilkøHendelser().forEach { usendtFeil ->
            usendtFeil.sendTilRapid(rapidPublish)
        }
    }
}

class AktivitetskortOppdateringJobb(
    private val repository: Repository,
    private val rapidPublish: (String, String) -> Unit
): Runnable {
    override fun run() {
        log.info("Kjører AktivitetskortOppdateringJobb")
        repository.hentSendteIkkePubliserteOppdateringer().forEach { oppdatering ->
            try {
                oppdatering.sendTilRapid(rapidPublish)
            } catch (e: Exception) {
                secure(log).error("Feil ved sending av AktivitetskortOppdatering til rapid", e)
            }
        }
    }
}

private val jacksonObjectMapper = jacksonObjectMapper()

private fun String.hentMessageId() = jacksonObjectMapper.readTree(this)["messageId"]?.asText()?.let {
    UUID.fromString(it)
} ?: error("Kunne ikke hente messageId fra hendelse: $this")

fun Instant.atOslo(): ZonedDateTime = this.atZone(of("Europe/Oslo")).truncatedTo(MILLIS)