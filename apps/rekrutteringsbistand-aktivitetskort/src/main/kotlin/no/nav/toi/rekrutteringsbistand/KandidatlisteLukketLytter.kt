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
import no.nav.arbeidsgiver.toi.logging.TeamLogLogger
import no.nav.arbeidsgiver.toi.logging.TeamLogLogger.Companion.teamlog
import no.nav.arbeidsgiver.toi.logging.log
import no.nav.toi.Repository

class KandidatlisteLukketLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
) : River.PacketListener {

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
                it.require("fnrFikkJobben") { node -> if(!node.isArray) throw IllegalStateException("fnrFikkJobben må være en array") }
                it.require("fnrFikkIkkeJobben") { node -> if(!node.isArray) throw IllegalStateException("fnrFikkIkkeJobben må være en array") }
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

        // Mirror kandidat-api behavior: only candidates with JA-svar are updated when list closes.
        repository.veilederLukkerKandidatliste(
            stillingId = stillingsId,
            fnr = fnrFikkIkkeJobben,
            endretAv = navIdent,
        )

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
        teamlog(log).error("Feil ved behandling av LukketKandidatliste: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}

