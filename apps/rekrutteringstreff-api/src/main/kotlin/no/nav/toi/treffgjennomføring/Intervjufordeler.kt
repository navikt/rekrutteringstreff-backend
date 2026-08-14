package no.nav.toi.treffgjennomføring

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import kotlin.math.abs

/**
 * Fordeler intervjuene på tidsluker. Posisjonen i den inkluderte lista er luka:
 * alle arbeidsgivere kjører intervju 1 samtidig, så intervju 2. Står samme person
 * på samme plass hos to arbeidsgivere er det en plasskonflikt.
 *
 * Dette er ingen fullstendig løser. Den fjerner de fleste konfliktene; resten
 * vises som varsel i frontend og rettes manuelt. Ren funksjon, ingen databasetilgang.
 */
object Intervjufordeler {

    fun fordel(
        interesser: List<Interesse>,
        eksisterendeFordelinger: List<ArbeidsgiverIntervjufordeling>,
        arbeidsgivere: List<ArbeidsgiverTreffId>,
    ): List<ArbeidsgiverIntervjufordeling> {
        val utgangspunkt = utgangspunkt(interesser, eksisterendeFordelinger, arbeidsgivere)

        val etterspørsel = interesser.groupingBy { it.personTreffId }.eachCount()
        val listerekkefølge = kjør(utgangspunkt) { it }
        val mestEtterspurteFørst = kjør(utgangspunkt) { personer ->
            personer.sortedByDescending { etterspørsel[it] ?: 0 }
        }

        // Ingen av strategiene vinner alltid, og å kjøre begge er billigere enn å gjette.
        return if (konflikter(mestEtterspurteFørst) < konflikter(listerekkefølge)) mestEtterspurteFørst
        else listerekkefølge
    }

    /**
     * Ekskluderte forblir ekskluderte, interesser uten plassering regnes som
     * inkluderte, og personer det ikke lenger finnes interesse for faller ut.
     */
    private fun utgangspunkt(
        interesser: List<Interesse>,
        eksisterendeFordelinger: List<ArbeidsgiverIntervjufordeling>,
        arbeidsgivere: List<ArbeidsgiverTreffId>,
    ): List<ArbeidsgiverIntervjufordeling> = arbeidsgivere.map { arbeidsgiver ->
        val interesserte = interesser.filter { it.arbeidsgiverTreffId == arbeidsgiver }.map { it.personTreffId }
        val lagret = eksisterendeFordelinger.firstOrNull { it.arbeidsgiverTreffId == arbeidsgiver }

        val ekskluderte = lagret?.ekskludertePersonTreffIder.orEmpty().filter { it in interesserte }
        val inkluderte = lagret?.inkludertePersonTreffIder.orEmpty().filter { it in interesserte }
        val uplasserte = interesserte.filter { it !in inkluderte && it !in ekskluderte }

        ArbeidsgiverIntervjufordeling(arbeidsgiver, inkluderte + uplasserte, ekskluderte)
    }

    private fun kjør(
        utgangspunkt: List<ArbeidsgiverIntervjufordeling>,
        kørekkefølge: (List<PersonTreffId>) -> List<PersonTreffId>,
    ): List<ArbeidsgiverIntervjufordeling> {
        val opptattePlasser = mutableMapOf<PersonTreffId, MutableSet<Int>>()
        val resultat = mutableMapOf<ArbeidsgiverTreffId, ArbeidsgiverIntervjufordeling>()

        // Arbeidsgivere med færrest personer først: de har minst å gå på, og får
        // velge mens det ennå er ledige luker.
        utgangspunkt.sortedBy { it.inkludertePersonTreffIder.size }.forEach { fordeling ->
            val personer = fordeling.inkludertePersonTreffIder
            val ledige = personer.indices.toMutableSet()
            val nyRekkefølge = arrayOfNulls<PersonTreffId>(personer.size)

            kørekkefølge(personer).forEach { person ->
                val dagensPlass = personer.indexOf(person)
                val opptatte = opptattePlasser.getOrPut(person) { mutableSetOf() }
                val nærmesteFørst = ledige.sortedWith(
                    compareBy({ abs(it - dagensPlass) }, { it })
                )
                val plass = nærmesteFørst.firstOrNull { it !in opptatte } ?: nærmesteFørst.first()

                ledige.remove(plass)
                nyRekkefølge[plass] = person
                opptatte.add(plass)
            }

            resultat[fordeling.arbeidsgiverTreffId] =
                fordeling.copy(inkludertePersonTreffIder = nyRekkefølge.filterNotNull())
        }

        return utgangspunkt.map { resultat[it.arbeidsgiverTreffId] ?: it }
    }

    fun konflikter(fordelinger: List<ArbeidsgiverIntervjufordeling>): Int =
        fordelinger
            .flatMap { it.inkludertePersonTreffIder.mapIndexed { plass, person -> person to plass } }
            .groupingBy { it }
            .eachCount()
            .values
            .sumOf { it - 1 }
}
