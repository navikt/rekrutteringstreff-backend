package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.databind.JsonNode
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
import no.nav.toi.log
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val klokkeslettFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val datoMedMånedFormatter = DateTimeFormatter.ofPattern("dd.\u00A0MMMM\u00A0yyyy", Locale.forLanguageTag("no-NO"))

class RekrutteringstreffOppdateringLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "rekrutteringstreffoppdatering")
                it.forbid("aktørId")    // Identmapper populerer meldinger med aktørId, men vi bruker ikke det i denne sammenhengen
            }
            validate {
                it.requireKey("fnr", "rekrutteringstreffId", "tittel", "fraTid", "tilTid",
                    "gateadresse", "postnummer", "poststed")
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
        val rekrutteringstreffId = packet["rekrutteringstreffId"].asText().toUUID()

        val startDato = packet["fraTid"].asZonedDateTime()
        val sluttDato = packet["tilTid"].asZonedDateTime()

        repository.oppdaterRekrutteringstreffAktivitetskort(
            fnr = fnr,
            rekrutteringstreffId = rekrutteringstreffId,
            tittel = packet["tittel"].asText(),
            startDato = startDato.toLocalDate(),
            sluttDato = sluttDato.toLocalDate(),
            tid = formaterTidsperiode(startDato, sluttDato),
            gateAdresse = packet["gateadresse"].asText(),
            postnummer = packet["postnummer"].asText(),
            poststed = packet["poststed"].asText()
        )

        log.info("Oppdaterte aktivitetskort for rekrutteringstreff $rekrutteringstreffId")
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av rekrutteringstreffoppdatering: $problems")
        secure(log).error("Feil ved behandling av rekrutteringstreffoppdatering: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}

private fun JsonNode.asZonedDateTime() = ZonedDateTime.parse(asText())

private fun formaterTidsperiode(startTid: ZonedDateTime, sluttTid: ZonedDateTime): String {
    val formatertStartKlokkeslett = klokkeslettFormatter.format(startTid)
    val formatertSluttKlokkeslett = klokkeslettFormatter.format(sluttTid)
    val formatertStartDato = datoMedMånedFormatter.format(startTid)

    return if (startTid.toLocalDate().isEqual(sluttTid.toLocalDate())) {
        "$formatertStartDato, kl.\u00A0$formatertStartKlokkeslett–$formatertSluttKlokkeslett"
    } else {
        val formatertSluttDato = datoMedMånedFormatter.format(sluttTid)
        "$formatertStartDato, kl.\u00A0$formatertStartKlokkeslett til $formatertSluttDato, kl.\u00A0$formatertSluttKlokkeslett"
    }
}

