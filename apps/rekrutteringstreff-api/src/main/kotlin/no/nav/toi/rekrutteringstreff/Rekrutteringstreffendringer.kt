package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

enum class MalParameter {
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
    fun mapParametereSomSkalVarsles(): List<MalParameter> = buildList {
        if (navn?.skalVarsle == true) add(MalParameter.NAVN)
        if (tidspunkt?.skalVarsle == true) add(MalParameter.TIDSPUNKT)
        if (svarfrist?.skalVarsle == true) add(MalParameter.SVARFRIST)
        if (sted?.skalVarsle == true) add(MalParameter.STED)
        if (introduksjon?.skalVarsle == true) add(MalParameter.INTRODUKSJON)
    }
}
