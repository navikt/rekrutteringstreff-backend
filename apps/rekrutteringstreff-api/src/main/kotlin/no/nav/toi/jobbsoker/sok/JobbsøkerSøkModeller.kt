package no.nav.toi.jobbsoker.sok

import no.nav.toi.jobbsoker.JobbsøkerStatus
import java.time.Instant

enum class JobbsøkerSorteringsretning(val sql: String) {
    ASC("ASC"),
    DESC("DESC"),
}

data class JobbsøkerSorteringValg(
    val sortering: JobbsøkerSortering,
    val retning: JobbsøkerSorteringsretning,
)

enum class JobbsøkerSortering {
    NAVN,
    LAGT_TIL_DATO,
    STATUS,
    ;

    val standardRetning: JobbsøkerSorteringsretning
        get() = when (this) {
            LAGT_TIL_DATO -> JobbsøkerSorteringsretning.DESC
            NAVN, STATUS -> JobbsøkerSorteringsretning.ASC
        }

    fun sql(retning: JobbsøkerSorteringsretning): String =
        when (this) {
            NAVN -> "LOWER(js_sok.etternavn) ${retning.sql}, LOWER(js_sok.fornavn) ${retning.sql}, js_sok.lagt_til_dato DESC NULLS LAST, js_sok.jobbsoker_id DESC"
            LAGT_TIL_DATO -> "js_sok.lagt_til_dato ${retning.sql} NULLS LAST, js_sok.jobbsoker_id ${retning.sql}"
            STATUS -> "CASE js_sok.status WHEN 'INVITERT' THEN 1 WHEN 'SVART_JA' THEN 2 WHEN 'SVART_NEI' THEN 3 WHEN 'LAGT_TIL' THEN 4 WHEN 'SLETTET' THEN 5 ELSE 6 END ${retning.sql}, LOWER(js_sok.etternavn) ASC, LOWER(js_sok.fornavn) ASC, js_sok.lagt_til_dato DESC NULLS LAST, js_sok.jobbsoker_id DESC"
        }

    companion object {
        fun fraQueryParam(verdi: String): JobbsøkerSorteringValg =
            when (verdi) {
                "navn", "navn-asc" -> JobbsøkerSorteringValg(NAVN, JobbsøkerSorteringsretning.ASC)
                "navn-desc" -> JobbsøkerSorteringValg(NAVN, JobbsøkerSorteringsretning.DESC)
                "lagt_til_dato", "lagt-til-desc", "lagt_til_dato-desc" ->
                    JobbsøkerSorteringValg(LAGT_TIL_DATO, JobbsøkerSorteringsretning.DESC)
                "lagt-til-asc", "lagt_til_dato-asc" ->
                    JobbsøkerSorteringValg(LAGT_TIL_DATO, JobbsøkerSorteringsretning.ASC)
                "status", "status-asc" -> JobbsøkerSorteringValg(STATUS, JobbsøkerSorteringsretning.ASC)
                "status-desc" -> JobbsøkerSorteringValg(STATUS, JobbsøkerSorteringsretning.DESC)
                else -> throw IllegalArgumentException("Ugyldig sortering: $verdi")
            }
    }
}

data class JobbsøkerFilterverdierRespons(
    val innsatsgrupper: List<String>,
)

data class JobbsøkerSøkRequest(
    val fritekst: String? = null,
    val status: List<JobbsøkerStatus>? = null,
    val innsatsgruppe: List<String>? = null,
    val sortering: JobbsøkerSortering = JobbsøkerSortering.NAVN,
    val sorteringsretning: JobbsøkerSorteringsretning = sortering.standardRetning,
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
    val telefonnummer: String?,
    val status: JobbsøkerStatus,
    val invitertDato: Instant?,
    val lagtTilDato: Instant?,
    val lagtTilAv: String?,
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
