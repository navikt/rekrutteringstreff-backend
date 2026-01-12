package no.nav.toi.jobbsoker.synlighet

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.SecureLog
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.log
import org.slf4j.Logger
import java.time.Instant

/**
 * Lytter på svar fra need-meldinger (synlighetRekrutteringstreff).
 *
 * SynlighetsBehovScheduler sender need-meldinger for jobbsøkere som mangler synlighetsstatus.
 * Denne lytteren mottar svaret og oppdaterer synlighet for personen i alle treff de er med i
 * (der synlighet ikke allerede er satt).
 *
 * Hvis ferdigBeregnet=false eller erSynlig mangler, behandles personen som ikke-synlig.
 * Dette sikrer at ved usikkerhet om synlighet, skjules personen (fail-safe).
 *
 * Skriver KUN hvis synlighet_sist_oppdatert er NULL.
 * Dette forhindrer at need-svar overskriver nyere data fra event-strømmen.
 */
class SynlighetsBehovLytter(
    rapidsConnection: RapidsConnection,
    private val jobbsøkerService: JobbsøkerService
) : River.PacketListener {

    private val secureLogger: Logger = SecureLog(log)

    init {
        log.info("SynlighetsBehovLytter initialisert")
        River(rapidsConnection).apply {
            precondition {
                // Lytter på meldinger der synlighetRekrutteringstreff-behovet finnes (løst eller ikke)
                it.requireKey("synlighetRekrutteringstreff")
            }
            validate {
                it.requireKey("fodselsnummer")
                it.requireKey("@opprettet")
                it.interestedIn("synlighetRekrutteringstreff.erSynlig")
                it.interestedIn("synlighetRekrutteringstreff.ferdigBeregnet")
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
        val opprettetTekst = packet["@opprettet"].asText()
        val meldingTidspunkt = Instant.parse(opprettetTekst)
        
        val synlighetNode = packet["synlighetRekrutteringstreff"]
        val ferdigBeregnet = synlighetNode["ferdigBeregnet"]?.asBoolean() ?: false
        val erSynligNode = synlighetNode["erSynlig"]
        
        // Hvis ikke ferdigBeregnet eller erSynlig mangler, behandle som ikke-synlig (fail-safe)
        val erSynlig = if (ferdigBeregnet && erSynligNode != null && !erSynligNode.isNull) {
            erSynligNode.asBoolean()
        } else {
            log.info("Need-svar ufullstendig (ferdigBeregnet=$ferdigBeregnet, erSynlig=${erSynligNode?.asBoolean()}) - behandler som ikke-synlig")
            false
        }

        log.info("Mottok need-svar for synlighetRekrutteringstreff: erSynlig=$erSynlig, ferdigBeregnet=$ferdigBeregnet")
        secureLogger.info("Mottok need-svar for fødselsnummer: $fodselsnummer, erSynlig=$erSynlig")

        val oppdatert = jobbsøkerService.oppdaterSynlighetFraNeed(fodselsnummer, erSynlig, meldingTidspunkt)

        if (oppdatert > 0) {
            log.info("Oppdaterte synlighet i $oppdatert rekrutteringstreff fra need-svar")
            secureLogger.info("Oppdaterte synlighet for fødselsnummer $fodselsnummer i $oppdatert treff, erSynlig=$erSynlig")
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
