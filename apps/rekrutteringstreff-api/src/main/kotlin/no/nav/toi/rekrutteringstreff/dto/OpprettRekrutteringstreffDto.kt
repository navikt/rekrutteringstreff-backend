package no.nav.toi.rekrutteringstreff.dto

data class OpprettRekrutteringstreffDto(
    val opprettetAvNavkontorEnhetId: String,
    val tittel: String = "Nytt rekrutteringstreff"
)
