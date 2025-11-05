package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.databind.JsonNode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val klokkeslettFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val datoMedMånedFormatter = DateTimeFormatter.ofPattern("dd.\u00A0MMMM\u00A0yyyy", Locale.forLanguageTag("no-NO"))


fun JsonNode.asZonedDateTime() = ZonedDateTime.parse(asText())

fun formaterTidsperiode(startTid: ZonedDateTime, sluttTid: ZonedDateTime): String {
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