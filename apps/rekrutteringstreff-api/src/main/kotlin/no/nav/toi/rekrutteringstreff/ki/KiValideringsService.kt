package no.nav.toi.rekrutteringstreff.ki

import io.javalin.http.UnprocessableContentResponse
import no.nav.toi.log
import java.util.UUID

class KiValideringsService(
    private val kiLoggRepository: KiLoggRepository
) {
    companion object {
        private const val FEIL_MANGLER_VALIDERING = "KI_VALIDERING_MANGLER"
        private const val FEIL_UGYLDIG_LOGG_ID = "KI_LOGG_ID_UGYLDIG"
        private const val FEIL_TEKST_ENDRET = "KI_TEKST_ENDRET"
        private const val FEIL_KREVER_BEKREFTELSE = "KI_KREVER_BEKREFTELSE"
    }

    fun verifiserKiValidering(
        tekst: String,
        kiLoggId: String?,
        lagreLikevel: Boolean,
        feltType: String
    ) {
        val normalisertTekst = normaliserTekst(tekst)
        if (normalisertTekst.isBlank()) return

        if (kiLoggId.isNullOrBlank()) {
            log.warn("Lagring avvist: Mangler KI-loggId for $feltType")
            throw UnprocessableContentResponse(
                """{"feilkode": "$FEIL_MANGLER_VALIDERING", "melding": "Teksten må KI-valideres før lagring."}"""
            )
        }

        val loggId = try {
            UUID.fromString(kiLoggId)
        } catch (e: IllegalArgumentException) {
            throw UnprocessableContentResponse(
                """{"feilkode": "$FEIL_UGYLDIG_LOGG_ID", "melding": "Ugyldig KI-validerings-ID."}"""
            )
        }

        val kiLogg = kiLoggRepository.findById(loggId)
            ?: throw UnprocessableContentResponse(
                """{"feilkode": "$FEIL_UGYLDIG_LOGG_ID", "melding": "Fant ikke KI-validering med oppgitt ID."}"""
            )

        val normalisertLoggetTekst = normaliserTekst(kiLogg.spørringFraFrontend)
        if (normalisertTekst != normalisertLoggetTekst) {
            log.warn("Lagring avvist: Tekst endret etter KI-validering for $feltType")
            throw UnprocessableContentResponse(
                """{"feilkode": "$FEIL_TEKST_ENDRET", "melding": "Teksten har blitt endret etter KI-valideringen."}"""
            )
        }

        if (kiLogg.bryterRetningslinjer && !lagreLikevel) {
            log.warn("Lagring avvist: KI rapporterte brudd og bruker har ikke bekreftet ($feltType)")
            throw UnprocessableContentResponse(
                """{"feilkode": "$FEIL_KREVER_BEKREFTELSE", "melding": "Teksten bryter retningslinjer. Bruker må bekrefte for å fortsette."}"""
            )
        }
    }

    fun erTekstEndret(tekst1: String?, tekst2: String?): Boolean =
        normaliserTekst(tekst1 ?: "") != normaliserTekst(tekst2 ?: "")

    private fun normaliserTekst(tekst: String): String =
        tekst
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
