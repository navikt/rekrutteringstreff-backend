package no.nav.toi.jobbsoker.synlighet

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.log
import java.time.Instant

/**
 * Lytter på synlighetsmeldinger fra event-strømmen (toi-synlighetsmotor).
 *
 * Når synlighetsmotor evaluerer en kandidat, publiseres en melding med synlighet-informasjon.
 * Denne lytteren oppdaterer synlighet for personen i alle rekrutteringstreff de er med i.
 * (En person kan være jobbsøker i flere treff samtidig.)
 *
 * Event-strømmen har alltid lov til å oppdatere, så lenge meldingen er nyere enn eksisterende tidsstempel.
 */
class SynlighetsLytter(
    rapidsConnection: RapidsConnection,
    private val jobbsøkerService: JobbsøkerService
) : River.PacketListener {

    init {
        log.info("SynlighetsLytter initialisert")
        River(rapidsConnection).apply {
            precondition {
                it.requireKey("synlighet.erSynlig")
                it.requireValue("synlighet.ferdigBeregnet", true)
            }
            validate {
                it.requireKey("fodselsnummer")
                it.requireKey("@opprettet")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val fodselsnummer = packet["fodselsnummer"].asText()
        val erSynlig = packet["synlighet"]["erSynlig"].asBoolean()
        val opprettetTekst = packet["@opprettet"].asText()
        val meldingTidspunkt = Instant.parse(opprettetTekst)

        log.info("Mottok synlighetsmelding fra event-strøm: erSynlig=$erSynlig, tidspunkt=$opprettetTekst")
        secure(log).info("Mottok synlighetsmelding for fødselsnummer: $fodselsnummer, erSynlig=$erSynlig")

        val oppdatert = jobbsøkerService.oppdaterSynlighetFraEvent(fodselsnummer, erSynlig, meldingTidspunkt)

        if (oppdatert > 0) {
            log.info("Oppdaterte synlighet i $oppdatert rekrutteringstreff fra event-strøm")
            secure(log).info("Oppdaterte synlighet for fødselsnummer $fodselsnummer i $oppdatert treff, erSynlig=$erSynlig")
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av synlighetsmelding: $problems")
    }
}
