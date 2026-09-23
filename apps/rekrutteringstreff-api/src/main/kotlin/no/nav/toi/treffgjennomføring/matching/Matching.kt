package no.nav.toi.treffgjennomføring.matching

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId

data class Interesse(val personTreffId: PersonTreffId, val arbeidsgiverTreffId: ArbeidsgiverTreffId)

data class ArbeidsgiverIntervjufordeling(
    val arbeidsgiverTreffId: ArbeidsgiverTreffId,
    val inkludertePersonTreffIder: List<PersonTreffId>,
    val ekskludertePersonTreffIder: List<PersonTreffId>,
) {
    fun inneholder(person: PersonTreffId) = person in inkludertePersonTreffIder || person in ekskludertePersonTreffIder

    /** Nye personer legges sist blant de inkluderte. En person som allerede er fordelt, blir stående. */
    fun medPerson(person: PersonTreffId) =
        if (inneholder(person)) this
        else copy(inkludertePersonTreffIder = inkludertePersonTreffIder + person)

    fun utenPerson(person: PersonTreffId) = copy(
        inkludertePersonTreffIder = inkludertePersonTreffIder - person,
        ekskludertePersonTreffIder = ekskludertePersonTreffIder - person,
    )

    companion object {
        fun tom(arbeidsgiverTreffId: ArbeidsgiverTreffId) =
            ArbeidsgiverIntervjufordeling(arbeidsgiverTreffId, emptyList(), emptyList())
    }
}

data class Matching(
    val interesser: List<Interesse>,
    val intervjufordelinger: List<ArbeidsgiverIntervjufordeling>,
)
