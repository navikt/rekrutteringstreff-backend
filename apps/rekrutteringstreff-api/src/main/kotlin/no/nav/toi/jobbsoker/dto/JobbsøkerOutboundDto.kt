package no.nav.toi.jobbsoker.dto

import no.nav.toi.jobbsoker.AktuellForTreffStatus
import no.nav.toi.jobbsoker.JobbsøkerStatus

data class JobbsøkerOutboundDto(
    val personTreffId: String,
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val status: JobbsøkerStatus,
    val aktuellForTreffStatus: AktuellForTreffStatus,
    val hendelser: List<JobbsøkerHendelseOutboundDto>
)
