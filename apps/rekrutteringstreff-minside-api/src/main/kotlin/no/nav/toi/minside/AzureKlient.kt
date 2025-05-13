package no.nav.arbeid.cv.felles.token

import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.responseObject
import no.nav.toi.minside.log

class AzureKlient(
    private val clientId: String,
    private val clientSecret: String,
    private val tokenEndpoint: String,
    private val scope: String
) {
    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    fun onBehalfOfToken(innkommendeToken: String) = try {
        tokenEndpoint.httpPost(
            parameters = listOf(
                "grant_type" to AZURE_ON_BEHALF_OF_GRANT_TYPE,
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "assertion" to innkommendeToken,
                "scope" to scope,
                "requested_token_use" to REQUESTED_TOKEN_USE
            )
        ).responseObject<TokenResponse>().third.get().access_token
    } catch (e: Exception) {
        log.error("feil fra azure: ${e.message}")
        throw e
    }
}

private class TokenResponse(
    val access_token: String,
    val expires_in: Int,
)