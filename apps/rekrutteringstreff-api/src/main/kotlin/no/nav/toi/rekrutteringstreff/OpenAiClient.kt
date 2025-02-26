package no.nav.toi.rekrutteringstreff.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiMessage(val role: String, val content: String)
data class OpenAiRequest(val messages: List<OpenAiMessage>, val temperature: Double, val max_tokens: Int)
data class OpenAiResponse(val id: String?, val choices: List<Choice>?)
data class Choice(val message: OpenAiMessage?, val finish_reason: String?)

object OpenAiClient {
    private val openAiApiUrl = System.getenv("OPENAI_API_URL")
        ?: "http://localhost:9955/openai/deployments/toi-gpt-4o/chat/completions?api-version=2023-03-15-preview"
    private val openAiApiKey = System.getenv("OPENAI_API_KEY") ?: "test-key"
    private val objectMapper = jacksonObjectMapper()

    fun validateRekrutteringstreff(dto: ValiderRekrutteringstreffDto): ValiderRekrutteringstreffResponsDto {
        val systemMessage = VALIDATION_SYSTEM_MESSAGE
        val userMessage = "Tittel: ${dto.tittel}\nBeskrivelse: ${dto.beskrivelse}"
        val request = OpenAiRequest(
            messages = listOf(
                OpenAiMessage(role = "system", content = systemMessage),
                OpenAiMessage(role = "user", content = userMessage)
            ),
            temperature = 0.5,
            max_tokens = 1000
        )
        val requestBody = objectMapper.writeValueAsString(request)
        val (_, response, result) = openAiApiUrl.httpPost()
            .header(mapOf("api-key" to openAiApiKey, "Content-Type" to "application/json"))
            .body(requestBody)
            .responseString()
        return when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> {
                val openAiResponse = objectMapper.readValue<OpenAiResponse>(result.get())
                val content = openAiResponse.choices?.firstOrNull()?.message?.content
                    ?: throw IllegalStateException("Ingen respons fra OpenAI")

                // Fjern eventuelle markdown fra opeani endepunkt
                val cleanedContent = content.removePrefix("```json").removeSuffix("```").trim()

                objectMapper.readValue(cleanedContent, ValiderRekrutteringstreffResponsDto::class.java)
            }
        }
    }

    private val VALIDATION_SYSTEM_MESSAGE = """
        Vurder om tittel og beskrivelse for et rekrutteringstreff overholder NAVs retningslinjer. Retningslinjene gjelder for både arbeidstaker og arbeidsgiver. Sjekk at teksten:
        - Ikke avslører sensitiv informasjon om enkeltpersoner eller deltakere.
        - Tydelig formidler at arrangementet er et rekrutteringstreff, der arbeidsgivere og potensielle deltakere møtes med jobbformål.
        - Bruker relevante og inkluderende formuleringer som fremmer mangfold, uten unødvendige eller indirekte diskriminerende krav. Eksempel på diskriminering er kjønn, religion, livssyn, hudfarge, nasjonal eller etnisk opprinnelse, politisk syn, medlemskap i arbeidstakerorganisasjon, seksuell orientering, funksjonshemming eller alder. Forbudet omfatter også indirekte diskriminering; for eksempel at det stilles krav om gode norskkunnskaper eller avtjent verneplikt, uten at slike krav er nødvendige for å utføre stillingens arbeidsoppgaver på en forsvarlig måte.
        
        Returner JSON uten markdown med feltene:
        - bryterRetningslinjer (boolean)
        - begrunnelse (string)
    """.trimIndent()
}
