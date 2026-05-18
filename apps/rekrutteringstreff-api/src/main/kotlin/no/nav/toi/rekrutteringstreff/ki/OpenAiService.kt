package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.dto.ValiderRekrutteringstreffResponsDto
import no.nav.toi.rekrutteringstreff.ki.client.DefaultOpenAiClient
import no.nav.toi.rekrutteringstreff.ki.client.SystemPrompt
import java.util.UUID

data class EkstraMetaDbJson(
    val promptVersjonsnummer: Int,
    val promptEndretTidspunkt: String,
    val promptHash: String
)

class OpenAiService(
    private val openAiClient: DefaultOpenAiClient,
    private val repo: KiLoggRepository,
    private val kiVersjon: String = System.getenv("OPENAI_DEPLOYMENT") ?: "toi-gpt-4.1"
) {
    private val mapper = JacksonConfig.mapper

    companion object {
        private const val KI_NAVN = "azure-openai"
    }

    fun validateRekrutteringstreffOgLogg(
        treffId: UUID,
        feltType: String,
        tekst: String
    ): Triple<ValiderRekrutteringstreffResponsDto, UUID?, String> {
        val (result, filtered, elapsedMs, ekstra) = openAiClient.validerTekst(tekst)

        val id = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = feltType,
                spørringFraFrontend = tekst,
                spørringFiltrert = filtered,
                systemprompt = SystemPrompt.systemMessage(),
                ekstraParametreJson = mapper.writeValueAsString(ekstra),
                bryterRetningslinjer = result.bryterRetningslinjer,
                begrunnelse = result.begrunnelse,
                kiNavn = KI_NAVN,
                kiVersjon = kiVersjon,
                svartidMs = elapsedMs.toInt()
            )
        )

        return Triple(result, id, filtered)
    }
}
