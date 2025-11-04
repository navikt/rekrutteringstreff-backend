package no.nav.toi.rekrutteringstreff.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonPropertyOrder

data class Field<T>(
    val value: T? = null,
    val endret: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder("tittel", "beskrivelse", "fraTid", "tilTid", "svarfrist", "gateadresse", "postnummer", "poststed", "htmlContent")
data class EndringerDto(
    val tittel: Field<String> = Field(),
    val beskrivelse: Field<String> = Field(),
    val fraTid: Field<String> = Field(),
    val tilTid: Field<String> = Field(),
    val svarfrist: Field<String> = Field(),
    val gateadresse: Field<String> = Field(),
    val postnummer: Field<String> = Field(),
    val poststed: Field<String> = Field(),
    val htmlContent: Field<String> = Field()
)

data class RegistrerEndringerDto(
    val gamleVerdierForEndringer: EndringerDto? = null,
)
