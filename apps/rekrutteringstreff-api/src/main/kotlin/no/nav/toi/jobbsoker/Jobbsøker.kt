package no.nav.toi.jobbsoker

import no.nav.toi.rekrutteringstreff.TreffId
import java.util.UUID

data class Fødselsnummer(private val fødselsnummer: String) {
    init {
        if (!(fødselsnummer.length == 11 && fødselsnummer.all { it.isDigit() })) {
            throw IllegalArgumentException("Fødselsnummer må være 11 siffer. Mottok [$fødselsnummer].")
        }
    }
    val asString: String = fødselsnummer
    override fun toString(): String = asString
}

data class Kandidatnummer(private val kandidatnummer: String) {
    init {
        if (kandidatnummer.isEmpty()) throw IllegalArgumentException("Kandidatnummer må være ikke-tomt.")
    }
    val asString: String = kandidatnummer
    override fun toString(): String = asString
}

data class Fornavn(private val fornavn: String) {
    init {
        if (fornavn.isEmpty()) throw IllegalArgumentException("Fornavn må være ikke-tomt.")
    }
    val asString: String = fornavn
    override fun toString(): String = asString
}

data class Etternavn(private val etternavn: String) {
    init {
        if (etternavn.isEmpty()) throw IllegalArgumentException("Etternavn må være ikke-tomt.")
    }
    val asString: String = etternavn
    override fun toString(): String = asString
}

data class Navkontor(private val navkontor: String) {
    init {
        if (navkontor.isEmpty()) {
            throw IllegalArgumentException("Navkontor kan ikke være tomt.")
        }
    }
    val asString: String = navkontor
    override fun toString(): String = asString
}

data class VeilederNavn(private val navn: String) {
    init {
        if (navn.isEmpty()) {
            throw IllegalArgumentException("VeilederNavn kan ikke være tomt.")
        }
    }
    val asString: String = navn
    override fun toString(): String = asString
}

data class VeilederNavIdent(private val ident: String) {
    init {
        if (ident.isEmpty()) {
            throw IllegalArgumentException("VeilederNavIdent kan ikke være tom.")
        }
    }
    val asString: String = ident
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
    val personTreffId: PersonTreffId,
    val treffId: TreffId,
    val fødselsnummer: Fødselsnummer,
    val kandidatnummer: Kandidatnummer?,
    val fornavn: Fornavn,
    val etternavn: Etternavn,
    val navkontor: Navkontor?,
    val veilederNavn: VeilederNavn?,
    val veilederNavIdent: VeilederNavIdent?,
    val hendelser: List<JobbsøkerHendelse> = emptyList()
)

data class PersonTreffId(private val id: UUID) {
    constructor(uuid: String) : this(UUID.fromString(uuid))

    val somUuid = id
    val somString = id.toString()
    override fun toString() = somString
}