package no.nav.toi.rekrutteringstreff.sok

import com.fasterxml.jackson.annotation.JsonValue
import java.time.Instant

enum class SokStatus(@JsonValue val jsonVerdi: String) {
    UTKAST("utkast"),
    PUBLISERT("publisert"),
    PUBLISERT_APEN("publisert_apen"),
    PUBLISERT_FRIST_UTGATT("publisert_frist_utgatt"),
    FULLFØRT("fullfort"),
    AVLYST("avlyst"),
    ;

    companion object {
        fun fraDbVerdi(verdi: String): SokStatus = when (verdi) {
            "UTKAST" -> UTKAST
            "PUBLISERT" -> PUBLISERT
            "FULLFØRT" -> FULLFØRT
            "AVLYST" -> AVLYST
            else -> throw IllegalArgumentException("Ugyldig status fra database: $verdi")
        }

        fun fraDbVerdiMedFrist(verdi: String, fristUtgatt: Boolean): SokStatus = when (verdi) {
            "PUBLISERT" -> if (fristUtgatt) PUBLISERT_FRIST_UTGATT else PUBLISERT_APEN
            else -> fraDbVerdi(verdi)
        }

        fun fraJsonVerdi(verdi: String): SokStatus =
            entries.find { it.jsonVerdi == verdi }
                ?: throw IllegalArgumentException("Ugyldig status: $verdi")
    }
}

enum class Visning(@JsonValue val jsonVerdi: String) {
    ALLE("alle"),
    MINE("mine"),
    MITT_KONTOR("mitt_kontor"),
    VALGTE_KONTORER("valgte_kontorer"),
    ;

    companion object {
        fun fraJsonVerdi(verdi: String): Visning =
            entries.find { it.jsonVerdi == verdi }
                ?: throw IllegalArgumentException("Ugyldig visning: $verdi")
    }
}

enum class Sortering(val sql: String, val jsonVerdi: String) {
    SIST_OPPDATERTE("sist_endret DESC", "sist_oppdaterte"),
    NYESTE("opprettet_av_tidspunkt DESC", "nyeste"),
    ELDSTE("opprettet_av_tidspunkt ASC", "eldste"),
    ;

    companion object {
        fun fraJsonVerdi(verdi: String): Sortering =
            entries.find { it.jsonVerdi == verdi }
                ?: throw IllegalArgumentException("Ugyldig sortering: $verdi")
    }
}

data class RekrutteringstreffSokRequest(
    val statuser: List<SokStatus>? = null,
    val publisertApen: Boolean? = null,
    val publisertFristUtgatt: Boolean? = null,
    val kontorer: List<String>? = null,
    val visning: Visning = Visning.ALLE,
    val sortering: Sortering = Sortering.SIST_OPPDATERTE,
    val side: Int = 1,
    val antallPerSide: Int = 20,
)

data class RekrutteringstreffSokRespons(
    val treff: List<RekrutteringstreffSokTreff>,
    val antallTotalt: Long,
    val side: Int,
    val antallPerSide: Int,
    val statusaggregering: List<FilterValg>,
)

data class RekrutteringstreffSokTreff(
    val id: String,
    val tittel: String,
    val beskrivelse: String?,
    val status: SokStatus,
    val fraTid: Instant?,
    val tilTid: Instant?,
    val svarfrist: Instant?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val opprettetAv: String,
    val opprettetAvTidspunkt: Instant,
    val sistEndret: Instant,
    val eiere: List<String>,
    val kontorer: List<String>,
    val antallArbeidsgivere: Long,
    val antallJobbsokere: Long,
)

data class FilterValg(
    val verdi: SokStatus,
    val antall: Long,
)

data class SokMedAggregeringResultat(
    val treff: List<RekrutteringstreffSokTreff>,
    val antallTotalt: Long,
    val statusaggregering: List<FilterValg>,
)
