package no.nav.toi.treffgjennomføring.matching

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ArbeidsgiverIntervjufordelingTest {

    private fun person() = PersonTreffId(UUID.randomUUID())
    private val arbeidsgiver = ArbeidsgiverTreffId(UUID.randomUUID())

    @Test
    fun `ny person legges sist blant de inkluderte`() {
        val først = person()
        val ny = person()
        val fordeling = ArbeidsgiverIntervjufordeling(arbeidsgiver, listOf(først), emptyList())

        assertThat(fordeling.medPerson(ny).inkludertePersonTreffIder).containsExactly(først, ny)
    }

    @Test
    fun `person som allerede er fordelt blir stående`() {
        val ekskludert = person()
        val fordeling = ArbeidsgiverIntervjufordeling(arbeidsgiver, emptyList(), listOf(ekskludert))

        assertThat(fordeling.medPerson(ekskludert)).isEqualTo(fordeling)
    }

    @Test
    fun `person fjernes fra begge listene`() {
        val inkludert = person()
        val ekskludert = person()
        val fordeling = ArbeidsgiverIntervjufordeling(arbeidsgiver, listOf(inkludert), listOf(ekskludert))

        assertThat(fordeling.utenPerson(inkludert).utenPerson(ekskludert))
            .isEqualTo(ArbeidsgiverIntervjufordeling.tom(arbeidsgiver))
    }

    @Test
    fun `fjerning av person som ikke er fordelt gir uendret fordeling`() {
        val fordeling = ArbeidsgiverIntervjufordeling(arbeidsgiver, listOf(person()), emptyList())

        assertThat(fordeling.utenPerson(person())).isEqualTo(fordeling)
    }
}
