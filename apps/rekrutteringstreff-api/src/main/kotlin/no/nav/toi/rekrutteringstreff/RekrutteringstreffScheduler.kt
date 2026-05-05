package no.nav.toi.rekrutteringstreff

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.toi.DefaultScheduler
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.ScheduledTask
import no.nav.toi.Scheduler
import no.nav.toi.log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RekrutteringstreffScheduler(
    private val rekrutteringstreffService: RekrutteringstreffService,
    leaderElection: LeaderElectionInterface,
) : ScheduledTask, Scheduler {
    private val scheduler: Scheduler = DefaultScheduler(leaderElection, this, 2, 15, TimeUnit.MINUTES)

    override fun start() {
        scheduler.start()
    }

    override fun stop() {
        scheduler.start()
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
