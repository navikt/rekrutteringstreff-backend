package no.nav.toi.rekrutteringstreff.tilgangsstyring

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import no.nav.toi.AccessTokenClient
import no.nav.toi.JacksonConfig
import no.nav.toi.exception.ModiaOppslagFeiletException
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

    /**
     * Henter alle Nav-enheter (orgenheter) som innlogget veileder er tilknyttet
     * via modiacontextholder `/api/decorator`. Brukes til tilgangsstyring på
     * jobbsøkere som er tilknyttet samme kontor som veileder.
     *
    * Returnerer tom liste hvis veileder ikke er tilknyttet noen enheter.
     */
    fun hentMineEnheter(innkommendeToken: String): List<String> {
        return try {
            val modiaUrl = "$modiaContextHolderUrl/api/decorator"
            val accessToken = accessTokenClient.hentAccessToken(innkommendeToken = innkommendeToken, scope = modiaContextHolderScope)

            fun fetch(): HttpResponse<String> {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(modiaUrl))
                    .header("Authorization", "Bearer $accessToken")
                    .GET()
                    .build()
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }

            val respons = withRetry(::fetch)
            if (respons.statusCode() in 200..299) {
                objectMapper.readValue(respons.body(), ModiaPerson::class.java)
                    .enheter
                    .map(ModiaEnhet::enhetId)
            } else if (respons.statusCode() == 404) {
                emptyList()
            } else {
                log.error("Feil ved henting av Modia-enheter. Status: ${respons.statusCode()}, body: ${respons.body()}")
                throw ModiaOppslagFeiletException("Klarte ikke å hente Modia-enheter. Prøv igjen senere.")
            }
        } catch (e: Exception) {
            if (e is ModiaOppslagFeiletException) {
                throw e
            }
            log.error("Det skjedde en feil ved henting av Modia-enheter: ${e.message}", e)
            throw ModiaOppslagFeiletException("Klarte ikke å hente Modia-enheter. Prøv igjen senere.", e)
        }
    }
}

data class AktivEnhet(
    val aktivEnhet: String
)

internal data class ModiaPerson(
    val enheter: List<ModiaEnhet> = emptyList()
)

internal data class ModiaEnhet(
    val enhetId: String,
)