package no.nav.toi.rekrutteringstreff.ki.client

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.toi.JacksonConfig
import no.nav.toi.SecureLog
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.PersondataFilter
import no.nav.toi.rekrutteringstreff.dto.ValiderRekrutteringstreffResponsDto
import no.nav.toi.rekrutteringstreff.ki.EkstraMetaDbJson
import no.nav.toi.rekrutteringstreff.ki.OpenAiClient
import no.nav.toi.rekrutteringstreff.ki.OpenAiValideringsResultat
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

class DefaultOpenAiClient(
    private val httpClient: HttpClient,
    private val openAiProperties: OpenAiProperties,
) : OpenAiClient {
    private val mapper = JacksonConfig.mapper
    private val secureLogger = SecureLog(log)
    private val zdtFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    companion object {
        private const val TEMPERATURE = 0.0
        private const val MAX_TOKENS = 400
        private const val TOP_P = 1.0
        private val responseFormat = ResponseFormat()
    }

    override fun validerTekst(tekst: String): OpenAiValideringsResultat {
        lateinit var result: ValiderRekrutteringstreffResponsDto
        lateinit var filtered: String

        val elapsedMs = measureTimeMillis {
            val userMessageFiltered = PersondataFilter.filtrerUtPersonsensitiveData(tekst)
            secureLogger.info("melding før filter: $tekst etter filter: $userMessageFiltered")

            if (userMessageFiltered.isBlank()) {
                result = ValiderRekrutteringstreffResponsDto(
                    bryterRetningslinjer = true,
                    begrunnelse = "Teksten gjør det ikke klart at dette er et rekrutteringstreff eller jobbtreff, og oppfyller derfor ikke formålet."
                )
                filtered = userMessageFiltered
                return@measureTimeMillis
            }

            val body = mapper.writeValueAsString(
                OpenAiRequest(
                    messages = listOf(
                        OpenAiMessage(role = "system", content = SystemPrompt.systemMessage()),
                        OpenAiMessage(role = "user", content = userMessageFiltered)
                    ),
                    temperature = TEMPERATURE,
                    max_tokens = MAX_TOKENS,
                    top_p = TOP_P,
                    response_format = responseFormat
                )
            )

            val request = HttpRequest.newBuilder()
                .uri(URI(openAiProperties.apiUrl))
                .headers("api-key", openAiProperties.apiKey, "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            secureLogger.info("kimelding input: $userMessageFiltered  response: $response")

            if (response.statusCode() == 429) {
                secureLogger.warn("For mange requester mot OpenAI.")
                throw RuntimeException("For mange requester mot OpenAI: ${response.statusCode()} - ${response.body()}")
            } else if (response.statusCode() == 400) {
                secureLogger.warn("Teksten bryter med retningslinjene til OpenAi: ${response.statusCode()} - ${response.body()}")
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
                    secureLogger.error("Uventet feil ved kall mot OpenAI uten content_filter_result: ${response.statusCode()} - ${response.body()}")
                    throw RuntimeException("Uventet feil ved kall mot OpenAI uten content_filter_result: ${response.statusCode()} - ${response.body()}")
                }

            } else if (response.statusCode() == 200) {
                val responseResult = mapper.readValue<OpenAiResponse>(response.body()).choices?.firstOrNull()?.message?.content
                    ?: error("Ingen respons fra OpenAI")
                result = mapper.readValue(responseResult.trim())
                filtered = userMessageFiltered

                val inneholderEpost = PersondataFilter.inneholderEpost(tekst)
                val inneholderTall = PersondataFilter.inneholderTall(tekst)
                if (inneholderEpost || inneholderTall) {
                    val hva = listOfNotNull(
                        "tall".takeIf { inneholderTall },
                        "epost".takeIf { inneholderEpost }
                    ).joinToString(" og ")
                    val forrigeBegrunnelse = if (result.bryterRetningslinjer) result.begrunnelse else ""
                    result = ValiderRekrutteringstreffResponsDto(
                        bryterRetningslinjer = true,
                        begrunnelse = (forrigeBegrunnelse + " Teksten inneholder $hva som kan være personopplysninger. Sjekk dette før du går videre.").trim(),
                    )
                }

            } else {
                secureLogger.error("Feil ved kall mot OpenAI: ${response.statusCode()}")
                throw RuntimeException("Feil ved kall mot OpenAI: ${response.statusCode()} - ${response.body()}")
            }
        }

        val ekstra = EkstraMetaDbJson(
            promptVersjonsnummer = SystemPrompt.versjonsnummer,
            promptEndretTidspunkt = SystemPrompt.endretTidspunkt.format(zdtFormatter),
            promptHash = SystemPrompt.hash
        )

        return OpenAiValideringsResultat(result, filtered, elapsedMs, ekstra)
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
}
