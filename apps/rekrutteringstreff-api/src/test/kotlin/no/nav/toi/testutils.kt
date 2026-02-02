package no.nav.toi

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.provider.Arguments
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object AzureAdRoller {
    val modiaGenerell: UUID = UUID.randomUUID()
    val jobbsøkerrettet: UUID = UUID.randomUUID()
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

val httpClient: HttpClient = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.ALWAYS)
    .build()

private const val minSideAudience = "rekrutteringstreff-minside-audience"

fun MockOAuth2Server.lagTokenBorger(
    authPort: Int,
    issuerId: String = "http://localhost:$authPort/default",
    pid: String = "11111111111",
    claims: Map<String, Any> = mapOf("pid" to pid),
    expiry: Long = 3600,
    audience: String = "rekrutteringstreff-audience"
) = issueToken(
    issuerId = issuerId,
    subject = "subject",
    claims = claims,
    expiry = expiry,
    audience = audience
)

/** Hjelpefunksjon for å sende HTTP-forespørsler i tester */
fun httpGet(url: String, token: String? = null): HttpResponse<String> {
    val builder = HttpRequest.newBuilder().uri(URI.create(url)).GET()
    token?.let { builder.header("Authorization", "Bearer $it") }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

fun httpPost(url: String, body: String = "", token: String? = null, contentType: String = "application/json"): HttpResponse<String> {
    val builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", contentType)
        .POST(HttpRequest.BodyPublishers.ofString(body))
    token?.let { builder.header("Authorization", "Bearer $it") }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

fun httpPut(url: String, body: String = "", token: String? = null, contentType: String = "application/json"): HttpResponse<String> {
    val builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", contentType)
        .PUT(HttpRequest.BodyPublishers.ofString(body))
    token?.let { builder.header("Authorization", "Bearer $it") }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

fun httpDelete(url: String, token: String? = null): HttpResponse<String> {
    val builder = HttpRequest.newBuilder().uri(URI.create(url)).DELETE()
    token?.let { builder.header("Authorization", "Bearer $it") }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

enum class UautentifiserendeTestCase(val leggPåToken: HttpRequest.Builder.(MockOAuth2Server, Int) -> HttpRequest.Builder) {
    UgyldigToken({ _, _ -> this.header("Authorization", "Bearer ugyldigtoken") }),
    IngenToken({ _, _ -> this }),
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