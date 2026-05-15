package no.nav.toi.rekrutteringstreff.opprydning

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.toi.DefaultScheduler
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.ScheduledTask
import no.nav.toi.Scheduler
import no.nav.toi.SecureLog
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.ki.KiLoggService
import org.slf4j.Logger
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RekrutteringstreffOpprydningScheduler(
    private val kiLoggService: KiLoggService,
    leaderElection: LeaderElectionInterface,
) : ScheduledTask, Scheduler {

    companion object {
        private const val ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING = 6
    }

    private val scheduler: Scheduler = DefaultScheduler(leaderElection, this, 1, 1, TimeUnit.DAYS)

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
        log.info("Starter opprydning")
        val kiLoggerSomSkalSlettes = kiLoggService.hentKiLoggUuiderForScheduledSletting(ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING)
        if (kiLoggerSomSkalSlettes.isEmpty()) {
            log.info("Ingen KI-logger å slette")
            return
        }
        log.info("Start opprydning/sletting av KI-logger")

        kiLoggService.slettKILogger(kiLoggerSomSkalSlettes)

        log.info("${kiLoggerSomSkalSlettes.size} KI-logger eldre enn ${ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING} måneder er slettet")

        log.info("Opprydning ferdig")
    }
}