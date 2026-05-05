package no.nav.toi

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface ScheduledTask {
    fun kjørJobb()
}

interface Scheduler {
    fun start()
    fun stop()
    fun wrapJobbkjøring()
}

class DefaultScheduler (
    private val leaderElection: LeaderElectionInterface,
    private val scheduledTask: ScheduledTask,
    private val initialDelay: Long,
    private val period: Long,
    private val timeUnit: TimeUnit,
    ) : Scheduler {

        private val scheduler = Executors.newScheduledThreadPool(1)
        private val isRunning = AtomicBoolean(false)

        override fun start() {
            log.info("Starter JobbsøkerhendelserScheduler")
            scheduler.scheduleAtFixedRate(::wrapJobbkjøring, initialDelay, period, timeUnit)
        }

        override fun stop() {
            log.info("Stopper JobbsøkerhendelserScheduler")
            scheduler.shutdown()
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow()
                }
            } catch (e: InterruptedException) {
                scheduler.shutdownNow()
            }
        }

    override fun wrapJobbkjøring() {
        log.info("Starter task ${scheduledTask::class.simpleName} ")
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av task ${scheduledTask::class.simpleName} er ikke ferdig, skipper denne kjøringen.")
            return
        }

        if (leaderElection.isLeader().not()) {
            log.info("Kjøring av task ${scheduledTask::class.simpleName} skippes, instansen er ikke leader.")
            isRunning.set(false)
            return
        }
        try {
            scheduledTask.kjørJobb()
        } catch (e: Exception) {
            log.error("Feil under kjøring av task ${scheduledTask::class.simpleName}", e)
        } finally {
            isRunning.set(false)
        }
    }
}