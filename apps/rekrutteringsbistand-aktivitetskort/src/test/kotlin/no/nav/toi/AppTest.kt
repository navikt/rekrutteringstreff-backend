package no.nav.toi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppTest {

    @Test
    fun `workop-lyttere er deaktivert i prod og ukjente miljoer`() {
        assertThat(skalRegistrereWorkOpLyttere("prod-gcp")).isFalse()
        assertThat(skalRegistrereWorkOpLyttere("annet-miljø")).isFalse()
    }

    @Test
    fun `workop-lyttere er aktivert i dev og lokalt`() {
        assertThat(skalRegistrereWorkOpLyttere("dev-gcp")).isTrue()
        assertThat(skalRegistrereWorkOpLyttere("local")).isTrue()
        assertThat(skalRegistrereWorkOpLyttere(null)).isTrue()
    }
}
