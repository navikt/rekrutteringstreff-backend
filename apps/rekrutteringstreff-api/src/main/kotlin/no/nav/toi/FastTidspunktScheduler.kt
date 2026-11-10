package no.nav.toi

import no.nav.arbeidsgiver.toi.logging.log
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

sealed interface SchedulerGjentakelse {
    data object Daglig : SchedulerGjentakelse

    data class Ukentlig(
        val ukedag: DayOfWeek,
    ) : SchedulerGjentakelse

    data class HverNDag(
        val antallDager: Int,
        val utgangspunktDato: LocalDate,
    ) : SchedulerGjentakelse {
        init {
            require(antallDager > 0) { "antallDager må være positivt" }
        }
    }
}

class FastTidspunktScheduler(
    private val leaderElection: LeaderElectionInterface,
    private val scheduledTask: ScheduledTask,
    private val kjøretidspunkt: LocalTime,
    private val tidssone: ZoneId,
    private val gjentakelse: SchedulerGjentakelse,
) : Scheduler {

    private val executor = ScheduledThreadPoolExecutor(1).apply {
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
    }
    private val erKjørende = AtomicBoolean(false)
    private var harStartet = false

    override fun start() {
        synchronized(executor) {
            check(!harStartet) { "Scheduleren er allerede startet" }
            check(!executor.isShutdown) { "Scheduleren er stoppet" }

            log.info("Starter ${scheduledTask::class.simpleName}")
            planleggNesteKjøring()
            harStartet = true
        }
    }

    override fun stop() {
        log.info("Stopper ${scheduledTask::class.simpleName}")

        synchronized(executor) {
            executor.shutdown()
        }

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (avbrudd: InterruptedException) {
            log.warn(
                "Avbrutt under stopping av ${scheduledTask::class.simpleName}",
                avbrudd,
            )
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    override fun wrapJobbkjøring() {
        log.info("Vurderer oppstart av jobb ${scheduledTask::class.simpleName}")

        if (erKjørende.getAndSet(true)) {
            log.info(
                "Forrige kjøring av jobb ${scheduledTask::class.simpleName} " +
                        "er ikke ferdig, skipper denne kjøringen.",
            )
            return
        }

        try {
            if (!leaderElection.isLeader()) {
                log.info(
                    "Kjøring av jobb ${scheduledTask::class.simpleName} " +
                            "skippes, instansen er ikke leader.",
                )
                return
            }

            log.info("Starter jobb ${scheduledTask::class.simpleName}")
            scheduledTask.kjørJobb()
        } catch (feil: Exception) {
            log.error(
                "Feil under kjøring av jobb ${scheduledTask::class.simpleName}",
                feil,
            )
        } finally {
            erKjørende.set(false)
        }
    }

    private fun planleggNesteKjøring() {
        synchronized(executor) {
            if (executor.isShutdown) return

            val nå = ZonedDateTime.now(tidssone)
            val nesteKjøring = finnNesteKjøretidspunkt(nå)
            val ventetid = Duration.between(
                nå.toInstant(),
                nesteKjøring.toInstant(),
            )
            val ventetidMillis = ventetid.toMillis() +
                    if (ventetid.nano % 1_000_000 == 0) 0L else 1L

            log.info(
                "Neste kjøring av ${scheduledTask::class.simpleName}: $nesteKjøring",
            )

            executor.schedule(
                {
                    try {
                        wrapJobbkjøring()
                    } finally {
                        planleggNesteKjøring()
                    }
                },
                ventetidMillis,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun finnNesteKjøretidspunkt(nå: ZonedDateTime): ZonedDateTime {
        var dato = finnNesteGyldigeDato(nå.toLocalDate())
        var nesteKjøring = dato.atTime(kjøretidspunkt).atZone(tidssone)

        while (!nesteKjøring.toInstant().isAfter(nå.toInstant())) {
            dato = finnNesteGyldigeDato(dato.plusDays(1))
            nesteKjøring = dato.atTime(kjøretidspunkt).atZone(tidssone)
        }

        return nesteKjøring
    }

    private fun finnNesteGyldigeDato(dato: LocalDate): LocalDate =
        when (gjentakelse) {
            SchedulerGjentakelse.Daglig -> dato

            is SchedulerGjentakelse.Ukentlig ->
                dato.with(TemporalAdjusters.nextOrSame(gjentakelse.ukedag))

            is SchedulerGjentakelse.HverNDag -> {
                if (dato.isBefore(gjentakelse.utgangspunktDato)) {
                    gjentakelse.utgangspunktDato
                } else {
                    val dagerSidenUtgangspunktDato = ChronoUnit.DAYS.between(
                        gjentakelse.utgangspunktDato,
                        dato,
                    )
                    val rest = dagerSidenUtgangspunktDato % gjentakelse.antallDager.toLong()

                    if (rest == 0L) {
                        dato
                    } else {
                        dato.plusDays(gjentakelse.antallDager.toLong() - rest)
                    }
                }
            }
        }
}