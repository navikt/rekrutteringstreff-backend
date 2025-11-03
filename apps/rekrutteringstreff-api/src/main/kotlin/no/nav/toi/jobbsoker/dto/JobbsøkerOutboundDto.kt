package no.nav.toi.jobbsoker.dto

data class JobbsøkerOutboundDto(
    val personTreffId: String,
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val hendelser: List<JobbsøkerHendelseOutboundDto>
)