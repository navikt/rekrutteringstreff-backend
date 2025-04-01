package no.nav.toi.jobbsoker

import no.nav.toi.rekrutteringstreff.TreffId

data class Fødselsnummer(private val fødselsnummer: String) {
    init {
        if (!(fødselsnummer.length == 11 && fødselsnummer.all(Char::isDigit))) throw IllegalArgumentException("Fødselsnummer må være 11 siffer. Mottok [$fødselsnummer].")
    }

    val asString = fødselsnummer
    override fun toString(): String = asString

}

data class Fornavn(private val fornavn: String) {
    init {
        if (fornavn.isEmpty()) throw IllegalArgumentException("Fornavn må være ikke-tomt.")
    }

    val asString = fornavn
    override fun toString(): String = asString
}

data class Etternavn(private val etternavn: String) {
    init {
        if (etternavn.isEmpty()) throw IllegalArgumentException("Etternavn må være ikke-tomt.")
    }

    val asString = etternavn
    override fun toString(): String = asString
}

data class LeggTilJobbsøker(
    val fødselsnummer: Fødselsnummer,
    val fornavn: Fornavn,
    val etternavn: Etternavn
)

data class Jobbsøker(
    val treffId: TreffId,
    val fødselsnummer: Fødselsnummer,
    val fornavn: Fornavn,
    val etternavn: Etternavn
)
