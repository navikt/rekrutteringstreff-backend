package no.nav.toi.rekrutteringstreff.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class Endringsfelt<T>(
    val gammelVerdi: T?,
    val nyVerdi: T?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EndringerDto(
    val tittel: Endringsfelt<String>? = null,
    val fraTid: Endringsfelt<String>? = null,
    val tilTid: Endringsfelt<String>? = null,
    val svarfrist: Endringsfelt<String>? = null,
    val gateadresse: Endringsfelt<String>? = null,
    val postnummer: Endringsfelt<String>? = null,
    val poststed: Endringsfelt<String>? = null,
    val innlegg: Endringsfelt<String>? = null
)
