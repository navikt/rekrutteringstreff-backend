package no.nav.toi.arbeidsgiver

import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.ZonedDateTime
import java.util.UUID

data class Orgnr(private val orgnr: String) {
    init {
        if (!(orgnr.length == 9 && orgnr.all(Char::isDigit)))
            throw IllegalArgumentException("Orgnr må være 9 siffer. Mottok [$orgnr].")
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

data class Næringskode(
    val kode: String?,
    val beskrivelse: String?,
)

data class LeggTilArbeidsgiver(
    val orgnr: Orgnr,
    val orgnavn: Orgnavn,
    val næringskoder: List<Næringskode> = emptyList()
)

data class Arbeidsgiver(
    val arbeidsgiverTreffId: ArbeidsgiverTreffId,
    val treffId: TreffId,
    val orgnr: Orgnr,
    val orgnavn: Orgnavn,
    val næringskoder: List<Næringskode> = emptyList(),
    val hendelser: List<ArbeidsgiverHendelse> = emptyList()
)

data class ArbeidsgiverTreffId(private val id: UUID) {
    constructor(uuid: String) : this(UUID.fromString(uuid))

    val somUuid = id
    val somString = id.toString()
    override fun toString() = somString
}
