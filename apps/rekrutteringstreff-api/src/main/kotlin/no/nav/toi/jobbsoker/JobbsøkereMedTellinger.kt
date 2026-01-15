package no.nav.toi.jobbsoker

data class JobbsøkereMedTellinger(
    val jobbsøkere: List<Jobbsøker>,
    val antallSkjulte: Int, // status != SLETTET OG er_synlig = FALSE
    val antallSlettede: Int // status = SLETTET (uansett er_synlig)
) {
    val antallSynlige: Int get() = jobbsøkere.size
}
