package no.nav.toi.jobbsoker.sok

import no.nav.toi.jobbsoker.JobbsøkerStatus

data class JobbsøkerFormidlingRequest(
    val fritekst: String? = null,
    val side: Int = 1,
    val antallPerSide: Int = 25,
)

data class JobbsøkerFormidlingTreff(
    val personTreffId: String,
    val fødselsnummer: String,
    val fornavn: String?,
    val etternavn: String?,
    val status: JobbsøkerStatus,
)

data class JobbsøkerFormidlingRespons(
    val totalt: Long,
    val side: Int,
    val jobbsøkere: List<JobbsøkerFormidlingTreff>,
)
