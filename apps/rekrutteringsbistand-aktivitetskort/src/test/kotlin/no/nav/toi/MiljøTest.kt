package no.nav.toi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** WorkOp-lytterne og andre lyttere under utvikling er bare på når miljøet ikke regnes som prod. */
class MiljøTest {

    @Test
    fun `prod og ukjente miljøer regnes som prod`() {
        assertThat(Miljø.fraClusterNavn("prod-gcp").erProd).isTrue()
        assertThat(Miljø.fraClusterNavn("annet-miljø").erProd).isTrue()
    }

    @Test
    fun `dev og lokalt regnes ikke som prod`() {
        assertThat(Miljø.fraClusterNavn("dev-gcp").erProd).isFalse()
        assertThat(Miljø.fraClusterNavn("local").erProd).isFalse()
        assertThat(Miljø.fraClusterNavn("lokalt").erProd).isFalse()
        assertThat(Miljø.fraClusterNavn(null).erProd).isFalse()
    }
}
