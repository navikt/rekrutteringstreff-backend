package no.nav.toi.jobbsoker

import no.nav.toi.rekrutteringstreff.TreffId

data class Fødselsnummer(private val fødselsnummer: String) {
    init {
        if (!(fødselsnummer.length == 11 && fødselsnummer.all(Char::isDigit))) {
            throw IllegalArgumentException("Fødselsnummer må være 11 siffer. Mottok [$fødselsnummer].")
        }
    }
    val asString = fødselsnummer
    override fun toString(): String = asString
}

data class Kandidatnummer(private val kandidatnummer: String) {
    init {
        if (kandidatnummer.isEmpty()) throw IllegalArgumentException("Kandidatnummer må være ikke-tomt.")
    }
    val asString = kandidatnummer
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

data class Navkontor(private val navkontor: String) {
    init {
        if (navkontor.isEmpty()) {
            throw IllegalArgumentException("Navkontor kan ikke være tomt.")
        }
    }
    val asString = navkontor
    override fun toString(): String = asString
}

data class VeilederNavn(private val navn: String) {
    init {
        if (navn.isEmpty()) {
            throw IllegalArgumentException("VeilederNavn kan ikke være tomt.")
        }
    }
    val asString = navn
    override fun toString(): String = asString
}

data class VeilederNavIdent(private val ident: String) {
    init {
        if (ident.isEmpty()) {
            throw IllegalArgumentException("VeilederNavIdent kan ikke være tom.")
        }
    }
    val asString = ident
    override fun toString(): String = asString
}

data class LeggTilJobbsøker(
    val fødselsnummer: Fødselsnummer,
    val kandidatnummer: Kandidatnummer?,
    val fornavn: Fornavn,
    val etternavn: Etternavn,
    val navkontor: Navkontor?,
    val veilederNavn: VeilederNavn?,
    val veilederNavIdent: VeilederNavIdent?
)

data class Jobbsøker(
    val treffId: TreffId,
    val fødselsnummer: Fødselsnummer,
    val kandidatnummer: Kandidatnummer?,
    val fornavn: Fornavn,
    val etternavn: Etternavn,
    val navkontor: Navkontor?,
    val veilederNavn: VeilederNavn?,
    val veilederNavIdent: VeilederNavIdent?
)