package no.nav.toi.treffgjennomføring.matching

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId

data class Interesse(val personTreffId: PersonTreffId, val arbeidsgiverTreffId: ArbeidsgiverTreffId)

data class ArbeidsgiverIntervjufordeling(
    val arbeidsgiverTreffId: ArbeidsgiverTreffId,
    val inkludertePersonTreffIder: List<PersonTreffId>,
    val ekskludertePersonTreffIder: List<PersonTreffId>,
)

data class Matching(
    val interesser: List<Interesse>,
    val intervjufordelinger: List<ArbeidsgiverIntervjufordeling>,
)
