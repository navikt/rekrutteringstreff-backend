package no.nav.toi

import no.nav.common.audit_log.cef.AuthorizationDecision
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.log.AuditLogger
import no.nav.common.audit_log.log.AuditLoggerImpl
import no.nav.toi.rekrutteringstreff.TreffId

class AuditLog {
    companion object {
        private val auditLogger: AuditLogger = AuditLoggerImpl()
        private val secureLogger = SecureLog(log)

        fun loggVisningAvRekrutteringstreff(
            navId: String,
            treffId: TreffId,
        ) {
            val loggmelding = "Rekrutteringstreff med uuid $treffId er vist til veileder"
            val cefMessage = createCefMessage(null, navId, CefMessageEvent.CREATE, loggmelding)
            auditLogger.log(cefMessage)
            secureLogger.info("Auditlogg - $loggmelding: $cefMessage")
        }

        fun createCefMessage(
            fnr: String?,
            navId: String,
            event: CefMessageEvent,
            loggmelding: String,
            timeEnded: Long = System.currentTimeMillis(),
        ): CefMessage {
            val cefMessageBuilder = CefMessage.builder()
                .applicationName("Rekrutteringsbistand")
                .loggerName("rekrutteringstreff-api")
                .event(event)
                .name("Sporingslogg")
                .authorizationDecision(AuthorizationDecision.PERMIT)
                .sourceUserId(navId)
                .timeEnded(timeEnded)
                .extension("msg", loggmelding)

            if (fnr != null) {
                cefMessageBuilder.destinationUserId(fnr)
            }

            return cefMessageBuilder.build()
        }
    }
}
