package no.nav.toi.formidling

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import no.nav.toi.AccessTokenClient
import no.nav.toi.JacksonConfig
import no.nav.toi.formidling.dto.StillingDto
import no.nav.toi.log
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

typealias RekrutteringstreffStilling = StillingDto

data class OpprettRekrutteringstreffFormidling(
    val eierNavKontorEnhetId: String,
    val rekrutteringstreffId: UUID,
    val stilling: RekrutteringstreffStilling,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpprettFormidlingStillingRespons(
    val stillingsId: UUID,
    val kandidatlisteId: UUID,
)

class StillingKlient(
    private val stillingApiUrl: String,
    private val stillingScope: String,
    private val accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val objectMapper: ObjectMapper = JacksonConfig.mapper,
) {
    fun erKonfigurert(): Boolean = stillingApiUrl.isNotBlank() && stillingScope.isNotBlank()

    fun opprettFormidlingStillingOgKandidatliste(
        opprettFormidling: OpprettRekrutteringstreffFormidling,
        userToken: String,
    ): OpprettFormidlingStillingRespons {
        val onBehalfOfToken = accessTokenClient.hentAccessToken(innkommendeToken = userToken, scope = stillingScope)
        return opprettStillingOgKandidatlisteMedAccessToken(opprettFormidling, onBehalfOfToken)
    }

    private fun opprettStillingOgKandidatlisteMedAccessToken(
        opprettFormidling: OpprettRekrutteringstreffFormidling,
        accessToken: String,
    ): OpprettFormidlingStillingRespons {
        log.info("Oppretter stilling og kandidatliste via formidling-api")
        val url = "$stillingApiUrl/rekrutteringstreff/formidling"
        val requestBodyJson = objectMapper.writeValueAsString(opprettFormidling)

        fun post(): HttpResponse<String> {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $accessToken")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build()

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        try {
            val response = withRetry(::post)
            return when (response.statusCode()) {
                in 200..299 -> {
                    val respons = objectMapper.readValue(response.body(), OpprettFormidlingStillingRespons::class.java)
                    log.info("Stilling og kandidatliste opprettet med stillingId=${respons.stillingsId} og kandidatlisteId=${respons.kandidatlisteId}")
                    respons
                }
                else -> {
                    log.error("Feil ved opprettelse av stilling og kandidatliste. Status: ${response.statusCode()}, body: ${response.body()}")
                    throw StillingKlientException("Klarte ikke å opprette stilling og kandidatliste. Status: ${response.statusCode()}")
                }
            }
        } catch (e: StillingKlientException) {
            throw e
        } catch (e: Exception) {
            log.error("Feil ved opprettelse av stilling og kandidatliste", e)
            throw StillingKlientException("Klarte ikke å opprette stilling og kandidatliste.", e)
        }
    }

    companion object {
        private fun withRetry(fetch: () -> HttpResponse<String>): HttpResponse<String> {
            fun børRekjøres(response: HttpResponse<String>): Boolean {
                val status = response.statusCode()
                return status == 429 || status in 500..599
            }
            val retryConfig = RetryConfig.custom<HttpResponse<String>>()
                .retryOnResult(::børRekjøres)
                .build()
            val retry = Retry.of("opprett stilling og kandidatliste", retryConfig)
            val fetchWithRetry = Retry.decorateSupplier(retry, fetch)
            return fetchWithRetry.get()
        }
    }
}

class StillingKlientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

