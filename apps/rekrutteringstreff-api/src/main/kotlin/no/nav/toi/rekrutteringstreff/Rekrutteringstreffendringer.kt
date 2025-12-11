package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

enum class EndringMalParameter {
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
    fun mapParametereSomSkalVarsles(): List<EndringMalParameter> = buildList {
        if (navn?.skalVarsle == true) add(EndringMalParameter.NAVN)
        if (tidspunkt?.skalVarsle == true) add(EndringMalParameter.TIDSPUNKT)
        if (svarfrist?.skalVarsle == true) add(EndringMalParameter.SVARFRIST)
        if (sted?.skalVarsle == true) add(EndringMalParameter.STED)
        if (introduksjon?.skalVarsle == true) add(EndringMalParameter.INTRODUKSJON)
    }
}
