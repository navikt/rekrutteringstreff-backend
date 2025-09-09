// OpenAiClient.kt
package no.nav.toi.rekrutteringstreff.ki

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.toi.JacksonConfig
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.PersondataFilter
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.system.measureTimeMillis

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiMessage(val role: String, val content: String)

private data class ResponseFormat(val type: String = "json_object")

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiRequest(
    val messages: List<OpenAiMessage>,
    val temperature: Double,
    val max_tokens: Int,
    val top_p: Double,
    val response_format: ResponseFormat
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Choice(val message: OpenAiMessage?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiResponse(val choices: List<Choice>?)

data class EkstraMetaDbJson(
    val promptVersjonsnummer: Int,
    val promptEndretTidspunkt: String,
    val promptHash: String
)

class OpenAiClient(
    private val repo: KiLoggRepository,
    private val apiUrl: String =
        System.getenv("OPENAI_API_URL")
            ?: "http://localhost:9955/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview",
    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: "test-key"
) {
    private val mapper = JacksonConfig.mapper
    private val zdtFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    fun validateRekrutteringstreffOgLogg(
        treffId: UUID,
        feltType: String,
        tekst: String
    ): Pair<ValiderRekrutteringstreffResponsDto, UUID?> {
        lateinit var result: ValiderRekrutteringstreffResponsDto
        lateinit var filtered: String

        val elapsedMs = measureTimeMillis {
            val userMessageFiltered = PersondataFilter.filtrerUtPersonsensitiveData(tekst)
            secure(log).info("melding før filter: $tekst etter filter: $userMessageFiltered")

            val body = mapper.writeValueAsString(
                OpenAiRequest(
                    messages = listOf(
                        OpenAiMessage(role = "system", content = SystemPrompt.systemMessage()),
                        OpenAiMessage(role = "user", content = userMessageFiltered)
                    ),
                    temperature = temperature,
                    max_tokens = maxTokens,
                    top_p = topP,
                    response_format = responseFormat
                )
            )

            val (_, _, responseResult) = apiUrl.httpPost()
                .header("api-key" to apiKey, "Content-Type" to "application/json")
                .body(body)
                .responseString()

            val raw = when (responseResult) {
                is Result.Failure -> throw responseResult.error
                is Result.Success -> responseResult.get()
            }

            val content = mapper
                .readValue<OpenAiResponse>(raw)
                .choices?.firstOrNull()?.message?.content
                ?: error("Ingen respons fra OpenAI")

            result = mapper.readValue(content.trim())
            filtered = userMessageFiltered
        }

        val ekstra = EkstraMetaDbJson(
            promptVersjonsnummer = SystemPrompt.versjonsnummer,
            promptEndretTidspunkt = SystemPrompt.endretTidspunkt.format(zdtFormatter),
            promptHash = SystemPrompt.hash
        )

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
                kiNavn = kiNavn,
                kiVersjon = kiVersjon,
                svartidMs = elapsedMs.toInt()
            )
        )

        return result to id
    }

    companion object {
        private const val kiNavn = "azure-openai"
        private const val kiVersjon = "toi-gpt-4o"
        private const val temperature = 0.0
        private const val maxTokens = 400
        private const val topP = 1.0
        private val responseFormat = ResponseFormat()
    }
}
