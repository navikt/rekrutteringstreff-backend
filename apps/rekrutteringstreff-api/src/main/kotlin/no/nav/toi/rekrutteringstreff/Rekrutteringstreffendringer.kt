package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

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
    @param:JsonSetter(contentNulls = Nulls.SKIP)
    val endredeFelter: Set<Endringsfelttype>
)
