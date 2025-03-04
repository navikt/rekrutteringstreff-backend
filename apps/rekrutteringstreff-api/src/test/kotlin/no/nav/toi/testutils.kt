package no.nav.toi

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat

/**
 * Denne funksjonens eksistensberettigelse er å få kastet den underliggende exception når et HTTP kall har feilet uten
 * at vi har fått noen responsstatuskode, f.eks. java.net.SocketException: Unexpected end of file from server */
fun assertStatuscodeEquals(
    expectedStatuscode: Int,
    actualResponse: Response,
    actualResult: Result<*, FuelError>
) {
    when (actualResult) {
        is Success -> assertThat(expectedStatuscode).isEqualTo(actualResponse.statusCode)
        is Failure -> if (actualResponse.statusCode == -1) {
            throw actualResult.error
        } else {
            assertThat(expectedStatuscode).isEqualTo(actualResponse.statusCode)
        }
    }
}
fun MockOAuth2Server.lagToken(
    authPort: Int,
    issuerId: String = "http://localhost:$authPort/default",
    navIdent: String = "A000001",
    claims: Map<String, Any> = mapOf("NAVident" to navIdent),
    expiry: Long = 3600,
    audience: String = "rekrutteringstreff-audience"
) = issueToken(
    issuerId = issuerId,
    subject = "subject",
    claims = claims,
    expiry = expiry,
    audience = audience
)