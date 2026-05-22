package no.nav.toi.jobbsoker.dto

data class KontorDto(
    val kontornummer: String,
    val kontornavn: String?,
)

data class JobbsøkerMedStatuserOutboundDto(
    val personTreffId: String,
    val treffId: String,
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val kontor: KontorDto?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val statuser: StatuserOutboundDto,
    val hendelser: List<JobbsøkerHendelseOutboundDto>
)