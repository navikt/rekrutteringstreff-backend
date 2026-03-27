package no.nav.toi.jobbsoker.sok

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.toi.jobbsoker.JobbsøkerStatus
import java.time.Instant

enum class JobbsøkerSortering(val sql: String, @JsonValue val jsonVerdi: String) {
    NAVN("LOWER(js_sok.fornavn) ASC, LOWER(js_sok.etternavn) ASC", "navn"),
    INVITERT_DATO("js_sok.invitert_dato DESC NULLS LAST", "invitert_dato"),
    STATUS("js_sok.status ASC", "status"),
    ;

    companion object {
        fun fraJsonVerdi(verdi: String): JobbsøkerSortering =
            entries.find { it.jsonVerdi == verdi }
                ?: throw IllegalArgumentException("Ugyldig sortering: $verdi")
    }
}

enum class Stedstype(@JsonValue val jsonVerdi: String) {
    KOMMUNE("kommune"),
    FYLKE("fylke"),
}

data class JobbsøkerFilterverdiSted(
    val navn: String,
    val type: Stedstype,
)

data class JobbsøkerFilterverdierRespons(
    val navkontor: List<String>,
    val innsatsgrupper: List<String>,
    val steder: List<JobbsøkerFilterverdiSted>,
)

data class JobbsøkerSøkRequest(
    val fritekst: String? = null,
    val status: List<JobbsøkerStatus>? = null,
    val innsatsgruppe: List<String>? = null,
    val fylke: String? = null,
    val kommune: String? = null,
    val navkontor: String? = null,
    val veileder: String? = null,
    val sortering: JobbsøkerSortering = JobbsøkerSortering.NAVN,
    val side: Int,
    val antallPerSide: Int,
)

data class JobbsøkerSøkRespons(
    val totalt: Long,
    val antallSkjulte: Int,
    val antallSlettede: Int,
    val side: Int,
    val antallPerSide: Int,
    val jobbsøkere: List<JobbsøkerSøkTreff>,
)

data class JobbsøkerSøkTreff(
    val personTreffId: String,
    val fornavn: String?,
    val etternavn: String?,
    val innsatsgruppe: String?,
    val fylke: String?,
    val kommune: String?,
    val poststed: String?,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavident: String?,
    val status: JobbsøkerStatus,
    val invitertDato: Instant?,
    val minsideHendelser: List<MinsideHendelseSøkDto> = emptyList(),
)

data class MinsideHendelseSøkDto(
    val id: String,
    val tidspunkt: String,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?,
    val hendelseData: com.fasterxml.jackson.databind.JsonNode?,
)

data class FødselsnummerSøkRequest(
    val fodselsnummer: String,
)
