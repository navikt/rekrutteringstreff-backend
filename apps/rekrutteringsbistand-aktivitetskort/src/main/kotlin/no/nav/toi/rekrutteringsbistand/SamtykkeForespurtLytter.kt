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
import java.time.ZonedDateTime

class SamtykkeForespurtLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.forbid("aktivitetskortuuid")
            }
            validate {
                it.requireKey(
                    "fnr",
                    "stillingsId",
                    "stillingsTittel",
                    "svarfrist",
                    "forespurtAvIdent",
                    "forespurtTidspunkt",
                )
                it.require("stillingsId") { node -> node.asText().toUUID() }
                it.require("svarfrist") { node -> ZonedDateTime.parse(node.asText()) }
                it.require("forespurtTidspunkt") { node -> ZonedDateTime.parse(node.asText()) }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fnr = packet["fnr"].asText()
        val stillingId = packet["stillingsId"].asText()

        repository.opprettDeltStilling(
            fnr = fnr,
            stillingId = stillingId,
            tittel = packet["stillingsTittel"].asText(),
            opprettetAv = packet["forespurtAvIdent"].asText(),
        )?.let { aktivitetskortId ->
            packet["aktivitetskortuuid"] = aktivitetskortId
            context.publish(fnr, packet.toJson())
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av $EVENT_NAME: $problems")
        teamlog(log).error("Feil ved behandling av $EVENT_NAME: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }

    private companion object {
        const val EVENT_NAME = "samtykke-forespurt-om-deling-av-cv"
    }
}
