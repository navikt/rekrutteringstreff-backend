package no.nav.toi.treffgjennomforing

import no.nav.toi.jobbsoker.PersonTreffId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RomfordelerTest {

    private fun person() = PersonTreffId(UUID.randomUUID())

    @Test
    fun `fordeler jevnt med round-robin`() {
        val personer = List(7) { person() }

        val rom = Romfordeler.fordelJevnt(personer, 3)

        assertThat(rom).hasSize(3)
        assertThat(rom.map { it.romnummer }).containsExactly(1, 2, 3)
        assertThat(rom.sumOf { it.jobbsøkere.size }).isEqualTo(7)
        assertThat(rom.map { it.jobbsøkere.size }).containsExactly(3, 2, 2)
    }

    @Test
    fun `ny fremmøtt havner i det minste rommet`() {
        val p1 = person()
        val p2 = person()
        val nykommer = person()
        val rom = listOf(Rom(1, listOf(p1, p2)), Rom(2, emptyList()))

        val resultat = Romfordeler.oppdaterEtterOppmøte(rom, listOf(p1, p2, nykommer))

        assertThat(resultat.first { it.romnummer == 2 }.jobbsøkere).containsExactly(nykommer)
        assertThat(resultat.first { it.romnummer == 1 }.jobbsøkere).containsExactly(p1, p2)
    }

    @Test
    fun `den som ikke lenger er møtt faller ut uten å røre de andre`() {
        val blir = person()
        val forsvinner = person()
        val rom = listOf(Rom(1, listOf(blir, forsvinner)), Rom(2, emptyList()))

        val resultat = Romfordeler.oppdaterEtterOppmøte(rom, listOf(blir))

        assertThat(resultat.first { it.romnummer == 1 }.jobbsøkere).containsExactly(blir)
        assertThat(resultat.flatMap { it.jobbsøkere }).doesNotContain(forsvinner)
    }

    @Test
    fun `normaliser fyller opp til antall rom når en arbeidsgiver kommer til`() {
        val rom = listOf(Rom(1, listOf(person())), Rom(2, listOf(person())))

        val resultat = Romfordeler.normaliser(rom, 3)

        assertThat(resultat).hasSize(3)
        assertThat(resultat.map { it.romnummer }).containsExactly(1, 2, 3)
        assertThat(resultat.first { it.romnummer == 3 }.jobbsøkere).isEmpty()
    }

    @Test
    fun `normaliser flytter hjemløse fra rom som forsvant`() {
        val hjemløs = person()
        val rom = listOf(Rom(1, listOf(person())), Rom(2, emptyList()), Rom(3, listOf(hjemløs)))

        val resultat = Romfordeler.normaliser(rom, 2)

        assertThat(resultat).hasSize(2)
        assertThat(resultat.first { it.romnummer == 2 }.jobbsøkere).containsExactly(hjemløs)
    }

    @Test
    fun `tom fordeling gir tom liste`() {
        assertThat(Romfordeler.oppdaterEtterOppmøte(emptyList(), listOf(person()))).isEmpty()
        assertThat(Romfordeler.fordelJevnt(listOf(person()), 0)).isEmpty()
    }
}
