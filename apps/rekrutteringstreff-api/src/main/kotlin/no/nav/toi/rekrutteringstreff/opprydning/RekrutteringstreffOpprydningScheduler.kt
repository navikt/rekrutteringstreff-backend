package no.nav.toi.rekrutteringstreff.opprydning

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.ki.KiLoggService
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RekrutteringstreffOpprydningScheduler(
    private val kiLoggService: KiLoggService,
    private val leaderElection: LeaderElectionInterface,
) {
    companion object {
        private const val ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING = 6
    }

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter RekrutteringstreffOpprydningScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds() //Vurdere om vi skal sette et bestemt tidspunkt senere, foreløpig avhenger det av når appen blir startet

        scheduler.scheduleAtFixedRate(::rekrutteringstreffOpprydning, initialDelay, 1, TimeUnit.DAYS)
    }

    fun stop() {
        log.info("Stopper RekrutteringstreffOpprydningScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    @WithSpan
    fun rekrutteringstreffOpprydning() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av RekrutteringstreffOpprydningScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            if (leaderElection.isLeader().not()) {
                log.info("Kjøring av RekrutteringstreffOpprydningScheduler skippes, instansen er ikke leader.")
                isRunning.set(false)
                return
            }

            log.info("Starter opprydning")
            slettKiLogger()

            log.info("Opprydning ferdig")
        } catch (e: Exception) {
            log.error("Feil under kjøring av RekrutteringstreffOpprydningScheduler", e)
        } finally {
            isRunning.set(false)
        }
    }

    private fun slettKiLogger() {
        val kiLoggerSomSkalSlettes = kiLoggService.hentKiLoggUuiderForScheduledSletting(ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING)
        if (kiLoggerSomSkalSlettes.isEmpty()) {
            log.info("Ingen KI-logger å slette")
            return
        }
        log.info("Start opprydning/sletting av KI-logger")

        kiLoggService.slettKILogger(kiLoggerSomSkalSlettes)

        log.info("Alle KI-logger eldre enn ${ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING} måneder er slettet")
    }
}