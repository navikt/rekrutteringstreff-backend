package no.nav.toi.arbeidsgiver

enum class Ansettelsesform(val wireValue: String) {
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
        fun fraWireValue(wireValue: String): Ansettelsesform =
            entries.firstOrNull { it.wireValue == wireValue }
                ?: throw IllegalArgumentException("Ukjent ansettelsesform [$wireValue].")
    }
}

data class BehovTag(
    val label: String,
    val kategori: String,
    val konseptId: Long? = null,
) {
    init {
        if (label.isBlank()) throw IllegalArgumentException("BehovTag.label kan ikke være tom.")
        if (kategori.isBlank()) throw IllegalArgumentException("BehovTag.kategori kan ikke være tom.")
    }
}

data class ArbeidsgiverBehov(
    val samledeKvalifikasjoner: List<BehovTag>,
    val arbeidssprak: List<String>,
    val antall: Int,
    val ansettelsesformer: List<Ansettelsesform>,
    val personligeEgenskaper: List<BehovTag> = emptyList(),
) {
    init {
        if (antall <= 0) throw IllegalArgumentException("Antall stillinger må være større enn 0.")
        if (samledeKvalifikasjoner.isEmpty()) throw IllegalArgumentException("Minst én samlet kvalifikasjon kreves.")
        if (arbeidssprak.isEmpty()) throw IllegalArgumentException("Minst ett arbeidsspråk kreves.")
        if (ansettelsesformer.isEmpty()) throw IllegalArgumentException("Minst én ansettelsesform kreves.")
        if (arbeidssprak.any { it.isBlank() }) throw IllegalArgumentException("Arbeidsspråk kan ikke være tomme strenger.")
    }
}

data class ArbeidsgiverMedBehov(
    val arbeidsgiver: Arbeidsgiver,
    val behov: ArbeidsgiverBehov?,
)
