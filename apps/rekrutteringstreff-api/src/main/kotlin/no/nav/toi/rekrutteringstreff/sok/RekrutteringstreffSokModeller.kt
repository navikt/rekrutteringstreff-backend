package no.nav.toi.rekrutteringstreff.sok

enum class Visningsstatus {
    UTKAST,
    PUBLISERT,
    SOKNADSFRIST_PASSERT,
    FULLFORT,
    AVLYST,
}

enum class Visning {
    ALLE,
    MINE,
    MITT_KONTOR,
    VALGTE_KONTORER,
}

data class RekrutteringstreffSokRequest(
    val visningsstatuser: List<Visningsstatus>? = null,
    val kontorer: List<String>? = null,
    val visning: Visning = Visning.ALLE,
    val side: Int = 0,
    val antallPerSide: Int = 25,
)

data class RekrutteringstreffSokRespons(
    val treff: List<RekrutteringstreffSokTreff>,
    val totaltAntall: Long,
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
