package no.nav.toi
import no.nav.common.audit_log.cef.CefMessageEvent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AuditLogTest {

    @Test
    fun `CEF-format skal ha korrekt struktur`() {
        val fnr = "00000000025"
        val navId = "A123456"
        val event = CefMessageEvent.ACCESS
        val loggmelding = "Hentet rekrutteringstreff"

        val cefMessage = AuditLog.createCefMessage(fnr, navId, event, loggmelding, 1767612071473)

        Assertions.assertEquals(
            "CEF:0|Rekrutteringsbistand|rekrutteringstreff-api|1.0|audit:access|Sporingslogg|INFO|flexString1=Permit msg=Hentet rekrutteringstreff duid=00000000025 flexString1Label=Decision end=1767612071473 suid=A123456",
            cefMessage.toString()
        )
    }

    @Test
    fun `CEF-format skal ha korrekt struktur uten fødselsnummer`() {
        val navId = "A123456"
        val event = CefMessageEvent.ACCESS
        val loggmelding = "Hentet rekrutteringstreff"

        val cefMessage = AuditLog.createCefMessage(null, navId, event, loggmelding, 1767612071473)

        Assertions.assertEquals(
            "CEF:0|Rekrutteringsbistand|rekrutteringstreff-api|1.0|audit:access|Sporingslogg|INFO|flexString1=Permit msg=Hentet rekrutteringstreff flexString1Label=Decision end=1767612071473 suid=A123456",
            cefMessage.toString()
        )
    }
}
