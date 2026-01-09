package no.nav.toi.jobbsoker.synlighet

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.log

/**
 * Publiserer need-meldinger for synlighetssjekk til Rapid.
 *
 * Når en person legges til et rekrutteringstreff, sender vi en need-melding
 * for å få synlighetsstatus fra toi-synlighetsmotor.
 */
open class SynlighetsBehovPublisher(
    private val rapidsConnection: RapidsConnection?
) {
    /**
     * Publiserer et synlighetsbehov for en person.
     *
     * @param fodselsnummer Fødselsnummeret til personen
     */
    open fun publiserSynlighetsBehov(fodselsnummer: String) {
        val melding = JsonMessage.newMessage(
            mapOf(
                "@event_name" to "behov",
                "@behov" to listOf("synlighetRekrutteringstreff"),
                "fodselsnummer" to fodselsnummer
            )
        )

        log.info("Publiserer synlighetsbehov for person (fødselsnummer i securelog)")
        secure(log).info("Publiserer synlighetsbehov for fødselsnummer: $fodselsnummer")

        rapidsConnection?.publish(fodselsnummer, melding.toJson())
            ?: throw IllegalStateException("RapidsConnection er ikke satt - kan ikke publisere synlighetsbehov")
    }
}
