package no.nav.toi.rekrutteringstreff.tilgangsstyring

import io.javalin.http.ForbiddenResponse
import no.nav.toi.AuthenticatedUser
import no.nav.toi.Rolle
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class WorkOpPilottilgangTest {

    private val workOpPilottilgang = WorkOpPilottilgang(listOf("0403", " 0602 "))

    private fun bruker(kontor: String?, erUtvikler: Boolean = false, erBorger: Boolean = false) = object : AuthenticatedUser {
        override fun extractNavIdent() = "A000001"
        override fun extractKontorId() = kontor
        override fun verifiserAutorisasjon(vararg arbeidsgiverRettet: Rolle) {}
        override fun extractPid() = "11111111111"
        override fun innkommendeToken() = "token"
        override fun erUtvikler() = erUtvikler
        override val erBorger = erBorger
    }

    @Test
    fun `pilotkontor har tilgang`() {
        assertTrue(workOpPilottilgang.harPilottilgang(bruker("0403")))
        assertTrue(workOpPilottilgang.harPilottilgang(bruker("0602")))
    }

    @Test
    fun `kontor utenfor piloten har ikke tilgang`() {
        assertFalse(workOpPilottilgang.harPilottilgang(bruker("9999")))
        assertThrows<ForbiddenResponse> { workOpPilottilgang.krevPilottilgang(bruker("9999")) }
    }

    @Test
    fun `manglende kontor har ikke tilgang`() {
        assertFalse(workOpPilottilgang.harPilottilgang(bruker(null)))
    }

    @Test
    fun `utvikler har tilgang uansett kontor`() {
        assertDoesNotThrow { workOpPilottilgang.krevPilottilgang(bruker("9999", erUtvikler = true)) }
    }

    @Test
    fun `borger stoppes ikke av pilotsjekken`() {
        assertTrue(workOpPilottilgang.harPilottilgang(bruker(null, erBorger = true)))
    }

    @Test
    fun `tom pilotliste gir ingen tilgang`() {
        assertFalse(WorkOpPilottilgang(emptyList()).harPilottilgang(bruker("0403")))
    }
}