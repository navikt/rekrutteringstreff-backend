package no.nav.toi.jobbsoker.dto

data class JobbsøkereOutboundDto(
    val jobbsøkere: List<JobbsøkerOutboundDto>,
    val antallTotalt: Int,
    val antallSkjulte: Int,
    val antallSlettede: Int
)
