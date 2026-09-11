package no.nav.toi.treffgjennomføring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegistreringerTest {
    @Test
    fun `ingen registreringer gir verken sperre eller ryddehint`() {
        val registreringer = Registreringer(interesser = 0, intervjufordelinger = 0, vurderinger = 0)
        assertThat(registreringer.finnesRegistreringer()).isFalse()
        assertThat(registreringer.lagHint()).isEmpty()
    }

    @Test
    fun `intervjufordeling alene gir egen sperre og riktig ryddehint`() {
        val registreringer = Registreringer(interesser = 0, intervjufordelinger = 1, vurderinger = 0)
        assertThat(registreringer.finnesRegistreringer()).isTrue()
        assertThat(registreringer.lagHint()).isEqualTo("Fjern registrerte intervjufordelinger først.")
    }

    @Test
    fun `romhandling kommer sammen med hint for alle registreringstyper`() {
        val registreringer = Registreringer(interesser = 1, intervjufordelinger = 1, vurderinger = 1)
        assertThat(registreringer.lagHint(listOf("flytt personene ut av arbeidsgiverens rom"))).isEqualTo(
            "Flytt personene ut av arbeidsgiverens rom og fjern registrerte interesser og fjern registrerte intervjufordelinger og nullstill registrerte vurderinger først."
        )
    }
}
