package no.nav.toi.rekrutteringstreff.ki.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiMessage(val role: String, val content: String)

data class ResponseFormat(val type: String = "json_object")

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiRequest(
    val messages: List<OpenAiMessage>,
    val temperature: Double,
    val max_tokens: Int,
    val top_p: Double,
    val response_format: ResponseFormat
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Choice(val message: OpenAiMessage?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiResponse(val choices: List<Choice>?)

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
