package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.log
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AktivitetskortScheduler(private val aktivitetskortRepository: AktivitetskortRepository) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter AktivitetskortScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleInvitasjoner, initialDelay, 60, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper AktivitetskortScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    private fun behandleInvitasjoner() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av AktivitetskortScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            val usendteInvitasjoner = aktivitetskortRepository.hentUsendteInvitasjoner()

            if (usendteInvitasjoner.isEmpty()) {
                log.info("Ingen usendte invitasjoner å behandle.")
                return
            }

            log.info("Starter behandling av ${usendteInvitasjoner.size} usendte invitasjoner for aktivitetskort")

            usendteInvitasjoner.forEach { usendtInvitasjon ->
                log.info("Fant usendt invitasjon med hendelse-ID ${usendtInvitasjon.jobbsokerHendelseDbId}. Ville ha publisert til rapid.")
                // legg på en rapid-melding
                aktivitetskortRepository.lagrePollingstatus(usendtInvitasjon.jobbsokerHendelseDbId)
            }
            log.info("Ferdig med behandling av usendte invitasjoner for aktivitetskort")
        } catch (e: Exception) {
            log.error("Feil under kjøring av AktivitetskortScheduler", e)
        } finally {
            isRunning.set(false)
        }
    }
}