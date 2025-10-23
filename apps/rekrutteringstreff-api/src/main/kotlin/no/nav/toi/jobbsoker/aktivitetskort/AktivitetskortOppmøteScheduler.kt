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

class AktivitetskortOppmøteScheduler(
    private val aktivitetskortRepository: AktivitetskortRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val rapidsConnection: RapidsConnection
) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter AktivitetskortOppmøteScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleOppmøte, initialDelay, 60, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper AktivitetskortOppmøteScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

     fun behandleOppmøte() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av AktivitetskortOppmøteScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {

            val usendteOppmøte = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.MØTT_OPP)
            val usendteIkkeOppmøte = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.IKKE_MØTT_OPP)


            if (usendteOppmøte.isEmpty() && usendteIkkeOppmøte.isEmpty()) {
                log.info("Ingen usendte oppmøte å behandle.")
                return
            }

            log.info("Starter behandling av ${usendteOppmøte.size + usendteIkkeOppmøte.size} usendte oppmøte/ikke oppmøte for aktivitetskort")

            usendteOppmøte.forEach { usendtOppmøte ->
                val treff = rekrutteringstreffRepository.hent(TreffId(usendtOppmøte.rekrutteringstreffUuid)) ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${usendtOppmøte.rekrutteringstreffUuid}")
                treff.aktivitetskortOppmøteFor(fnr = usendtOppmøte.fnr, møttOpp = true)
                    .publiserTilRapids(rapidsConnection)
                aktivitetskortRepository.lagrePollingstatus(usendtOppmøte.jobbsokerHendelseDbId)
            }
            log.info("Ferdig med behandling av usendte oppmøte for aktivitetskort")

            usendteIkkeOppmøte.forEach { usendtOppmøte ->
                val treff = rekrutteringstreffRepository.hent(TreffId(usendtOppmøte.rekrutteringstreffUuid)) ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${usendtOppmøte.rekrutteringstreffUuid}")
                treff.aktivitetskortOppmøteFor(fnr = usendtOppmøte.fnr, møttOpp = false)
                    .publiserTilRapids(rapidsConnection)
                aktivitetskortRepository.lagrePollingstatus(usendtOppmøte.jobbsokerHendelseDbId)
            }
            log.info("Ferdig med behandling av usendte ikke oppmøte for aktivitetskort")

        } catch (e: Exception) {
            log.error("Feil under kjøring av AktivitetskortOppmøteScheduler", e)
            throw e
        } finally {
            isRunning.set(false)
        }
    }


}