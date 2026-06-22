package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.SecureLog
import no.nav.toi.exception.KiValideringsException
import no.nav.toi.log
import java.util.*

class KiLoggService(
    private val kiLoggRepository: KiLoggRepository,
) {
    private val secureLog = SecureLog(log)

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
            loggFørsteTegnAvvikTilSecureLog(loggId, feltType, normalisertTekst, normalisertLoggetTekst)
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

    // Denne koden trengs ikke vanligvis, kan være noe man slår på om man vil debugge feil i sammenligningen av tekst etter KI-validering. 
    private fun loggFørsteTegnAvvikTilSecureLog(
        loggId: UUID,
        feltType: String,
        tekstSomLagres: String,
        tekstSomErValidert: String
    ) {
        val avvik = finnFørsteTegnAvvik(tekstSomLagres, tekstSomErValidert)
        secureLog.warn(
            "KI_TEKST_ENDRET for $feltType, loggId=$loggId. " +
                "Første avvik: $avvik. " +
                "Lengde lagring=${tekstSomLagres.length}, validering=${tekstSomErValidert.length}"
        )
    }

    private fun finnFørsteTegnAvvik(tekstSomLagres: String, tekstSomErValidert: String): String {
        val lagringTegn = tekstSomLagres.codePoints().toArray()
        val valideringTegn = tekstSomErValidert.codePoints().toArray()
        val fellesLengde = minOf(lagringTegn.size, valideringTegn.size)

        val førsteAvvikIndex = (0 until fellesLengde).firstOrNull { index ->
            lagringTegn[index] != valideringTegn[index]
        } ?: fellesLengde

        val lagring = lagringTegn.getOrNull(førsteAvvikIndex)?.let(::beskrivTegn) ?: "<slutt>"
        val validering = valideringTegn.getOrNull(førsteAvvikIndex)?.let(::beskrivTegn) ?: "<slutt>"

        return "index=$førsteAvvikIndex, lagring=$lagring, validering=$validering"
    }

    private fun beskrivTegn(kodepunkt: Int): String =
        "U+${kodepunkt.toString(16).uppercase().padStart(4, '0')} (${Character.getName(kodepunkt) ?: "ukjent tegn"})"

    private fun normaliserTekst(tekst: String): String =
        tekst
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+"), " ")
            .trim()

    fun hentKiLoggUuiderForScheduledSletting(månederSidenLoggOpprettet: Int): List<UUID> {
        return kiLoggRepository.hentKiLoggIderForScheduledSletting(månederSidenLoggOpprettet)
    }

    fun slettKILogger(loggUuider: List<UUID>) {
        kiLoggRepository.slettKiLogger(loggUuider)
    }
}
