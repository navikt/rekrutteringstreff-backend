package no.nav.toi.arbeidsgiver.dto

data class ArbeidsgiverOutboundDto(
    val arbeidsgiverTreffId: String,
    val organisasjonsnummer: String,
    val navn: String,
    val status: String,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
)

