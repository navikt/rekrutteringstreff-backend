package no.nav.toi.rekrutteringstreff

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RekrutteringstreffScheduler(
    private val rekrutteringstreffService: RekrutteringstreffService,
    private val leaderElection: LeaderElectionInterface,
) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter RekrutteringstreffScheduler")
        scheduler.scheduleAtFixedRate(::fullførJobbtreff, 2, 15, TimeUnit.MINUTES)
    }

    fun stop() {
        log.info("Stopper RekrutteringstreffScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    @WithSpan
    fun fullførJobbtreff() {
        log.info("Starter fullførJobbtreff i RekrutteringstreffScheduler")
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av RekrutteringstreffScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        if (leaderElection.isLeader().not()) {
            log.info("Kjøring av RekrutteringstreffScheduler skippes, instansen er ikke leader.")
            isRunning.set(false)
            return
        }

        try {
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
        } catch (e: Exception) {
            log.error("Feil under kjøring av RekrutteringstreffScheduler", e)
        } finally {
            isRunning.set(false)
        }
    }
}
