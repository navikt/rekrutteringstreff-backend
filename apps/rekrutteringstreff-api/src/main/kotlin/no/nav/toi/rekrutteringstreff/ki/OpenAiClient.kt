package no.nav.toi.rekrutteringstreff.ki

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.toi.JacksonConfig
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.PersondataFilter
import no.nav.toi.rekrutteringstreff.dto.ValiderRekrutteringstreffResponsDto
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
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
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val repo: KiLoggRepository,
    private val apiUrl: String = System.getenv("OPENAI_API_URL") ?: "http://localhost:9955/openai/deployments/toi-gpt-4.1/chat/completions?api-version=2025-01-01-preview",
    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: "test-key",
    private val kiVersjon: String = System.getenv("OPENAI_DEPLOYMENT") ?: "toi-gpt-4.1"
) {
    private val mapper = JacksonConfig.mapper
    private val zdtFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    companion object {
        private const val kiNavn = "azure-openai"
        private const val temperature = 0.0
        private const val maxTokens = 400
        private const val topP = 1.0
        private val responseFormat = ResponseFormat()
    }

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

            val request = HttpRequest.newBuilder()
                .uri(URI(apiUrl))
                .headers("api-key", apiKey, "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            secure(log).info("kimelding input: $userMessageFiltered  response: $response")

            if (response.statusCode() == 429) {
                secure(log).warn("For mange requester mot OpenAI.")
                throw RuntimeException("For mange requester mot OpenAI: ${response.statusCode()} - ${response.body()}")
            } else if (response.statusCode() == 400) {
                secure(log).warn("Teksten bryter med retningslinjene til OpenAi: ${response.statusCode()} - ${response.body()}")
                val error = mapper.readValue<OpenAiBadRequestDto>(response.body())
                val contentFilterResult = error.error?.innererror?.content_filter_result

                if (contentFilterResult != null) {
                    result = ValiderRekrutteringstreffResponsDto(
                        bryterRetningslinjer = true,
                        begrunnelse = "Teksten bryter med retningslinjene til KI-leverandøren og trigger: ${
                            sorterContentFilterResult(
                                contentFilterResult
                            )
                        }. Den kan derfor ikke vurderes av KI."
                    )
                    filtered = userMessageFiltered
                } else {
                    secure(log).error("Uventet feil ved kall mot OpenAI uten content_filter_result: ${response.statusCode()} - ${response.body()}")
                }

            } else if (response.statusCode() == 200) {
                val responseResult = mapper.readValue<OpenAiResponse>(response.body()).choices?.firstOrNull()?.message?.content
                    ?: error("Ingen respons fra OpenAI")
                result = mapper.readValue(responseResult.trim())
                filtered = userMessageFiltered

            } else {
                secure(log).error("Feil ved kall mot OpenAI: ${response.statusCode()}")
                throw RuntimeException("Feil ved kall mot OpenAI: ${response.statusCode()} - ${response.body()}")
            }
            

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

    private fun sorterContentFilterResult(contentFilterResult: ContentFilterResultDto): String {
        val tekstResultat = mutableListOf<String>()
        if (contentFilterResult.hate?.filtered == true) {
            tekstResultat.add("hatefull tekst")
        }
        if (contentFilterResult.jailbreak?.filtered == true) {
            tekstResultat.add("tekst som prøver å forbigå retningslinjene")
        }
        if (contentFilterResult.self_harm?.filtered == true) {
            tekstResultat.add("tekst om selvskading")
        }
        if (contentFilterResult.sexual?.filtered == true) {
            tekstResultat.add("seksuelt innhold")
        }
        if (contentFilterResult.violence?.filtered == true) {
            tekstResultat.add("voldelig innhold")
        }
        return tekstResultat.joinToString(", ")
    }

    // Dto-er basert på response body-en fra OpenAi ved 400 Bad Request
    private data class OpenAiBadRequestDto(
        val error: ErrorDto?
    )

    private data class ErrorDto(
        val message: String?,
        val type: String?,
        val param: String?,
        val code: String?,
        val status: Int?,
        val innererror: InnerErrorDto?
    )

    private data class InnerErrorDto(
        val code: String?,
        val content_filter_result: ContentFilterResultDto?
    )

    private data class ContentFilterResultDto(
        val hate: SeverityFilterResultDto?,
        val jailbreak: JailbreakFilterResultDto?,
        val self_harm: SeverityFilterResultDto?,
        val sexual: SeverityFilterResultDto?,
        val violence: SeverityFilterResultDto?
    )

    private data class SeverityFilterResultDto(
        val filtered: Boolean?,
        val severity: String?
    )

    private data class JailbreakFilterResultDto(
        val filtered: Boolean?,
        val detected: Boolean?
    )
}
