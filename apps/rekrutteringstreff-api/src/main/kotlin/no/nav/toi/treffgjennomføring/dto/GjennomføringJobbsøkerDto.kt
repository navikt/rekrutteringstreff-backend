package no.nav.toi.treffgjennomføring.dto

import no.nav.toi.jobbsoker.JobbsøkerStatus

data class GjennomføringJobbsøkereRequestDto(
    val status: List<JobbsøkerStatus>? = null,
    val side: Int = 1,
    val antallPerSide: Int = 100,
)

/** Bare feltene treffgjennomføringen trenger. Egen DTO, slik at nye felt i jobbsøkersøket ikke lekker hit. */
data class GjennomføringJobbsøkerDto(
    val personTreffId: String,
    val fornavn: String?,
    val etternavn: String?,
    val status: JobbsøkerStatus,
    /** `null` for usynlige på WorkOp. */
    val fødselsnummer: String?,
)

data class GjennomføringJobbsøkersideDto(
    val totalt: Long,
    val side: Int,
    /** Teller alle som vises i treffgjennomføringen, uavhengig av statusfilteret. */
    val antallPerStatus: Map<JobbsøkerStatus, Int>,
    val jobbsøkere: List<GjennomføringJobbsøkerDto>,
)
