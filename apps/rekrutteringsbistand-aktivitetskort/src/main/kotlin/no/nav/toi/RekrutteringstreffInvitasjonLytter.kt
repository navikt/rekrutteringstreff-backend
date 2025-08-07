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
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.text.format

private val klokkeslettFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val datoMedMånedFormatter = DateTimeFormatter.ofPattern("dd.\u00A0MMMM\u00A0yyyy", Locale.forLanguageTag("no-NO"))

class RekrutteringstreffInvitasjonLytter(rapidsConnection: RapidsConnection, private val repository: Repository): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition{
                it.requireValue("@event_name", "rekrutteringstreffinvitasjon")
                it.forbid("aktivitetskortuuid")
                it.forbid("aktørId")    // Identmapper populerer meldinger med aktørId, men vi bruker ikke det i denne sammenhengen
            }
            validate {
                it.requireKey("fnr", "rekrutteringstreffId", "tittel", "fraTid", "tilTid",
                    "opprettetAv", "opprettetTidspunkt", "gateadresse", "postnummer", "poststed")
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

        val startDato = packet["fraTid"].asZonedDateTime()
        val sluttDato = packet["tilTid"].asZonedDateTime()

        val aktivitetskortId = repository.opprettRekrutteringstreffInvitasjon(
            fnr = fnr,
            rekrutteringstreffId = packet["rekrutteringstreffId"].asText().toUUID(),
            tittel = packet["tittel"].asText(),
            beskrivelse = "Nav arrangerer rekrutteringstreff, og vil gjerne ha deg med hvis du vil. På treffet møter du arbeidsgivere som leter etter folk å ansette. Kanskje finner du jobbmuligheten du ikke visste fantes? Følg lenken under for å lese mer om treffet og svare på invitasjonen.",
            startDato = startDato.toLocalDate(),
            sluttDato = sluttDato.toLocalDate(),
            tid = formaterTidsperiode(startDato, sluttDato),
            endretAv = packet["opprettetAv"].asText(),
            endretTidspunkt = packet["opprettetTidspunkt"].asZonedDateTime(),
            gateAdresse = packet["gateadresse"].asText(),
            postnummer = packet["postnummer"].asText(),
            poststed = packet["poststed"].asText()
        )
        if(aktivitetskortId != null) {
            packet["aktivitetskortuuid"] = aktivitetskortId
            context.publish(fnr, packet.toJson())
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av rekrutteringstreffinvitasjon: $problems")
        secure(log).error("Feil ved behandling av rekrutteringstreffinvitasjon: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}

private fun JsonNode.asZonedDateTime() = ZonedDateTime.parse(asText())

private fun formaterTidsperiode(startTid: ZonedDateTime, sluttTid: ZonedDateTime): String {
    val formatertStartKlokkeslett = klokkeslettFormatter.format(startTid)
    val formatertSluttKlokkeslett = klokkeslettFormatter.format(sluttTid)
    val formatertStartDato = datoMedMånedFormatter.format(startTid)

    return if (startTid.toLocalDate().isEqual(sluttTid.toLocalDate())) {
        "$formatertStartDato,\u00A0kl.\u00A0$formatertStartKlokkeslett–$formatertSluttKlokkeslett"
    } else {
        val formatertSluttDato = datoMedMånedFormatter.format(sluttTid)
        "$formatertStartDato,\u00A0kl.\u00A0$formatertStartKlokkeslett til $formatertSluttDato,\u00A0kl.\u00A0$formatertSluttKlokkeslett"
    }
}