package no.nav.toi.jobbsoker.synlighet

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.SecureLog
import no.nav.toi.log
import org.slf4j.Logger

/**
 * Publiserer need-meldinger for synlighetssjekk til Rapid.
 *
 * Når en person legges til et rekrutteringstreff, sender vi en need-melding
 * for å få synlighetsstatus fra toi-synlighetsmotor.
 */
open class SynlighetsBehovPublisher(
    private val rapidsConnection: RapidsConnection?
) {
    private val secureLogger: Logger = SecureLog(log)

    open fun publiserSynlighetsBehov(fodselsnummer: String) {
        val melding = JsonMessage.newMessage(
            mapOf(
                "@event_name" to "behov",
                "@behov" to listOf("synlighetRekrutteringstreff"),
                "fodselsnummer" to fodselsnummer
            )
        )

        log.info("Publiserer synlighetsbehov for person (fødselsnummer i securelog)")
        secureLogger.info("Publiserer synlighetsbehov for fødselsnummer: $fodselsnummer")

        rapidsConnection?.publish(fodselsnummer, melding.toJson())
            ?: throw IllegalStateException("RapidsConnection er ikke satt - kan ikke publisere synlighetsbehov")
    }
}
