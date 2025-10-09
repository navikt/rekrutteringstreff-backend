package no.nav.toi.rekrutteringstreff.ki

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.toi.JacksonConfig
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.PersondataFilter
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto
import java.lang.Thread.sleep
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
/*
//Egen klient som brukes til test av robs vurderinger uten database og logging

private const val KI_Navn = "azure-openai"
private const val KI_Versjon = "toi-gpt-4o"
private const val TEMPERATURE = 1.0
private const val MAX_TOKENS = 4000
private const val TOP_P = 1.0

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiMessageTestClient(val role: String, val content: String)

private data class ResponseFormatTestClient(val type: String = "json_object")

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
private data class OpenAiRequestTestClient(
    val messages: List<OpenAiMessageTestClient>,
    val temperature: Double,
    val max_completion_tokens: Int,
    val top_p: Double,
    val response_format: ResponseFormatTestClient,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ChoiceTestClient(val message: OpenAiMessageTestClient?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiResponseTestClient(val choices: List<ChoiceTestClient>?)

class OpenAiTestClient(
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val apiUrl: String =
        System.getenv("OPENAI_API_URL")
            ?: "https://arbeidsmarked-dev.openai.azure.com/openai/deployments/toi-gpt-5-chat/chat/completions?api-version=2025-01-01-preview",
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
                max_completion_tokens = maxTokens,
                top_p = topP,
                response_format = responseFormat,
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
            val raw = response.body()
            log.info("OpenAI 200 response body: $raw")
            val result = mapper.readValue<OpenAiResponseTestClient>(raw).choices?.firstOrNull()?.message?.content
                ?: error("Ingen respons fra OpenAI")
            if (result.isBlank()) {
                throw RuntimeException("OpenAI returnerte tomt content i message.choices[0].message.content. Rårespons: $raw")
            }
            return mapper.readValue(result.trim())
        } else {
            log.error("Feil ved kall mot OpenAI: ${response.statusCode()}")
        }
        throw RuntimeException("Feil ved kall mot OpenAI: ${response.statusCode()} - ${response.body()}")
    }

    companion object {
        private const val kiNavn = KI_Navn
        private const val kiVersjon = KI_Versjon
        private const val temperature = TEMPERATURE
        private const val maxTokens = MAX_TOKENS
        private const val topP = TOP_P
        private val responseFormat = ResponseFormatTestClient()
    }
}
*/





