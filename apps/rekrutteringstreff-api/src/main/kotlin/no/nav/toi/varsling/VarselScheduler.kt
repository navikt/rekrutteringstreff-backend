package no.nav.toi.varsling

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.log
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

class VarselScheduler(
    dataSource: DataSource,
    private val rapidsConnection: RapidsConnection
) {
    private val varselRepository = VarselRepository(dataSource)
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter VarselScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleVarselHendelser, initialDelay, 10, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper VarselScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    fun behandleVarselHendelser() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av VarselScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            val usendteHendelser = varselRepository.hentUsendteVarselHendelser()

            if (usendteHendelser.isEmpty()) {
                log.info("Ingen usendte varsel-hendelser å behandle.")
                return
            }

            log.info("Starter behandling av ${usendteHendelser.size} usendte varsel-hendelser")

            usendteHendelser.forEach { hendelse ->
                try {
                    behandleHendelse(hendelse)
                } catch (e: Exception) {
                    log.error("Feil under behandling av varsel-hendelse ${hendelse.jobbsokerHendelseDbId} av type ${hendelse.hendelsestype}", e)
                    throw e
                }
            }

            log.info("Ferdig med behandling av usendte varsel-hendelser")
        } catch (e: Exception) {
            log.error("Feil under kjøring av VarselScheduler", e)
            throw e
        } finally {
            isRunning.set(false)
        }
    }

    private fun behandleHendelse(hendelse: UsendtVarselHendelse) {
        when (hendelse.hendelsestype) {
            JobbsøkerHendelsestype.INVITERT -> sendKandidatInvitertHendelse(hendelse)
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON -> sendInvitertKandidatTreffEndretHendelse(hendelse)
            else -> {
                log.warn("Ukjent hendelsestype for varsling: ${hendelse.hendelsestype}")
            }
        }
    }

    private fun sendKandidatInvitertHendelse(hendelse: UsendtVarselHendelse) {
        val varselId = UUID.randomUUID().toString()
        
        val message = JsonMessage.newMessage(
            eventName = "kandidat.invitert",
            map = mapOf(
                "varselId" to varselId,
                "fnr" to hendelse.fnr,
                "avsenderNavident" to (hendelse.aktøridentifikasjon ?: "UKJENT")
            )
        )

        rapidsConnection.publish(hendelse.fnr, message.toJson())
        varselRepository.lagreSendtStatus(hendelse.jobbsokerHendelseDbId)
        
        log.info("Sendt kandidat.invitert-hendelse for varselId=$varselId")
    }

    private fun sendInvitertKandidatTreffEndretHendelse(hendelse: UsendtVarselHendelse) {
        val varselId = UUID.randomUUID().toString()
        
        val message = JsonMessage.newMessage(
            eventName = "invitert.kandidat.endret",
            map = mapOf(
                "varselId" to varselId,
                "fnr" to hendelse.fnr,
                "avsenderNavident" to (hendelse.aktøridentifikasjon ?: "UKJENT")
            )
        )

        rapidsConnection.publish(hendelse.fnr, message.toJson())
        varselRepository.lagreSendtStatus(hendelse.jobbsokerHendelseDbId)
        
        log.info("Sendt invitert.kandidat.endret-hendelse for varselId=$varselId")
    }
}
