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
 * Lytter på svar fra need-meldinger (synlighetRekrutteringstreff).
 *
 * Når en person legges til et rekrutteringstreff, sendes en need-melding til toi-synlighetsmotor.
 * Denne lytteren mottar svaret og oppdaterer synlighet for personen i alle treff de er med i
 * (der synlighet ikke allerede er satt).
 *
 * Skriver KUN hvis synlighet_sist_oppdatert er NULL.
 * Dette forhindrer at need-svar overskriver nyere data fra event-strømmen.
 */
class SynlighetsBehovLytter(
    rapidsConnection: RapidsConnection,
    private val jobbsøkerService: JobbsøkerService
) : River.PacketListener {

    init {
        log.info("SynlighetsBehovLytter initialisert")
        River(rapidsConnection).apply {
            precondition {
                // Lytter på meldinger der synlighetRekrutteringstreff-behovet er løst
                it.requireKey("synlighetRekrutteringstreff.erSynlig")
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
        val erSynlig = packet["synlighetRekrutteringstreff"]["erSynlig"].asBoolean()
        val opprettetTekst = packet["@opprettet"].asText()
        val meldingTidspunkt = Instant.parse(opprettetTekst)

        log.info("Mottok need-svar for synlighetRekrutteringstreff: erSynlig=$erSynlig")
        secure(log).info("Mottok need-svar for fødselsnummer: $fodselsnummer, erSynlig=$erSynlig")

        val oppdatert = jobbsøkerService.oppdaterSynlighetFraNeed(fodselsnummer, erSynlig, meldingTidspunkt)

        if (oppdatert > 0) {
            log.info("Oppdaterte synlighet i $oppdatert rekrutteringstreff fra need-svar")
            secure(log).info("Oppdaterte synlighet for fødselsnummer $fodselsnummer i $oppdatert treff, erSynlig=$erSynlig")
        } else {
            log.info("Ingen oppdatering fra need-svar (synlighet allerede satt av event-strøm eller person ikke funnet i noen treff)")
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av synlighetsbehovsvar: $problems")
    }
}
