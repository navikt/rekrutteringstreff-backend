package no.nav.toi.rekrutteringstreff

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.toi.*
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
            publiserteTreffHvorTilTidErPassert.forEach { treff ->
                rekrutteringstreffService.fullfør(treff.id, "SYSTEM")
            }
            log.info("RekrutteringstreffScheduler fullførte ${publiserteTreffHvorTilTidErPassert.size} treff")
        } else {
            log.info("RekrutteringstreffScheduler fant ingen treff å fullføre")
        }
    }
}
