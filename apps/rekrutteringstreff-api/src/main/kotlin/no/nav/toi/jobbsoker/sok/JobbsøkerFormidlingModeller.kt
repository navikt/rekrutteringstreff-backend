package no.nav.toi.jobbsoker.sok

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
)

data class JobbsøkerFormidlingRespons(
    val totalt: Long,
    val side: Int,
    val jobbsøkere: List<JobbsøkerFormidlingTreff>,
)
