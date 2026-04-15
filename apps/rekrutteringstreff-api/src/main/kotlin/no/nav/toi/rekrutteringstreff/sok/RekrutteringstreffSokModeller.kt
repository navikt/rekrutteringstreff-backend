package no.nav.toi.rekrutteringstreff.sok

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import java.time.Instant

enum class SokStatus(@JsonValue val jsonVerdi: String) {
    UTKAST("UTKAST"),
    PUBLISERT("PUBLISERT"),
    FULLFØRT("FULLFØRT"),
    AVLYST("AVLYST"),
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

enum class PublisertStatus(@JsonValue val jsonVerdi: String) {
    ÅPEN_FOR_SØKERE("ÅPEN_FOR_SØKERE"),
    SVARFRIST_PASSERT("SVARFRIST_PASSERT"),
    ;
    companion object {
        fun fraJsonVerdi(verdi: String): PublisertStatus =
            PublisertStatus.entries.find { it.jsonVerdi == verdi }
                ?: throw IllegalArgumentException("Ugyldig publisertstatus: $verdi")


        fun fraDbVerdiMedFrist(verdi: RekrutteringstreffStatus, fristUtgatt: Boolean): PublisertStatus? = when (verdi) {
            RekrutteringstreffStatus.PUBLISERT -> if (fristUtgatt) SVARFRIST_PASSERT else ÅPEN_FOR_SØKERE
            else -> return null
        }
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
    val publisertStatuser: List<PublisertStatus>? = null,
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
    val publisertstatusaggregering: List<FilterValg>,
)

data class RekrutteringstreffSokTreff(
    val id: String,
    val tittel: String,
    val beskrivelse: String?,
    val status: RekrutteringstreffStatus,
    val publisertStatus: PublisertStatus?,
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
    val verdi: String,
    val antall: Long,
)

data class SokMedAggregeringResultat(
    val treff: List<RekrutteringstreffSokTreff>,
    val antallTotalt: Long,
    val statusaggregering: List<FilterValg>,
    val publisertstatusaggregering: List<FilterValg>,
)
