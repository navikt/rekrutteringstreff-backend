package no.nav.toi.jobbsoker.sok

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.toi.jobbsoker.JobbsøkerStatus
import java.time.Instant

enum class JobbsøkerSorteringsretning(val sql: String) {
    ASC("ASC"),
    DESC("DESC"),
    ;

    @JsonValue
    fun jsonVerdi(): String = name.lowercase()

    companion object {
        @JvmStatic
        @JsonCreator
        fun fraJson(verdi: String): JobbsøkerSorteringsretning =
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

    @JsonValue
    fun jsonVerdi(): String = when (this) {
        NAVN -> "navn"
        LAGT_TIL -> "lagt-til"
    }

    val standardRetning: JobbsøkerSorteringsretning
        get() = when (this) {
            LAGT_TIL -> JobbsøkerSorteringsretning.DESC
            NAVN -> JobbsøkerSorteringsretning.ASC
        }

    fun sql(retning: JobbsøkerSorteringsretning): String =
        when (this) {
            NAVN -> "LOWER(v.etternavn) ${retning.sql}, LOWER(v.fornavn) ${retning.sql}, v.lagt_til_dato DESC NULLS LAST, v.jobbsoker_id DESC"
            LAGT_TIL -> "v.lagt_til_dato ${retning.sql} NULLS LAST, v.jobbsoker_id ${retning.sql}"
        }

    companion object {
        @JvmStatic
        @JsonCreator
        fun fraJson(verdi: String): JobbsøkerSorteringsfelt =
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
    @JsonProperty("sortering")
    val sorteringsfelt: JobbsøkerSorteringsfelt = JobbsøkerSorteringsfelt.NAVN,
    @JsonProperty("retning")
    val sorteringsretning: JobbsøkerSorteringsretning = sorteringsfelt.standardRetning,
    val side: Int = 1,
    val antallPerSide: Int = 25,
)

data class JobbsøkerSøkRespons(
    val totalt: Long,
    val antallSkjulte: Int,
    val antallSlettede: Int,
    val side: Int,
    val jobbsøkere: List<JobbsøkerSøkTreff>,
    val antallPerStatus: Map<JobbsøkerStatus, Int> = emptyMap(),
)

data class JobbsøkerSøkTreff(
    val personTreffId: String,
    val fødselsnummer: String,
    val fornavn: String?,
    val etternavn: String?,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavident: String?,
    val status: JobbsøkerStatus,
    val lagtTilDato: Instant?,
    val lagtTilAv: String?,
    val lagtTilAvNavn: String?,
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
