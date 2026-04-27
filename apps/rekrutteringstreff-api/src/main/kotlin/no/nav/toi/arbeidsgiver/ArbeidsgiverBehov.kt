package no.nav.toi.arbeidsgiver

enum class Ansettelsesform(val apiNavn: String) {
    FAST("Fast"),
    VIKARIAT("Vikariat"),
    ENGASJEMENT("Engasjement"),
    PROSJEKT("Prosjekt"),
    AREMAL("Åremål"),
    SESONG("Sesong"),
    FERIEJOBB("Feriejobb"),
    TRAINEE("Trainee"),
    LAERLING("Lærling"),
    SELVSTENDIG_NARINGSDRIVENDE("Selvstendig næringsdrivende"),
    ANNET("Annet");

    companion object {
        fun fraApiNavn(apiNavn: String): Ansettelsesform =
            entries.firstOrNull { it.apiNavn == apiNavn }
                ?: throw IllegalArgumentException("Ukjent ansettelsesform [$apiNavn].")
    }
}

enum class Arbeidssprak(val apiNavn: String) {
    NORSK("Norsk"),
    ENGELSK("Engelsk"),
    SVENSK("Svensk"),
    DANSK("Dansk"),
    TYSK("Tysk"),
    FRANSK("Fransk"),
    SPANSK("Spansk"),
    ANNET("Annet");

    companion object {
        fun fraApiNavn(apiNavn: String): Arbeidssprak =
            entries.firstOrNull { it.apiNavn == apiNavn }
                ?: throw IllegalArgumentException("Ukjent arbeidsspråk [$apiNavn].")
    }
}

data class BehovTag(
    val label: String,
    val kategori: String,
    val konseptId: Long,
) {
    init {
        if (label.isBlank()) throw IllegalArgumentException("BehovTag.label kan ikke være tom.")
        if (kategori.isBlank()) throw IllegalArgumentException("BehovTag.kategori kan ikke være tom.")
        if (konseptId <= 0) throw IllegalArgumentException("BehovTag.konseptId må være positiv. Tag må komme fra pam-ontologi.")
    }
}

data class ArbeidsgiverBehov(
    val samledeKvalifikasjoner: List<BehovTag>,
    val arbeidssprak: List<Arbeidssprak>,
    val antall: Int,
    val ansettelsesformer: List<Ansettelsesform>,
    val personligeEgenskaper: List<BehovTag> = emptyList(),
) {
    init {
        if (antall <= 0) throw IllegalArgumentException("Antall stillinger må være større enn 0.")
        if (samledeKvalifikasjoner.isEmpty()) throw IllegalArgumentException("Minst én samlet kvalifikasjon kreves.")
        if (arbeidssprak.isEmpty()) throw IllegalArgumentException("Minst ett arbeidsspråk kreves.")
        if (ansettelsesformer.isEmpty()) throw IllegalArgumentException("Minst én ansettelsesform kreves.")
    }
}

data class ArbeidsgiverMedBehov(
    val arbeidsgiver: Arbeidsgiver,
    val behov: ArbeidsgiverBehov?,
)
