package no.nav.toi

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import java.time.Instant
import java.util.*


class AccessTokenClient(
    private val secret: String,
    private val clientId: String,
    private val scope: String,
    private val azureUrl: String,
) {
    private val cache = CacheHjelper().lagCache { fetchAccessToken(it).tilEntry() }
    fun hentAccessToken(innkommendeToken: String) = cache.invoke(innkommendeToken).access_token

    private fun fetchAccessToken(token: String): AccessTokenResponse {
        fun fetch(): Triple<Request, Response, Result<AccessTokenResponse, FuelError>> {
            val formData = listOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "client_secret" to secret,
                "client_id" to clientId,
                "assertion" to token,
                "scope" to scope,
                "requested_token_use" to "on_behalf_of"
            )
            return FuelManager().post(azureUrl, formData).responseObject<AccessTokenResponse>()
        }

        val (_, response, result) = withRetry(::fetch)
        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> {
                /*secureLog.error(
                    "Noe feil skjedde ved henting av access_token. msg: ${
                        response.body().asString("application/json")
                    }", result.getException()
                )*/

                throw RuntimeException("Noe feil skjedde ved henting av access_token: ", result.getException())
            }
        }
    }

    companion object {
        private fun withRetry(fetch: () -> Triple<Request, Response, Result<AccessTokenResponse, FuelError>>): Triple<Request, Response, Result<AccessTokenResponse, FuelError>> {
            fun isFailure(t: Triple<Request, Response, Result<Any, Exception>>) = t.third is Result.Failure
            val retryConfig =
                RetryConfig.custom<Triple<Request, Response, Result<Any, Exception>>>()
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


private class CacheHjelper {
    private val cacheKonfigurasjon = CacheConfigurationBuilder.newCacheConfigurationBuilder(
        String::class.java, AccessTokenCacheEntry::class.java,
        ResourcePoolsBuilder.heap(666)
    )
    private val cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache(
            "preConfiguredCache",
            cacheKonfigurasjon
        ).build().also(CacheManager::init)

    fun lagCache(getter: (String) -> AccessTokenCacheEntry): (String) -> AccessTokenCacheEntry =
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
