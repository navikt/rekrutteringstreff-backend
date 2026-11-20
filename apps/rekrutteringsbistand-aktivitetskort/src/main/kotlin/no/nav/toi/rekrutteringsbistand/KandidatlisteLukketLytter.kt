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
import no.nav.toi.Repository

class KandidatlisteLukketLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "kandidat_v2.LukketKandidatliste")
                it.forbidValue("@slutt_av_hendelseskjede", true)
                it.requireKey("stillingsinfo.stillingsid", "fodselsnummerliste")
            }
            validate {
                it.requireKey(
                    "aktørIderFikkJobben",
                    "aktørIderFikkIkkeJobben",
                    "stillingsId",
                    "utførtAvNavIdent",
                    "tidspunkt",
                )
                it.require("stillingsId") { node -> node.asString().toUUID() }
                it.requireArray("aktørIderFikkJobben")
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
        val noenFikkJobben = !packet["aktørIderFikkJobben"].isEmpty
        val fnrFikkIkkeJobben = packet["aktørIderFikkIkkeJobben"].toList().map { it.asString() }.distinct().map { aktørId ->
            val fnr = packet["fodselsnummerliste"].path(aktørId)
            require(fnr.isString && fnr.asString().isNotBlank()) {
                "Fødselsnummerlistebehov for LukketKandidatliste mangler gyldig løsning for kandidat"
            }
            fnr.asString()
        }
        val stillingsId = packet["stillingsId"].asString().toUUID()
        val navIdent = packet["utførtAvNavIdent"].asString()

        repository.veilederLukkerKandidatliste(
            stillingId = stillingsId,
            fnr = fnrFikkIkkeJobben,
            endretAv = navIdent,
        )

        if (!noenFikkJobben) {
            log.info("Kandidatliste lukket uten treff: stillingsId=$stillingsId")
        }

        packet["@slutt_av_hendelseskjede"] = true
        context.publish(stillingsId.toString(), packet.toJson())
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
