package no.nav.toi.jobbsoker

/**
 * Domeneobjekt som samler jobbsøkere med tellinger.
 *
 * Kategoriene er gjensidig utelukkende - hver jobbsøker telles i nøyaktig én kategori.
 * Sletting har prioritet: en slettet jobbsøker telles alltid som slettet, uavhengig av synlighet.
 *
 * - jobbsøkere: Liste av synlige jobbsøkere (status != SLETTET OG er_synlig = TRUE)
 * - antallSynlige: = jobbsøkere.size
 * - antallSkjulte: status != SLETTET OG er_synlig = FALSE
 * - antallSlettede: status = SLETTET (uansett er_synlig)
 *
 * Invariant: antallSynlige + antallSkjulte + antallSlettede = totalt antall jobbsøkere i treffet
 */
data class JobbsøkereMedTellinger(
    val jobbsøkere: List<Jobbsøker>,
    val antallSkjulte: Int,
    val antallSlettede: Int
) {
    val antallSynlige: Int get() = jobbsøkere.size
}
