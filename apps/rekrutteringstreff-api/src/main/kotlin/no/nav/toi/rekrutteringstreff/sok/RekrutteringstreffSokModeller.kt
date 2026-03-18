package no.nav.toi.rekrutteringstreff.sok

import com.fasterxml.jackson.annotation.JsonValue

enum class Visningsstatus(@JsonValue val jsonVerdi: String) {
    UTKAST("utkast"),
    PUBLISERT("publisert"),
    SOKNADSFRIST_PASSERT("soknadsfrist_passert"),
    FULLFORT("fullfort"),
    AVLYST("avlyst"),
    ;

    companion object {
        fun fraJsonVerdi(verdi: String): Visningsstatus =
            entries.find { it.jsonVerdi == verdi }
                ?: throw IllegalArgumentException("Ugyldig visningsstatus: $verdi")
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
    val statuser: List<Visningsstatus>? = null,
    val kontorer: List<String>? = null,
    val visning: Visning = Visning.ALLE,
    val sortering: Sortering = Sortering.SIST_OPPDATERTE,
    val side: Int = 1,
    val antallPerSide: Int = 25,
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
    val visningsstatus: Visningsstatus,
    val fraTid: String?,
    val tilTid: String?,
    val svarfrist: String?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val kommune: String?,
    val fylke: String?,
    val opprettetAvTidspunkt: String,
    val sistEndret: String,
    val eiere: List<String>,
    val kontorer: List<String>,
)

data class FilterValg(
    val verdi: String,
    val antall: Long,
)
