package no.nav.toi.rekrutteringstreff.dto

import java.time.ZonedDateTime

data class OppdaterRekrutteringstreffDto(
    val tittel: String,
    val beskrivelse: String?,
    val fraTid: ZonedDateTime?,
    val tilTid: ZonedDateTime?,
    val svarfrist: ZonedDateTime?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?
)
