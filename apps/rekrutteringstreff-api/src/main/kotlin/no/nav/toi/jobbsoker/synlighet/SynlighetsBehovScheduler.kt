package no.nav.toi.jobbsoker.synlighet

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.SecureLog
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.log
import org.slf4j.Logger
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scheduler som periodisk sjekker etter jobbsøkere der synlighet ikke er evaluert,
 * og trigger need-meldinger for å hente synlighetsstatus.
 *
 * Når en jobbsøker legges til, lagres den med synlighet_sist_oppdatert = NULL.
 * Denne scheduleren finner slike jobbsøkere og publiserer need-meldinger for dem.
 *
 * Need-svar oppdaterer kun synlighet hvis synlighet_sist_oppdatert er NULL,
 * så event-strømmen (som setter synlighet_sist_oppdatert) har alltid prioritet.
 */
class SynlighetsBehovScheduler(
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val rapidsConnection: RapidsConnection
) {
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)
    private val secureLogger: Logger = SecureLog(log)

    fun start() {
        log.info("Starter SynlighetsBehovScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        // Kjører hvert minutt
        scheduler.scheduleAtFixedRate(::behandleJobbsøkereUtenSynlighet, initialDelay, 60, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper SynlighetsBehovScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    fun behandleJobbsøkereUtenSynlighet() {
        log.info("Kjører SynlighetsBehovScheduler for å finne jobbsøkere uten evaluert synlighet")
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av SynlighetsBehovScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            val fødselsnumreUtenSynlighet = jobbsøkerRepository.hentFødselsnumreUtenEvaluertSynlighet()

            if (fødselsnumreUtenSynlighet.isEmpty()) {
                log.debug("Ingen jobbsøkere uten evaluert synlighet.")
                return
            }

            log.info("Fant ${fødselsnumreUtenSynlighet.size} jobbsøkere uten evaluert synlighet - trigger need-meldinger")

            fødselsnumreUtenSynlighet.forEach { fnr ->
                try {
                    publiserSynlighetsBehov(fnr)
                } catch (e: Exception) {
                    log.error("Kunne ikke publisere synlighetsbehov fra scheduler", e)
                }
            }

            log.info("Ferdig med å trigge need-meldinger for ${fødselsnumreUtenSynlighet.size} jobbsøkere")
        } catch (e: Exception) {
            log.error("Feil under kjøring av SynlighetsBehovScheduler", e)
        } finally {
            isRunning.set(false)
        }
    }

    private fun publiserSynlighetsBehov(fodselsnummer: String) {
        val melding = JsonMessage.newMessage(
            mapOf(
                "@event_name" to "behov",
                "@behov" to listOf("synlighetRekrutteringstreff"),
                "fodselsnummer" to fodselsnummer
            )
        )

        log.info("Publiserer synlighetsbehov for person (fødselsnummer i securelog)")
        secureLogger.info("Publiserer synlighetsbehov for fødselsnummer: $fodselsnummer")

        rapidsConnection.publish(fodselsnummer, melding.toJson())
    }
}
