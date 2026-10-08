package no.nav.toi.rekrutteringstreff

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.arbeidsgiver.toi.logging.log
import no.nav.toi.*
import no.nav.toi.exception.UlovligOppdateringException
import java.util.concurrent.TimeUnit

class RekrutteringstreffScheduler(
    private val rekrutteringstreffService: RekrutteringstreffService,
    leaderElection: LeaderElectionInterface,
) : ScheduledTask, Scheduler {
    private val scheduler: Scheduler = DefaultScheduler(leaderElection, this, 2, 15, TimeUnit.MINUTES)

    override fun start() {
        scheduler.start()
    }

    override fun stop() {
        scheduler.stop()
    }

    override fun wrapJobbkjøring() {
        scheduler.wrapJobbkjøring()
    }

    @WithSpan
    override fun kjørJobb() {
        val publiserteTreffHvorTilTidErPassert = rekrutteringstreffService.hentPubliserteTreffHvorTilTidErPassert()
        if (publiserteTreffHvorTilTidErPassert.isNotEmpty()) {
            log.info("RekrutteringstreffScheduler fullfører ${publiserteTreffHvorTilTidErPassert.size} rekrutteringstreff")
            val antallFullført = publiserteTreffHvorTilTidErPassert.count { treff -> fullfør(treff.id) }
            log.info("RekrutteringstreffScheduler fullførte $antallFullført av ${publiserteTreffHvorTilTidErPassert.size} treff")
        } else {
            log.info("RekrutteringstreffScheduler fant ingen treff å fullføre")
        }
    }

    /** Ett treff som feiler, skal ikke stoppe de andre. */
    private fun fullfør(treffId: TreffId): Boolean =
        try {
            rekrutteringstreffService.fullfør(treffId, "SYSTEM")
            true
        } catch (e: UlovligOppdateringException) {
            log.info("RekrutteringstreffScheduler fullførte ikke treff ${treffId.somString}: ${e.message}")
            false
        } catch (e: Exception) {
            log.error("RekrutteringstreffScheduler klarte ikke å fullføre treff ${treffId.somString}", e)
            false
        }
}
