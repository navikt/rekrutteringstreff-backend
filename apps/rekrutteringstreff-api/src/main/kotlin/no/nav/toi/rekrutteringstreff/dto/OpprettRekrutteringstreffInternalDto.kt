package no.nav.toi.rekrutteringstreff.dto

import java.time.ZonedDateTime

data class OpprettRekrutteringstreffInternalDto( // TODO Are: Finne et bedre navn? Ligger den i feil klasse?
    val tittel: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
)
