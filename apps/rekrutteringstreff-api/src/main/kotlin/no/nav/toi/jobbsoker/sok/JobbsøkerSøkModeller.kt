package no.nav.toi.jobbsoker.sok

import no.nav.toi.jobbsoker.JobbsøkerStatus
import java.time.Instant

enum class JobbsøkerSorteringsretning(val sql: String) {
    ASC("ASC"),
    DESC("DESC"),
    ;

    companion object {
        fun fraQueryParam(verdi: String): JobbsøkerSorteringsretning =
            when (verdi.lowercase()) {
                "asc" -> ASC
                "desc" -> DESC
                else -> throw IllegalArgumentException("Ugyldig retning: $verdi")
            }
    }
}

enum class JobbsøkerSorteringsfelt {
    NAVN,
    LAGT_TIL,
    ;

    val standardRetning: JobbsøkerSorteringsretning
        get() = when (this) {
            LAGT_TIL -> JobbsøkerSorteringsretning.DESC
            NAVN -> JobbsøkerSorteringsretning.ASC
        }

    fun sql(retning: JobbsøkerSorteringsretning): String =
        when (this) {
            NAVN -> "LOWER(j.etternavn) ${retning.sql}, LOWER(j.fornavn) ${retning.sql}, j.lagt_til_dato DESC NULLS LAST, j.jobbsoker_id DESC"
            LAGT_TIL -> "j.lagt_til_dato ${retning.sql} NULLS LAST, j.jobbsoker_id ${retning.sql}"
        }

    companion object {
        fun fraQueryParam(verdi: String): JobbsøkerSorteringsfelt =
            when (verdi.lowercase()) {
                "navn" -> NAVN
                "lagt-til" -> LAGT_TIL
                else -> throw IllegalArgumentException("Ugyldig sortering: $verdi")
            }
    }
}

data class JobbsøkerSøkRequest(
    val fritekst: String? = null,
    val status: List<JobbsøkerStatus>? = null,
    val sorteringsfelt: JobbsøkerSorteringsfelt = JobbsøkerSorteringsfelt.NAVN,
    val sorteringsretning: JobbsøkerSorteringsretning = sorteringsfelt.standardRetning,
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
    val fodselsnummer: String,
    val fornavn: String?,
    val etternavn: String?,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavident: String?,
    val status: JobbsøkerStatus,
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
