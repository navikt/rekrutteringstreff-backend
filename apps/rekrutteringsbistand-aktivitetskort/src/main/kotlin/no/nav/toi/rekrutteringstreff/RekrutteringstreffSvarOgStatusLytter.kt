package no.nav.toi.rekrutteringstreff

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.Repository
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.log

class RekrutteringstreffSvarOgStatusLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "rekrutteringstreffSvarOgStatus")
                it.forbid("aktørId")
            }
            validate {
                it.requireKey("fnr", "rekrutteringstreffId", "endretAv", "endretAvPersonbruker")
                it.interestedIn("svar", "treffstatus")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val fnr = packet["fnr"].asText()
        val rekrutteringstreffId = packet["rekrutteringstreffId"].asText()

        val aktivitetskortId = repository.hentAktivitetskortId(
            fnr = fnr,
            rekrutteringstreffId = rekrutteringstreffId.toUUID()
        )

        if (aktivitetskortId == null) {
            log.error("Fant ikke aktivitetskort for rekrutteringstreff med id $rekrutteringstreffId (se secure log)")
            secure(log).error("Fant ikke aktivitetskort for rekrutteringstreff med id $rekrutteringstreffId for personbruker $fnr")
            return
        }

        val svar = packet["svar"].takeIf { !it.isMissingNode }?.asBoolean()
        val treffstatus = packet["treffstatus"].takeIf { !it.isMissingNode }?.asText()

        // Determine the activity status based on svar and treffstatus
        val aktivitetsStatus = when {
            // If svar is present, it takes priority
            svar != null -> {
                if (svar) {
                    // User has answered yes
                    when (treffstatus?.lowercase()) {
                        "fullført", "fullfort" -> AktivitetsStatus.FULLFORT
                        "avlyst" -> AktivitetsStatus.AVBRUTT
                        null -> AktivitetsStatus.GJENNOMFORES
                        else -> {
                            log.error("Ukjent treffstatus '$treffstatus' for bruker som har svart ja, rekrutteringstreffId=$rekrutteringstreffId (se secure log)")
                            secure(log).error("Ukjent treffstatus '$treffstatus' for bruker som har svart ja, rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr. Hopper over oppdatering.")
                            return
                        }
                    }
                } else {
                    // User has answered no
                    AktivitetsStatus.AVBRUTT
                }
            }
            // If only treffstatus is present (user hasn't answered)
            treffstatus != null -> {
                when (treffstatus.lowercase()) {
                    "fullført", "fullfort", "avlyst" -> AktivitetsStatus.AVBRUTT
                    else -> {
                        log.error("Ukjent treffstatus '$treffstatus' for bruker som ikke har svart, rekrutteringstreffId=$rekrutteringstreffId (se secure log)")
                        secure(log).error("Ukjent treffstatus '$treffstatus' for bruker som ikke har svart, rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr. Hopper over oppdatering.")
                        return
                    }
                }
            }
            else -> {
                log.error("Melding mangler både svar og treffstatus, rekrutteringstreffId=$rekrutteringstreffId (se secure log)")
                secure(log).error("Melding mangler både svar og treffstatus, rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr. Hopper over oppdatering.")
                return
            }
        }

        val endretAvPersonbruker = packet["endretAvPersonbruker"].asBoolean()
        secure(log).info("Oppdaterer aktivitetsstatus for rekrutteringstreff med id $rekrutteringstreffId for personbruker $fnr til $aktivitetsStatus (svar=$svar, treffstatus=$treffstatus)")

        repository.oppdaterAktivitetsstatus(
            aktivitetskortId = aktivitetskortId,
            aktivitetsStatus = aktivitetsStatus,
            endretAv = packet["endretAv"].asText(),
            endretAvType = if (endretAvPersonbruker) EndretAvType.PERSONBRUKERIDENT else EndretAvType.NAVIDENT
        )
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av rekrutteringstreffSvarOgStatus: $problems")
        secure(log).error("Feil ved behandling av rekrutteringstreffSvarOgStatus: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}

