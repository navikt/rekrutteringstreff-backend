package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AktivitetskortSvarScheduler(
) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter AktivitetskortSvarScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleSvar, initialDelay, 60, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper AktivitetskortSvarScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

     fun behandleSvar() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av AktivitetskortSvarScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            TODO()
            /*val usendteInvitasjoner = aktivitetskortInvitasjonRepository.hentUsendteInvitasjoner()

            if (usendteInvitasjoner.isEmpty()) {
                log.info("Ingen usendte invitasjoner å behandle.")
                return
            }

            log.info("Starter behandling av ${usendteInvitasjoner.size} usendte invitasjoner for aktivitetskort")

            usendteInvitasjoner.forEach { usendtInvitasjon ->
                val treff = rekrutteringstreffRepository.hent(TreffId(usendtInvitasjon.rekrutteringstreffUuid)) ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${usendtInvitasjon.rekrutteringstreffUuid}")
                treff.aktivitetskortInvitasjonFor(usendtInvitasjon.fnr)
                    .publiserTilRapids(rapidsConnection)
                aktivitetskortInvitasjonRepository.lagrePollingstatus(usendtInvitasjon.jobbsokerHendelseDbId)
            }
            log.info("Ferdig med behandling av usendte invitasjoner for aktivitetskort")*/
        } catch (e: Exception) {
            log.error("Feil under kjøring av AktivitetskortSvarScheduler", e)
            throw e
        } finally {
            isRunning.set(false)
        }
    }
}