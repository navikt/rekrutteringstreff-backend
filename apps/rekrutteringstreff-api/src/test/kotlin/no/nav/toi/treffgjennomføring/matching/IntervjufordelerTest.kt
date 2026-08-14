package no.nav.toi.treffgjennomføring.matching

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class IntervjufordelerTest {

    private fun person() = PersonTreffId(UUID.randomUUID())
    private fun arbeidsgiver() = ArbeidsgiverTreffId(UUID.randomUUID())

    @Test
    fun `interesser uten plassering blir inkludert`() {
        val a = arbeidsgiver()
        val p1 = person()
        val p2 = person()

        val resultat = Intervjufordeler.fordel(
            interesser = listOf(Interesse(p1, a), Interesse(p2, a)),
            eksisterendeFordelinger = emptyList(),
            arbeidsgivere = listOf(a),
        )

        assertThat(resultat).hasSize(1)
        assertThat(resultat.first().inkludertePersonTreffIder).containsExactlyInAnyOrder(p1, p2)
        assertThat(resultat.first().ekskludertePersonTreffIder).isEmpty()
    }

    @Test
    fun `ekskluderte forblir ekskluderte`() {
        val a = arbeidsgiver()
        val inkludert = person()
        val ekskludert = person()

        val resultat = Intervjufordeler.fordel(
            interesser = listOf(Interesse(inkludert, a), Interesse(ekskludert, a)),
            eksisterendeFordelinger = listOf(
                ArbeidsgiverIntervjufordeling(a, listOf(inkludert), listOf(ekskludert))
            ),
            arbeidsgivere = listOf(a),
        )

        assertThat(resultat.first().ekskludertePersonTreffIder).containsExactly(ekskludert)
        assertThat(resultat.first().inkludertePersonTreffIder).containsExactly(inkludert)
    }

    @Test
    fun `personer uten registrert interesse faller ut av begge lister`() {
        val a = arbeidsgiver()
        val medInteresse = person()
        val utenInteresse = person()
        val tidligereEkskludert = person()

        val resultat = Intervjufordeler.fordel(
            interesser = listOf(Interesse(medInteresse, a)),
            eksisterendeFordelinger = listOf(
                ArbeidsgiverIntervjufordeling(a, listOf(medInteresse, utenInteresse), listOf(tidligereEkskludert))
            ),
            arbeidsgivere = listOf(a),
        )

        assertThat(resultat.first().inkludertePersonTreffIder).containsExactly(medInteresse)
        assertThat(resultat.first().ekskludertePersonTreffIder).isEmpty()
    }

    @Test
    fun `alle interesserte er med etter fordeling`() {
        val arbeidsgivere = List(3) { arbeidsgiver() }
        val personer = List(5) { person() }
        val interesser = arbeidsgivere.flatMap { ag -> personer.map { Interesse(it, ag) } }

        val resultat = Intervjufordeler.fordel(interesser, emptyList(), arbeidsgivere)

        assertThat(resultat).hasSize(3)
        resultat.forEach { fordeling ->
            assertThat(fordeling.inkludertePersonTreffIder).containsExactlyInAnyOrderElementsOf(personer)
        }
    }

    @Test
    fun `fordelingen gir færre plasskonflikter enn samme rekkefølge hos alle`() {
        val arbeidsgivere = List(4) { arbeidsgiver() }
        val personer = List(6) { person() }
        val interesser = arbeidsgivere.flatMap { ag -> personer.map { Interesse(it, ag) } }

        // Samme rekkefølge hos alle gir konflikt i hver eneste tidsluke.
        val naiv = arbeidsgivere.map { ArbeidsgiverIntervjufordeling(it, personer, emptyList()) }
        val fordelt = Intervjufordeler.fordel(interesser, emptyList(), arbeidsgivere)

        assertThat(Intervjufordeler.konflikter(fordelt))
            .isLessThan(Intervjufordeler.konflikter(naiv))
    }

    @Test
    fun `konflikter teller samme person på samme plass hos flere arbeidsgivere`() {
        val a1 = arbeidsgiver()
        val a2 = arbeidsgiver()
        val p = person()

        val medKonflikt = listOf(
            ArbeidsgiverIntervjufordeling(a1, listOf(p), emptyList()),
            ArbeidsgiverIntervjufordeling(a2, listOf(p), emptyList()),
        )
        val utenKonflikt = listOf(
            ArbeidsgiverIntervjufordeling(a1, listOf(p), emptyList()),
            ArbeidsgiverIntervjufordeling(a2, listOf(person(), p), emptyList()),
        )

        assertThat(Intervjufordeler.konflikter(medKonflikt)).isEqualTo(1)
        assertThat(Intervjufordeler.konflikter(utenKonflikt)).isEqualTo(0)
    }

    @Test
    fun `arbeidsgiver uten interesser får tom fordeling`() {
        val medInteresse = arbeidsgiver()
        val utenInteresse = arbeidsgiver()
        val p = person()

        val resultat = Intervjufordeler.fordel(
            interesser = listOf(Interesse(p, medInteresse)),
            eksisterendeFordelinger = emptyList(),
            arbeidsgivere = listOf(medInteresse, utenInteresse),
        )

        assertThat(resultat.first { it.arbeidsgiverTreffId == utenInteresse }.inkludertePersonTreffIder).isEmpty()
    }
}
