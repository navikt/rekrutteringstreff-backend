package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

enum class MalParameter {
    TITTEL,
    TIDSPUNKT,
    SVARFRIST,
    STED,
    INNHOLD
}

data class Endringsfelt<T>(
    val gammelVerdi: T?,
    val nyVerdi: T?,
    val skalVarsle: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Rekrutteringstreffendringer(
    val tittel: Endringsfelt<String>? = null,
    val fraTid: Endringsfelt<String>? = null,
    val tilTid: Endringsfelt<String>? = null,
    val svarfrist: Endringsfelt<String>? = null,
    val gateadresse: Endringsfelt<String>? = null,
    val postnummer: Endringsfelt<String>? = null,
    val poststed: Endringsfelt<String>? = null,
    val innlegg: Endringsfelt<String>? = null
) {
    fun utledMalParametere(): List<MalParameter> = buildList {
        if (tittel?.skalVarsle == true) add(MalParameter.TITTEL)
        if (fraTid?.skalVarsle == true || tilTid?.skalVarsle == true) add(MalParameter.TIDSPUNKT)
        if (svarfrist?.skalVarsle == true) add(MalParameter.SVARFRIST)
        if (gateadresse?.skalVarsle == true || postnummer?.skalVarsle == true || poststed?.skalVarsle == true) add(MalParameter.STED)
        if (innlegg?.skalVarsle == true) add(MalParameter.INNHOLD)
    }
}
