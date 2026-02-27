package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Typer av felt som kan endres på et rekrutteringstreff */
enum class Endringsfelttype(val tekst: String) {
    NAVN("NAVN"),
    TIDSPUNKT("TIDSPUNKT"),
    SVARFRIST("SVARFRIST"),
    STED("STED"),
    INTRODUKSJON("INTRODUKSJON"),
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Rekrutteringstreffendringer(
    val endredeFelter: Set<Endringsfelttype>
)
