package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AktivitetskortSvartJaTreffstatusScheduler(
    private val aktivitetskortRepository: AktivitetskortRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val rapidsConnection: RapidsConnection
) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter AktivitetskortSvartJaTreffstatusScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleStatusendringer, initialDelay, 60, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper AktivitetskortSvartJaTreffstatusScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    fun behandleStatusendringer() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av AktivitetskortSvartJaTreffstatusScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            val usendteAvlyst = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
            val usendteFullført = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)

            if (usendteAvlyst.isEmpty() && usendteFullført.isEmpty()) {
                log.info("Ingen usendte treffstatus-endringer å behandle.")
                return
            }

            log.info("Starter behandling av ${usendteAvlyst.size + usendteFullført.size} usendte treffstatus-endringer for aktivitetskort")

            usendteAvlyst.forEach { usendt ->
                val treff = rekrutteringstreffRepository.hent(TreffId(usendt.rekrutteringstreffUuid))
                    ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${usendt.rekrutteringstreffUuid}")
                treff.aktivitetskortSvartJaTreffstatusEndretFor(fnr = usendt.fnr, status = "avlyst")
                    .publiserTilRapids(rapidsConnection)
                aktivitetskortRepository.lagrePollingstatus(usendt.jobbsokerHendelseDbId)
            }
            log.info("Ferdig med behandling av usendte 'avlyst'-status for aktivitetskort")

            usendteFullført.forEach { usendt ->
                val treff = rekrutteringstreffRepository.hent(TreffId(usendt.rekrutteringstreffUuid))
                    ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${usendt.rekrutteringstreffUuid}")
                treff.aktivitetskortSvartJaTreffstatusEndretFor(fnr = usendt.fnr, status = "fullført")
                    .publiserTilRapids(rapidsConnection)
                aktivitetskortRepository.lagrePollingstatus(usendt.jobbsokerHendelseDbId)
            }
            log.info("Ferdig med behandling av usendte 'fullført'-status for aktivitetskort")

        } catch (e: Exception) {
            log.error("Feil under kjøring av AktivitetskortSvartJaTreffstatusScheduler", e)
            throw e
        } finally {
            isRunning.set(false)
        }
    }
}
