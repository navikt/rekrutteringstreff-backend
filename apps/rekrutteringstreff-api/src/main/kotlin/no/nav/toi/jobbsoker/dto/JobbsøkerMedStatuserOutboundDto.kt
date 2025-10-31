package no.nav.toi.jobbsoker.dto

data class JobbsøkerMedStatuserOutboundDto(
    val personTreffId: String,
    val treffId: String,
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val statuser: StatuserOutboundDto,
    val hendelser: List<JobbsøkerHendelseOutboundDto>
)