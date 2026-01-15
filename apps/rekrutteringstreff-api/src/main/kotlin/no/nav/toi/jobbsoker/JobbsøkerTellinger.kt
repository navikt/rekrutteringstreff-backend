package no.nav.toi.jobbsoker

/**
 * Tellinger for jobbsøkere i et rekrutteringstreff.
 *
 * Kategoriene er gjensidig utelukkende - hver jobbsøker telles i nøyaktig én kategori.
 * Sletting har prioritet: en slettet jobbsøker telles alltid som slettet, uavhengig av synlighet.
 *
 * - antallSkjulte:  status != SLETTET OG er_synlig = FALSE
 * - antallSlettede: status = SLETTET (uansett er_synlig)
 *
 * Invariant: antallSynlige + antallSkjulte + antallSlettede = totalt antall jobbsøkere i treffet
 * (antallSynlige = jobbsøkere.size fra hentJobbsøkere)
 */
data class JobbsøkerTellinger(
    val antallSkjulte: Int,
    val antallSlettede: Int
)
