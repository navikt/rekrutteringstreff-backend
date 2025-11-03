package no.nav.toi.rekrutteringstreff.dto

data class EndringerDto(
    val tittel: String?,
    val beskrivelse: String?,
    val fraTid: String?,
    val tilTid: String?,
    val svarfrist: String?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val htmlContent: String?
)
