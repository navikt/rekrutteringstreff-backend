package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Typer av felt som kan endres på et rekrutteringstreff */
enum class Endringsfelttype {
    NAVN,
    TIDSPUNKT,
    SVARFRIST,
    STED,
    INTRODUKSJON
}

data class Endringsfelt<T>(
    val gammelVerdi: T?,
    val nyVerdi: T?,
    val skalVarsle: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Rekrutteringstreffendringer(
    val navn: Endringsfelt<String>? = null,
    val sted: Endringsfelt<String>? = null,
    val tidspunkt: Endringsfelt<String>? = null,
    val svarfrist: Endringsfelt<String>? = null,
    val introduksjon: Endringsfelt<String>? = null
) {
    fun hentFelterSomSkalVarsles(): List<Endringsfelttype> = buildList {
        if (navn?.skalVarsle == true) add(Endringsfelttype.NAVN)
        if (tidspunkt?.skalVarsle == true) add(Endringsfelttype.TIDSPUNKT)
        if (svarfrist?.skalVarsle == true) add(Endringsfelttype.SVARFRIST)
        if (sted?.skalVarsle == true) add(Endringsfelttype.STED)
        if (introduksjon?.skalVarsle == true) add(Endringsfelttype.INTRODUKSJON)
    }
}
