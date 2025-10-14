package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.ki

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.toi.JacksonConfig
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.PersondataFilter
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto
import no.nav.toi.rekrutteringstreff.ki.SystemPrompt
import java.lang.Thread.sleep
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

//Egen klient som brukes til test av robs vurderinger uten database og logging

private const val KI_Navn = "azure-openai"
private const val KI_Versjon = "toi-gpt-4o"
private const val TEMPERATURE = 0.0
private const val MAX_TOKENS = 400
private const val TOP_P = 1.0

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiMessageTestClient(val role: String, val content: String)

private data class ResponseFormatTestClient(val type: String = "json_object")

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiRequestTestClient(
    val messages: List<OpenAiMessageTestClient>,
    val temperature: Double,
    val max_tokens: Int,
    val top_p: Double,
    val response_format: ResponseFormatTestClient
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ChoiceTestClient(val message: OpenAiMessageTestClient?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiResponseTestClient(val choices: List<ChoiceTestClient>?)

class OpenAiTestClient(
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val apiUrl: String =
        System.getenv("OPENAI_API_URL")
            ?: "http://localhost:9955/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview",
    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: "test-key"
) {
    private val mapper = JacksonConfig.mapper

    fun validerTekst(tekst: String): ValiderRekrutteringstreffResponsDto {
        val filtrert = PersondataFilter.filtrerUtPersonsensitiveData(tekst)

        val body = mapper.writeValueAsString(
            OpenAiRequestTestClient(
                messages = listOf(
                    OpenAiMessageTestClient(role = "system", content = SystemPrompt.systemMessage()),
                    OpenAiMessageTestClient(role = "user", content = filtrert)
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

        if (response.statusCode() == 429) {
            log.warn("For mange requester mot OpenAI. Venter fem sekunder og prøver igjen.")
            sleep(5000)
            return validerTekst(tekst)
        }

        if (response.statusCode() == 200) {
            val result =
                mapper.readValue<OpenAiResponseTestClient>(response.body()).choices?.firstOrNull()?.message?.content
                    ?: error("Ingen respons fra OpenAI")
            return mapper.readValue(result.trim())
        } else if (response.statusCode() == 400) {
            log.warn("Teksten bryter med retningslinjene til OpenAi: ${response.statusCode()} - ${response.body()}")
            val error = mapper.readValue<OpenAiBadRequestDto>(response.body())
            val contentFilterResult = error.error?.innererror?.content_filter_result
            if (contentFilterResult != null) {
                val respons = ValiderRekrutteringstreffResponsDto(
                    bryterRetningslinjer = true,
                    begrunnelse = "Teksten bryter med retningslinjene til KI-leverandøren og trigger: ${
                        sorterContentFilterResult(
                            contentFilterResult
                        )
                    }. Den kan derfor ikke vurderes av KI."
                )
                log.info(respons.begrunnelse)
                return respons
            } else
                throw RuntimeException("Feil ved kall mot OpenAI: ${response.statusCode()} - ${response.body()}")
        } else {
            log.error("Feil ved kall mot OpenAI: ${response.statusCode()}")
        }
        throw RuntimeException("Feil ved kall mot OpenAI: ${response.statusCode()} - ${response.body()}")
    }

    private fun sorterContentFilterResult(contentFilterResult: ContentFilterResultDto): String {
        var tekstResultat = ""
        if (contentFilterResult.hate?.filtered == true) {
            tekstResultat += "hatefull tekst, "
        }
        if (contentFilterResult.jailbreak?.filtered == true) {
            tekstResultat += "tekst som prøver å forbigå retningslinjene, "
        }
        if (contentFilterResult.self_harm?.filtered == true) {
            tekstResultat += "tekst om selvskading, "
        }
        if (contentFilterResult.sexual?.filtered == true) {
            tekstResultat += "seksuelt innhold, "
        }
        if (contentFilterResult.violence?.filtered == true) {
            tekstResultat += "voldelig innhold, "
        }
        return tekstResultat.trim().trimEnd(',')
    }

    companion object {
        private const val kiNavn = KI_Navn
        private const val kiVersjon = KI_Versjon
        private const val temperature = TEMPERATURE
        private const val maxTokens = MAX_TOKENS
        private const val topP = TOP_P
        private val responseFormat = ResponseFormatTestClient()
    }

    // Dto-er basert på response body-en fra OpenAi ved 400 Bad Request
    data class OpenAiBadRequestDto(
        val error: ErrorDto?
    )

    data class ErrorDto(
        val message: String?,
        val type: String?,
        val param: String?,
        val code: String?,
        val status: Int?,
        val innererror: InnerErrorDto?
    )

    data class InnerErrorDto(
        val code: String?,
        val content_filter_result: ContentFilterResultDto?
    )

    data class ContentFilterResultDto(
        val hate: SeverityFilterResultDto?,
        val jailbreak: JailbreakFilterResultDto?,
        val self_harm: SeverityFilterResultDto?,
        val sexual: SeverityFilterResultDto?,
        val violence: SeverityFilterResultDto?
    )

    data class SeverityFilterResultDto(
        val filtered: Boolean?,
        val severity: String?
    )

    data class JailbreakFilterResultDto(
        val filtered: Boolean?,
        val detected: Boolean?
    )
}






