package no.nav.toi.rekrutteringsbistand

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arbeidsgiver.toi.logging.TeamLogLogger.Companion.teamlog
import no.nav.arbeidsgiver.toi.logging.log

class KandidatlisteLukketBehovLytter(
    rapidsConnection: RapidsConnection,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "kandidat_v2.LukketKandidatliste")
                it.forbidValue("@slutt_av_hendelseskjede", true)
                it.requireKey("stillingsinfo.stillingsid")
                it.forbid("fodselsnummerliste")
                it.interestedIn("@behov") { behov ->
                    require(behov.toList().none { it.asString() == "fodselsnummerliste" })
                }
            }
            validate {
                it.require("stillingsId") { node -> node.asString().toUUID() }
                it.requireArray("aktørIderFikkIkkeJobben")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val stillingsId = packet["stillingsId"].asString().toUUID()
        val behov = packet["@behov"].toList().map { it.asString() }
        packet["@behov"] = listOf("fodselsnummerliste") + behov
        packet["aktørIder"] = packet["aktørIderFikkIkkeJobben"].toList().map { it.asString() }.distinct()
        context.publish(stillingsId.toString(), packet.toJson())
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved opprettelse av fødselsnummerlistebehov for LukketKandidatliste: $problems")
        teamlog(log).error("Feil ved opprettelse av fødselsnummerlistebehov for LukketKandidatliste: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}
