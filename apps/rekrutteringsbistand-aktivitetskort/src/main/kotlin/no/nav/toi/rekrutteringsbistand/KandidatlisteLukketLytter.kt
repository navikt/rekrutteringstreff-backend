package no.nav.toi.rekrutteringsbistand

import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.Repository
import no.nav.toi.SecureLog
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.log

class KandidatlisteLukketLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
) : River.PacketListener {
    private val secureLog = SecureLog(log)

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "LukketKandidatliste")
                it.forbidValue("@slutt_av_hendelseskjede", true)
            }
            validate {
                it.requireKey(
                    "fnrFikkJobben",
                    "fnrFikkIkkeJobben",
                    "stillingsId",
                    "utførtAvNavIdent",
                    "tidspunkt",
                )
                it.require("stillingsId") { node -> node.asText().toUUID() }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val noenFikkJobben = !(packet["fnrFikkJobben"] as ArrayNode).isEmpty
        val fnrFikkIkkeJobben = packet["fnrFikkIkkeJobben"].map { it.asText() }
        val stillingsId = packet["stillingsId"].asText().toUUID()
        val navIdent = packet["utførtAvNavIdent"].asText()

        fnrFikkIkkeJobben
            .mapNotNull { fnr -> repository.hentAktivitetskortIdForDeltStilling(fnr, stillingsId) }
            // Mirror kandidat-api behavior: only candidates with JA-svar are updated when list closes.
            .filter { aktivitetskortId -> repository.hentSisteAktivitetsstatus(aktivitetskortId) == AktivitetsStatus.GJENNOMFORES }
            .forEach { aktivitetskortId ->
                repository.oppdaterAktivitetsstatus(
                    aktivitetskortId = aktivitetskortId,
                    aktivitetsStatus = AktivitetsStatus.FULLFORT,
                    endretAv = navIdent,
                    endretAvType = EndretAvType.NAVIDENT,
                )
            }

        if (!noenFikkJobben) {
            log.info("Kandidatliste lukket uten treff: stillingsId=$stillingsId")
        }

        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av LukketKandidatliste: $problems")
        secureLog.error("Feil ved behandling av LukketKandidatliste: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}

