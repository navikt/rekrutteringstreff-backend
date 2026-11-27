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
import java.util.UUID

class SamtykkeForespurtLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.forbid("aktivitetskortuuid")
                it.requireKey("stillingsinfo.stillingsid")
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
                it.require("stillingsId") { node -> node.asString().toUUID() }
                it.require("svarfrist") { node -> ZonedDateTime.parse(node.asString()) }
                it.require("forespurtTidspunkt") { node -> ZonedDateTime.parse(node.asString()) }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fnr = packet["fnr"].asString()
        val stillingId = packet["stillingsId"].asString()
        val svarFrist = ZonedDateTime.parse(packet["svarfrist"].asString())

        val forespurtAv = packet["forespurtAvIdent"].asString()

        val hendelseId = UUID.randomUUID()
        val opprettetAktivitetskortId = repository.opprettDeltStilling(
            fnr = fnr,
            stillingId = stillingId,
            tittel = packet["stillingsTittel"].asString(),
            opprettetAv = forespurtAv,
            messageId = hendelseId,
            svarfrist = svarFrist,
        )

        if (opprettetAktivitetskortId != null) {
            packet["aktivitetskortuuid"] = opprettetAktivitetskortId
            packet["hendelseId"] = hendelseId
            context.publish(fnr, packet.toJson())
        } else {
            flyttEksisterendeAktivitetskortTilForslag(
                fnr = fnr,
                stillingId = stillingId,
                forespurtAv = forespurtAv,
                svarfrist = svarFrist,
                forespurtTidspunkt = ZonedDateTime.parse(packet["forespurtTidspunkt"].asString()),
            )
        }
    }

    private fun flyttEksisterendeAktivitetskortTilForslag(
        fnr: String,
        stillingId: String,
        forespurtAv: String,
        svarfrist: ZonedDateTime,
        forespurtTidspunkt: ZonedDateTime,
    ) {
        val aktivitetskortId = repository.hentAktivitetskortIdForDeltStilling(fnr, stillingId.toUUID())
        if (aktivitetskortId == null) {
            log.error("Fant ikke eksisterende aktivitetskort for delt stilling med id $stillingId (se secure log)")
            teamlog(log).error("Fant ikke eksisterende aktivitetskort for delt stilling med id $stillingId for personbruker $fnr")
            return
        }

        repository.flyttDeltStillingTilForslag(
            aktivitetskortId = aktivitetskortId,
            stillingId = stillingId,
            endretAv = forespurtAv,
            svarfrist = svarfrist,
            forespurtTidspunkt = forespurtTidspunkt,
        )
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
