package no.nav.toi.minside

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.javalin.Javalin
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.minside.AuthenticatedUser.Companion.extractIdent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.ServerSocket
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Låser hvilke tokens autentiseringsfilteret i minside godtar og avviser. Kjører filteret alene, uten database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutentiseringFilterTest {

    private val authPort = ledigPort()
    private val appPort = ledigPort()
    private val authServer = MockOAuth2Server()
    private val audience = "minside-audience"
    private val httpClient = HttpClient.newHttpClient()
    private lateinit var javalin: Javalin

    private val url = "http://localhost:$appPort/api/rekrutteringstreff/hvem"
    private val issuer get() = "http://localhost:$authPort/tokenx"

    @BeforeAll
    fun setUp() {
        authServer.start(port = authPort)
        javalin = Javalin.create { config ->
            config.routes.apply {
                leggTilAutensieringPåRekrutteringstreffEndepunkt(
                    listOf(AuthenticationConfiguration(issuer = issuer, jwksUri = "$issuer/jwks", audience = audience))
                )
                get("/api/rekrutteringstreff/hvem") { ctx -> ctx.result("borger:${ctx.extractIdent()}") }
            }
        }.start(appPort)
    }

    @AfterAll
    fun tearDown() {
        javalin.stop()
        authServer.shutdown()
    }

    private fun ledigPort() = ServerSocket(0).use { it.localPort }

    private fun get(token: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create(url)).GET()
        token?.let { builder.header("Authorization", "Bearer $it") }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun token(claims: Map<String, Any> = mapOf("pid" to "12345678910"), issuerId: String = "tokenx", aud: String = audience, expiry: Long = 3600) =
        authServer.issueToken(issuerId = issuerId, subject = "subject", audience = aud, claims = claims, expiry = expiry)

    private fun claimsSet() = JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(audience)
        .subject("subject")
        .issueTime(Date.from(Instant.now()))
        .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
        .claim("pid", "12345678910")
        .build()

    @Test
    fun `gyldig TokenX-token gir borger`() {
        val respons = get(token().serialize())
        assertThat(respons.statusCode()).isEqualTo(HTTP_OK)
        assertThat(respons.body()).isEqualTo("borger:12345678910")
    }

    @Test
    fun `manglende og ugyldig token gir 401`() {
        assertThat(get(null).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
        assertThat(get("abc.def.ghi").statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
        assertThat(get("ikke-et-token").statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `token uten pid gir 401`() {
        assertThat(get(token(claims = emptyMap()).serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `feil issuer, audience eller utløpt token gir 401`() {
        assertThat(get(token(issuerId = "annen").serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
        assertThat(get(token(aud = "feil").serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
        assertThat(get(token(expiry = -3600).serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `nbf langt fram i tid gir 401`() {
        val t = token(claims = mapOf("pid" to "12345678910", "nbf" to Instant.now().plusSeconds(3600).epochSecond))
        assertThat(t.jwtClaimsSet.notBeforeTime.toInstant()).isAfter(Instant.now().plusSeconds(600))
        assertThat(get(t.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `alg none og HS256 gir 401`() {
        val payload = token().serialize().split(".")[1]
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        assertThat(get("$header.$payload.").statusCode()).isEqualTo(HTTP_UNAUTHORIZED)

        val hs = SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claimsSet())
        hs.sign(MACSigner(ByteArray(64) { it.toByte() }))
        assertThat(get(hs.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `forfalsket signatur og ukjent kid gir 401`() {
        val kid = token().header.keyID
        listOf(kid, "ukjent-kid").forEach { k ->
            val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(k).type(JOSEObjectType.JWT).build(), claimsSet())
            jwt.sign(RSASSASigner(RSAKeyGenerator(2048).keyID(k).generate()))
            assertThat(get(jwt.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
        }
    }
}
