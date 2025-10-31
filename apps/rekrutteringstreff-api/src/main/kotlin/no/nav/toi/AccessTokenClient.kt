package no.nav.toi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import no.nav.toi.SecureLogLogger.Companion.secure
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.*

class AccessTokenClient(
    private val secret: String,
    private val clientId: String,
    private val azureUrl: String,
    private val httpClient: HttpClient
) {
    private val objectMapper: ObjectMapper = JacksonConfig.mapper
    private val cache = CacheHjelper().lagCache { fetchAccessToken(it.token, it.scope).tilEntry() }

    fun hentAccessToken(innkommendeToken: String, scope: String) = cache.invoke(AccessTokenCacheKey(scope = scope, token = innkommendeToken)).access_token

    private fun fetchAccessToken(token: String, scope: String): AccessTokenResponse {
        fun fetch(): HttpResponse<String> {
            val formData = mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "client_secret" to secret,
                "client_id" to clientId,
                "assertion" to token,
                "scope" to scope,
                "requested_token_use" to "on_behalf_of"
            )

            val formBody = formData.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(azureUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build()

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val response = withRetry(::fetch)
        return if (response.statusCode() in 200..299) {
            objectMapper.readValue(response.body())
        } else {
            secure(log).error(
                "Noe feil skjedde ved henting av access_token. Status: ${response.statusCode()}, msg: ${response.body()}"
            )
            throw RuntimeException("Noe feil skjedde ved henting av access_token. Status: ${response.statusCode()}")
        }
    }

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
}


private data class AccessTokenResponse(
    val access_token: String,
    val expires_in: Long
) {
    fun tilEntry() = AccessTokenCacheEntry(access_token, Instant.now().plusSeconds(expires_in - 10))
}

private class AccessTokenCacheEntry(
    val access_token: String,
    private val expiry: Instant
) {
    fun erGåttUt() = Instant.now().isAfter(expiry)
}

private class AccessTokenCacheKey(
    val scope: String,
    val token: String
)

private class CacheHjelper {
    private val cacheKonfigurasjon = CacheConfigurationBuilder.newCacheConfigurationBuilder(
        AccessTokenCacheKey::class.java, AccessTokenCacheEntry::class.java,
        ResourcePoolsBuilder.heap(666)
    )
    private val cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache(
            "preConfiguredCache",
            cacheKonfigurasjon
        ).build().also(CacheManager::init)

    fun lagCache(getter: (AccessTokenCacheKey) -> AccessTokenCacheEntry): (AccessTokenCacheKey) -> AccessTokenCacheEntry =
        cacheManager.createCache(
            "cache${UUID.randomUUID()}",
            cacheKonfigurasjon
        ).let { cache ->
            { key ->
                if (!cache.containsKey(key) || cache.get(key).erGåttUt()) {
                    cache.put(key, getter(key))
                }
                cache.get(key)
            }
        }
}
