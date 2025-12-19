package no.nav.toi.rekrutteringstreff.dto

import java.time.ZonedDateTime

data class OpprettRekrutteringstreffInternalDto(
    val tittel: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
)
