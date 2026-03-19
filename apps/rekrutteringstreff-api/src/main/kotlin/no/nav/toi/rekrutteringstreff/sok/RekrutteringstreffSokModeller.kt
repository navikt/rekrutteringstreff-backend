package no.nav.toi.rekrutteringstreff.sok

import com.fasterxml.jackson.annotation.JsonValue

enum class SokStatus(@JsonValue val jsonVerdi: String) {
    UTKAST("utkast"),
    PUBLISERT("publisert"),
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
    val apenForSokere: Boolean? = null,
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
    val antallApenForSokere: Long,
)

data class RekrutteringstreffSokTreff(
    val id: String,
    val tittel: String,
    val beskrivelse: String?,
    val status: SokStatus,
    val apenForSokere: Boolean,
    val fraTid: String?,
    val tilTid: String?,
    val svarfrist: String?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val opprettetAvTidspunkt: String,
    val sistEndret: String,
    val eiere: List<String>,
    val kontorer: List<String>,
)

data class FilterValg(
    val verdi: String,
    val antall: Long,
)
