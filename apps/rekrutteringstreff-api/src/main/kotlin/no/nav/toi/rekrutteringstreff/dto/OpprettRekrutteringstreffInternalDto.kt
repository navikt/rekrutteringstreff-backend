package no.nav.toi.rekrutteringstreff.dto

import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import java.time.ZonedDateTime

data class OpprettRekrutteringstreffInternalDto(
    val tittel: String,
    val kategori: RekrutteringstreffKategori,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
)
