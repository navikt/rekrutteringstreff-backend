package no.nav.toi.rekrutteringstreff.dto

import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori

data class OpprettRekrutteringstreffDto(
    val tittel: String = "Nytt rekrutteringstreff",
    val kategori: RekrutteringstreffKategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF
)
