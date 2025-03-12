package no.nav.toi.arbeidsgiver

data class Orgnr(private val orgnr: String) {
    init {
        if (!(orgnr.length == 9 && orgnr.all(Char::isDigit))) throw IllegalArgumentException("Orgnr må være 9 siffer. Mottok [$orgnr].")
    }

    val asString = orgnr
    override fun toString(): String = asString

}

data class Orgnavn(private val orgnavn: String) {
    init {
        if (orgnavn.isEmpty()) throw IllegalArgumentException("Orgnavn må være ikke-tomt.")
    }

    val asString = orgnavn
    override fun toString(): String = asString
}


data class LeggTilArbeidsgiver(
    val orgnr: Orgnr,
    val orgnavn: Orgnavn,
)
