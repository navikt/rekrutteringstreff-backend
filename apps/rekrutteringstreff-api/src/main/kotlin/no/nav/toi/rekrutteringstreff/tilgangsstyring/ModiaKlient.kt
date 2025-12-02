package no.nav.toi.rekrutteringstreff.tilgangsstyring

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import no.nav.toi.AccessTokenClient
import no.nav.toi.JacksonConfig
import no.nav.toi.log
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ModiaKlient(
    private val modiaContextHolderUrl: String,
    private val modiaContextHolderScope: String,
    private val accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient,
) {
    private val objectMapper = JacksonConfig.mapper

    companion object {
        private fun withRetry(fetch: () -> HttpResponse<String>): HttpResponse<String> {
            fun isFailure(response: HttpResponse<String>) = response.statusCode() !in 200..299
            val retryConfig = RetryConfig.custom<HttpResponse<String>>()
                .retryOnResult(::isFailure)
                .build()
            val retry = Retry.of("fetch access token", retryConfig)
            val fetchAccessTokenWithRetry = Retry.decorateSupplier(retry, fetch)
            return fetchAccessTokenWithRetry.get()
        }
    }

    fun hentVeiledersAktivEnhet(innkommendeToken: String) : String? {
        val modiaUrl = "$modiaContextHolderUrl/api/context/v2/aktivenhet"
        val accessToken = accessTokenClient.hentAccessToken(innkommendeToken = innkommendeToken, scope = modiaContextHolderScope)

        fun fetch(): HttpResponse<String> {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(modiaUrl))
                .header("Authorization","Bearer $accessToken")
                .GET()
                .build()

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        try {
            val respons = withRetry(::fetch)

            return if (respons.statusCode() in 200..299) {
                objectMapper.readValue(respons.body(), AktivEnhet::class.java).aktivEnhet
            } else {
                log.error("Det skjedde en feil ved henting av aktiv enhet fra Modia. Status: ${respons.statusCode()}, body: ${respons.body()}")
                null
            }

        } catch (e: Exception) {
            log.error("Det skjedde en feil ved henting av aktiv enhet fra Modia: ${e.message}", e)
            throw RuntimeException("Noe feil skjedde ved henting av aktiv enhet fra Modia: ", e)
        }
    }
}

data class AktivEnhet(
    val aktivEnhet: String
)