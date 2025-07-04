package no.nav.toi

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.SecureLogLogger.Companion.secure
import java.time.ZonedDateTime

class RekrutteringstreffInvitasjonLytter(rapidsConnection: RapidsConnection, private val repository: Repository): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition{
                it.requireValue("@event_name", "rekrutteringstreffinvitasjon")
                it.forbid("aktivitetskortuuid")
            }
            validate {
                it.requireKey("fnr", "rekrutteringstreffId", "tittel", "beskrivelse", "startTid", "sluttTid",
                    "endretAv", "endretAvType", "endretTidspunkt")
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
        packet["aktivitetskortuuid"] = repository.opprettRekrutteringstreffInvitasjon(
            fnr = fnr,
            rekrutteringstreffId = packet["rekrutteringstreffId"].asText().toUUID(),
            tittel = packet["tittel"].asText(),
            beskrivelse = packet["beskrivelse"].asText(),
            startDato = packet["startTid"].asZonedDateTime().toLocalDate(),
            sluttDato = packet["sluttTid"].asZonedDateTime().toLocalDate(),
            endretAv = packet["endretAv"].asText(),
            endretAvType = packet["endretAvType"].asText().let(::enumValueOf),
            endretTidspunkt = packet["endretTidspunkt"].asZonedDateTime()
        )
        context.publish(fnr, packet.toJson())
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av rekrutteringstreffinvitasjon: $problems")
        secure(log).error("Feil ved behandling av rekrutteringstreffinvitasjon: ${problems.toExtendedReport()}")
    }
}

private fun JsonNode.asZonedDateTime() = ZonedDateTime.parse(asText())
