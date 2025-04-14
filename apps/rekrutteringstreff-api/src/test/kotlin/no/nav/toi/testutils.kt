package no.nav.toi

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.provider.Arguments
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Denne funksjonens eksistensberettigelse er å få kastet den underliggende exception når et HTTP kall har feilet uten
 * at vi har fått noen responsstatuskode, f.eks. java.net.SocketException: Unexpected end of file from server */
fun assertStatuscodeEquals(
    expectedStatuscode: Int,
    actualResponse: Response,
    actualResult: Result<*, FuelError>
) {
    when (actualResult) {
        is Success -> assertThat(actualResponse.statusCode).isEqualTo(expectedStatuscode)
        is Failure -> if (actualResponse.statusCode == -1) {
            throw actualResult.error
        } else {
            assertThat(actualResponse.statusCode).isEqualTo(expectedStatuscode)
        }
    }
}

object AzureAdRoller {
    val modiaGenerell: UUID = UUID.randomUUID()
    val arbeidsgiverrettet: UUID = UUID.randomUUID()
    val utvikler: UUID = UUID.randomUUID()
}

fun MockOAuth2Server.lagToken(
    authPort: Int,
    issuerId: String = "http://localhost:$authPort/default",
    navIdent: String = "A000001",
    groups: List<UUID> = listOf(arbeidsgiverrettet),
    claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to groups.map(UUID::toString)),
    expiry: Long = 3600,
    audience: String = "rekrutteringstreff-audience"
) = issueToken(
    issuerId = issuerId,
    subject = "subject",
    claims = claims,
    expiry = expiry,
    audience = audience
)


enum class UautentifiserendeTestCase(val leggPåToken: Request.(MockOAuth2Server, Int) -> Request) {
    UgyldigToken({ authServer, authPort -> this.header("Authorization", "Bearer ugyldigtoken") }),
    IngenToken({ authServer, authPort -> this }),
    UgyldigIssuer({ authServer, authPort ->
        this.header(
            "Authorization",
            "Bearer ${authServer.lagToken(authPort, issuerId = "http://localhost:12345/default").serialize()}"
        )
    }),
    UgyldigAudience({ authServer, authPort ->
        this.header(
            "Authorization",
            "Bearer ${authServer.lagToken(authPort, audience = "ugyldig-audience").serialize()}"
        )
    }),
    UtgåttToken({ authServer, authPort ->
        this.header("Authorization", "Bearer ${authServer.lagToken(authPort, expiry = -1).serialize()}")
    }),
    ManglendeNavIdent({ authServer, authPort ->
        this.header("Authorization", "Bearer ${authServer.lagToken(authPort, claims = emptyMap()).serialize()}")
    }),
    NoneAlgoritme({ authServer, authPort ->
        this.header(
            "Authorization",
            "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJub25lIn0.${authServer.lagToken(authPort).serialize().split(".")[1]}."
        )
    });

    companion object {
        fun somStrømAvArgumenter() = entries.map { Arguments.of(it) }.stream()
    }
}


object ubruktPortnrFra10000 {
    private val portnr = AtomicInteger(10000)
    fun ubruktPortnr(): Int = portnr.andIncrement
}