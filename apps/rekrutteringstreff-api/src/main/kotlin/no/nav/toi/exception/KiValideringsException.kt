package no.nav.toi.exception

/**
 * Exception som kastes ved KI-valideringsfeil.
 * Håndteres sentralt i App.kt og returnerer 422 Unprocessable Entity med JSON-body.
 */
class KiValideringsException(
    val feilkode: String,
    val melding: String
) : RuntimeException("$feilkode: $melding")
