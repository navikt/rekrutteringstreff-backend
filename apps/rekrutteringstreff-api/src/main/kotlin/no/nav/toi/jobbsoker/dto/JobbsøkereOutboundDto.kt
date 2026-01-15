package no.nav.toi.jobbsoker.dto

/**
 * DTO for liste av jobbsøkere med telleverdier.
 *
 * Kategoriene er gjensidig utelukkende - hver jobbsøker telles i nøyaktig én kategori.
 * Sletting har prioritet: en slettet jobbsøker telles som slettet, uavhengig av synlighet.
 *
 * - antallSynlige:  status != SLETTET OG er_synlig = TRUE
 * - antallSkjulte:  status != SLETTET OG er_synlig = FALSE
 * - antallSlettede: status = SLETTET (uansett er_synlig)
 *
 * Invariant: antallSynlige + antallSkjulte + antallSlettede = totalt antall jobbsøkere i treffet
 */
data class JobbsøkereOutboundDto(
    /** Jobbsøkere som er synlige (status != SLETTET og er_synlig = TRUE) */
    val jobbsøkere: List<JobbsøkerOutboundDto>,

    /** Antall synlige jobbsøkere (= jobbsøkere.size) */
    val antallSynlige: Int,

    /** Antall skjulte jobbsøkere (status != SLETTET og er_synlig = FALSE) */
    val antallSkjulte: Int,

    /** Antall slettede jobbsøkere (status = SLETTET, uansett er_synlig-verdi) */
    val antallSlettede: Int
)
