package no.nav.toi.arbeidsgiver.dto

data class ArbeidsgiverOutboundDto(
    val arbeidsgiverTreffId: String,
    val organisasjonsnummer: String,
    val navn: String,
    val hendelser: List<ArbeidsgiverHendelseOutboundDto>
)
