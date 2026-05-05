package no.nav.toi.jobbsoker.synlighet

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.toi.DefaultScheduler
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.ScheduledTask
import no.nav.toi.Scheduler
import no.nav.toi.SecureLog
import no.nav.toi.jobbsoker.JobbsøkerService
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
    private val jobbsøkerService: JobbsøkerService,
    private val rapidsConnection: RapidsConnection,
    leaderElection: LeaderElectionInterface,
) : ScheduledTask, Scheduler {

    private val secureLogger: Logger = SecureLog(log)
    private val scheduler: Scheduler = DefaultScheduler(leaderElection, this, 60, 60, TimeUnit.SECONDS)

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
        val fødselsnumreUtenSynlighet = jobbsøkerService.hentFødselsnumreUtenEvaluertSynlighet()

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
