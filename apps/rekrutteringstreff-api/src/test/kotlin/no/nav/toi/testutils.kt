package no.nav.toi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.provider.Arguments
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

val mapper: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}
