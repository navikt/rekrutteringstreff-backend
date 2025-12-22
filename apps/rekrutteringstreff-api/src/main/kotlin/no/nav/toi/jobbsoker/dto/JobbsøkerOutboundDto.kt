package no.nav.toi.jobbsoker.dto

import no.nav.toi.jobbsoker.JobbsøkerStatus

data class JobbsøkerOutboundDto(
    val personTreffId: String,
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val status: JobbsøkerStatus,
    val hendelser: List<JobbsøkerHendelseOutboundDto>
)
