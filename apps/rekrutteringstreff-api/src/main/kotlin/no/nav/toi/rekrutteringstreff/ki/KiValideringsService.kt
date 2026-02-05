package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.exception.KiValideringsException
import no.nav.toi.log
import java.util.UUID

class KiValideringsService(
    private val kiLoggRepository: KiLoggRepository
) {
    companion object {
        private const val MANGLER_VALIDERING = "KI_VALIDERING_MANGLER"
        private const val UGYLDIG_LOGG_ID = "KI_LOGG_ID_UGYLDIG"
        private const val TEKST_ENDRET = "KI_TEKST_ENDRET"
        private const val KREVER_BEKREFTELSE = "KI_KREVER_BEKREFTELSE"
        private const val FEIL_FELT_TYPE = "KI_FEIL_FELT_TYPE"
        private const val FEIL_TREFF = "KI_FEIL_TREFF"
    }

    fun verifiserKiValidering(
        tekst: String,
        kiLoggId: String?,
        lagreLikevel: Boolean,
        feltType: String,
        forventetTreffId: UUID? = null
    ) {
        val normalisertTekst = normaliserTekst(tekst)
        if (normalisertTekst.isBlank()) return

        if (kiLoggId.isNullOrBlank()) {
            log.warn("Lagring avvist: Mangler KI-loggId for $feltType")
            throw KiValideringsException(
                feilkode = MANGLER_VALIDERING,
                melding = "Teksten må KI-valideres før lagring."
            )
        }

        val loggId = try {
            UUID.fromString(kiLoggId)
        } catch (e: IllegalArgumentException) {
            throw KiValideringsException(
                feilkode = UGYLDIG_LOGG_ID,
                melding = "Ugyldig KI-validerings-ID."
            )
        }

        val kiLogg = kiLoggRepository.findById(loggId)
            ?: throw KiValideringsException(
                feilkode = UGYLDIG_LOGG_ID,
                melding = "Fant ikke KI-validering med oppgitt ID."
            )

        // Verifiser at KI-loggen tilhører riktig feltType
        if (kiLogg.feltType != feltType) {
            log.warn("Lagring avvist: KI-logg har feltType ${kiLogg.feltType}, forventet $feltType")
            throw KiValideringsException(
                feilkode = FEIL_FELT_TYPE,
                melding = "KI-valideringen tilhører feil felttype."
            )
        }

        // Verifiser at KI-loggen tilhører riktig treff (hvis forventetTreffId er oppgitt)
        if (forventetTreffId != null && kiLogg.treffId != forventetTreffId) {
            log.warn("Lagring avvist: KI-logg tilhører treff ${kiLogg.treffId}, forventet $forventetTreffId")
            throw KiValideringsException(
                feilkode = FEIL_TREFF,
                melding = "KI-valideringen tilhører et annet rekrutteringstreff."
            )
        }

        val normalisertLoggetTekst = normaliserTekst(kiLogg.spørringFraFrontend)
        if (normalisertTekst != normalisertLoggetTekst) {
            log.warn("Lagring avvist: Tekst endret etter KI-validering for $feltType")
            throw KiValideringsException(
                feilkode = TEKST_ENDRET,
                melding = "Teksten har blitt endret etter KI-valideringen."
            )
        }

        if (kiLogg.bryterRetningslinjer && !lagreLikevel) {
            log.warn("Lagring avvist: KI rapporterte brudd og bruker har ikke bekreftet ($feltType)")
            throw KiValideringsException(
                feilkode = KREVER_BEKREFTELSE,
                melding = "Teksten bryter retningslinjer. Bruker må bekrefte for å fortsette."
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
