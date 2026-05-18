package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.rekrutteringstreff.dto.ValiderRekrutteringstreffResponsDto

data class OpenAiValideringsResultat(
    val result: ValiderRekrutteringstreffResponsDto,
    val filtrertTekst: String,
    val elapsedMs: Long,
    val ekstra : EkstraMetaDbJson,
)

interface OpenAiClient {
    fun validerTekst(tekst: String): OpenAiValideringsResultat
}
