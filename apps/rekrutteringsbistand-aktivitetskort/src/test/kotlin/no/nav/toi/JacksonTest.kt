package no.nav.toi

import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.sun.net.httpserver.HttpServer
import no.nav.toi.aktivitetskort.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class JacksonTest {

    // Our JSON handling uses Jackson 3, but Auth0 still uses Jackson 2 transitively.
    // These compatibility tests were added when we raised that transitive Jackson 2 version
    // through the BOM: the existing application tests did not exercise Auth0's JSON handling.
    // They exercise the installed libraries, not an application authentication flow.
    // When upgrading Auth0 to Jackson 3, keep these tests for the upgrade: they check public
    // behavior, not Jackson 2 APIs. Check java-jwt and jwks-rsa separately; one may migrate first.
    // Update the Jackson-version comments after verifying the resolved dependencies, and adapt
    // library API calls only if necessary without weakening the assertions. Do not retain a
    // Jackson 2 dependency just for these tests. Remove them if the Auth0 libraries are removed,
    // or if equivalent tests cover these operations elsewhere.

    // Check that java-jwt can still write and read JSON headers/claims with the selected
    // Jackson 2 version, preserving escaped text and lists in a token that verifies successfully.
    @Test
    fun `should create decode and verify JWT with Auth0`() {
        val keys = rsaKeys()
        val token = signedToken(keys)

        val decoded = JWT.decode(token)
        assertThat(decoded.keyId).isEqualTo("test-key")
        assertThat(decoded.issuer).isEqualTo("test-issuer")
        assertThat(decoded.audience).containsExactly("test-audience")
        assertThat(decoded.getClaim("message").asString()).isEqualTo("Test \"message\"\nnext line")
        assertThat(decoded.getClaim("roles").asList(String::class.java)).containsExactly("reader", "writer")

        val verified = JWT.require(Algorithm.RSA256(keys.public as RSAPublicKey, null))
            .withIssuer("test-issuer")
            .withAudience("test-audience")
            .build()
            .verify(token)

        assertThat(verified.getClaim("message").asString()).isEqualTo("Test \"message\"\nnext line")
    }

    // Check that jwks-rsa can still parse a JSON key set into the correct usable public key.
    // The local HTTP fixture exercises Auth0's own JSON reader, not our Jackson 3 mapper.
    // Verifying a token with the fetched key checks more than successful JSON parsing.
    @Test
    fun `should fetch JWKS with Auth0 and verify JWT using returned key`() {
        val keys = rsaKeys()
        val publicKey = keys.public as RSAPublicKey
        val jwks = """{"keys":[{
            "kty":"RSA","kid":"test-key","use":"sig","alg":"RS256",
            "n":"${base64UrlUnsigned(publicKey.modulus)}",
            "e":"${base64UrlUnsigned(publicKey.publicExponent)}"
        }]}""".toByteArray(Charsets.UTF_8)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/jwks") { exchange ->
                exchange.use {
                    it.responseHeaders.set("Content-Type", "application/json")
                    it.sendResponseHeaders(200, jwks.size.toLong())
                    it.responseBody.write(jwks)
                }
            }
            server.start()
            val provider = UrlJwkProvider(
                URI.create("http://127.0.0.1:${server.address.port}/jwks").toURL(),
                2000, 2000, Proxy.NO_PROXY
            )

            val jwk = provider.get("test-key")

            assertThat(jwk.id).isEqualTo("test-key")
            assertThat(jwk.publicKey).isEqualTo(publicKey)
            val verified = JWT.require(Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null))
                .withIssuer("test-issuer")
                .withAudience("test-audience")
                .build()
                .verify(signedToken(keys))
            assertThat(verified.getClaim("roles").asList(String::class.java)).containsExactly("reader", "writer")
        } finally {
            server.stop(0)
        }
    }

    // A readable token is not necessarily trustworthy. This negative control checks that
    // successful JSON decoding still leads to rejection when the signature uses another key.
    // It supports the compatibility checks above; it is not itself a Jackson parsing test.
    @Test
    fun `should reject JWT signed by a different key`() {
        val trustedKeys = rsaKeys()
        val token = signedToken(rsaKeys())
        val verifier = JWT.require(Algorithm.RSA256(trustedKeys.public as RSAPublicKey, null))
            .withIssuer("test-issuer")
            .withAudience("test-audience")
            .build()

        assertThatThrownBy { verifier.verify(token) }
            .isInstanceOf(SignatureVerificationException::class.java)
    }

    private fun rsaKeys(): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    private fun signedToken(keys: KeyPair): String = JWT.create()
        .withKeyId("test-key")
        .withIssuer("test-issuer")
        .withAudience("test-audience")
        .withClaim("message", "Test \"message\"\nnext line")
        .withArrayClaim("roles", arrayOf("reader", "writer"))
        .sign(Algorithm.RSA256(keys.public as RSAPublicKey, keys.private as RSAPrivateKey))

    private fun base64UrlUnsigned(value: BigInteger): String {
        val bytes = value.toByteArray()
        val unsignedBytes = if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsignedBytes)
    }

    @Test
    fun `should deserialize an error queue message`() {
        val json = """
            {
                "source": "REKRUTTERINGSBISTAND",
                "failingMessage": "{\"messageId\":\"00000000-0000-0000-0000-000000000001\"}",
                "errorMessage": "Activity not found",
                "errorType": "AKTIVITET_IKKE_FUNNET"
            }
        """.trimIndent()

        val event = jackson3ObjectMapper.readValue(json, FeilKøHendelse::class.java)

        assertKnownFields(event)
    }

    @Test
    fun `should ignore unknown fields in an error queue message`() {
        val json = """
            {
                "source": "REKRUTTERINGSBISTAND",
                "failingMessage": "{\"messageId\":\"00000000-0000-0000-0000-000000000001\"}",
                "errorMessage": "Activity not found",
                "errorType": "AKTIVITET_IKKE_FUNNET",
                "additionalField": "ignored"
            }
        """.trimIndent()

        val event = jackson3ObjectMapper.readValue(json, FeilKøHendelse::class.java)

        assertKnownFields(event)
    }

    @Test
    fun `should read elector name and ignore unknown fields`() {
        val json = """
            {
                "name": "activity-card-0",
                "additionalField": "ignored"
            }
        """.trimIndent()

        val name = parseElectorName(json)

        assertThat(name).isEqualTo("activity-card-0")
    }

    @Test
    fun `should read stored details in order and ignore unknown fields`() {
        val details = AktivitetskortDetalj.fraAkaasJson(
            """[
                {"label":"Sted","verdi":"Teststed","additionalField":"ignored"},
                {"label":"Tid","verdi":"10:00"}
            ]"""
        )

        assertThat(details).hasSize(2)
        assertThat(details.map { it.label }).containsExactly("Sted", "Tid")
        assertThat(details.map { it.verdi }).containsExactly("Teststed", "10:00")
    }

    @Test
    fun `should read stored actions and link types`() {
        val actions = AktivitetskortHandling.fraAkaasJson(
            """[
                {"tekst":"First","subtekst":"First detail","url":"https://example.com/1","lenkeType":"FELLES"},
                {"tekst":"Second","subtekst":"Second detail","url":"https://example.com/2","lenkeType":"INTERN"},
                {"tekst":"Third","subtekst":"Third detail","url":"https://example.com/3","lenkeType":"EKSTERN"}
            ]"""
        )

        assertThat(actions).hasSize(3)
        assertThat(actions.map { it.tekst }).containsExactly("First", "Second", "Third")
        assertThat(actions.map { it.subtekst }).containsExactly("First detail", "Second detail", "Third detail")
        assertThat(actions.map { it.url }).containsExactly(
            "https://example.com/1", "https://example.com/2", "https://example.com/3"
        )
        assertThat(actions.map { it.lenkeType }).containsExactly(LenkeType.FELLES, LenkeType.INTERN, LenkeType.EKSTERN)
    }

    @Test
    fun `should read stored labels with present missing and null code`() {
        val labels = AktivitetskortEtikett.fraAkaasJson(
            """[
                {"tekst":"Waiting","sentiment":"NEUTRAL"},
                {"tekst":"Accepted","sentiment":"POSITIVE","kode":"ACCEPTED"},
                {"tekst":"Declined","sentiment":"NEGATIVE","kode":null}
            ]"""
        )

        assertThat(labels).hasSize(3)
        assertThat(labels.map { it.tekst }).containsExactly("Waiting", "Accepted", "Declined")
        assertThat(labels.map { it.sentiment }).containsExactly(Sentiment.NEUTRAL, Sentiment.POSITIVE, Sentiment.NEGATIVE)
        assertThat(labels.map { it.kode }).containsExactly(null, "ACCEPTED", null)
    }

    @Test
    fun `should read stored task sides and ignore unknown fields`() {
        val task = AktivitetskortOppgave.fraAkaasJson(
            """{
                "ekstern":{"tekst":"External","subtekst":"External detail","url":"https://example.com/external"},
                "intern":{"tekst":"Internal","subtekst":"Internal detail","url":"https://example.com/internal"},
                "additionalField":"ignored"
            }"""
        )

        val external = requireNotNull(task.ekstern)
        assertThat(external.tekst).isEqualTo("External")
        assertThat(external.subtekst).isEqualTo("External detail")
        assertThat(external.url).isEqualTo("https://example.com/external")
        val internal = requireNotNull(task.intern)
        assertThat(internal.tekst).isEqualTo("Internal")
        assertThat(internal.subtekst).isEqualTo("Internal detail")
        assertThat(internal.url).isEqualTo("https://example.com/internal")
    }

    @Test
    fun `should read stored task with missing or null optional side`() {
        val missingInternal = AktivitetskortOppgave.fraAkaasJson(
            """{"ekstern":{"tekst":"External","subtekst":"Detail","url":"https://example.com"}}"""
        )
        val nullExternal = AktivitetskortOppgave.fraAkaasJson(
            """{"ekstern":null,"intern":{"tekst":"Internal","subtekst":"Detail","url":"https://example.com"}}"""
        )

        assertThat(missingInternal.intern).isNull()
        assertThat(requireNotNull(missingInternal.ekstern).tekst).isEqualTo("External")
        assertThat(nullExternal.ekstern).isNull()
        assertThat(requireNotNull(nullExternal.intern).tekst).isEqualTo("Internal")
    }

    @Test
    fun `should read empty stored lists`() {
        assertThat(AktivitetskortDetalj.fraAkaasJson("[]")).isEmpty()
        assertThat(AktivitetskortHandling.fraAkaasJson("[]")).isEmpty()
        assertThat(AktivitetskortEtikett.fraAkaasJson("[]")).isEmpty()
    }

    @Test
    fun `should write stored details with escaped text and preserve order`() {
        val details = listOf(
            AktivitetskortDetalj("Tid", "10:00"),
            AktivitetskortDetalj("Sted", "Test \"location\"\nEntrance\\A")
        )
        val expected = jackson3ObjectMapper.readTree(
            """[
                {"label":"Tid","verdi":"10:00"},
                {"label":"Sted","verdi":"Test \"location\"\nEntrance\\A"}
            ]"""
        )

        val json = jackson3ObjectMapper.writeValueAsString(details)

        assertThat(jackson3ObjectMapper.readTree(json)).isEqualTo(expected)
    }

    @Test
    fun `should write stored actions with enum names`() {
        val actions = listOf(
            AktivitetskortHandling("Open", "View details", "https://example.com/card", LenkeType.FELLES)
        )
        val expected = jackson3ObjectMapper.readTree(
            """[{"tekst":"Open","subtekst":"View details","url":"https://example.com/card","lenkeType":"FELLES"}]"""
        )

        val json = jackson3ObjectMapper.writeValueAsString(actions)

        assertThat(jackson3ObjectMapper.readTree(json)).isEqualTo(expected)
    }

    @Test
    fun `should write stored labels with enum names and omit null code`() {
        val labels = listOf(
            AktivitetskortEtikett("Waiting", Sentiment.NEUTRAL),
            AktivitetskortEtikett("Accepted", Sentiment.POSITIVE, "ACCEPTED"),
            AktivitetskortEtikett("Declined", Sentiment.NEGATIVE)
        )
        val expected = jackson3ObjectMapper.readTree(
            """[
                {"tekst":"Waiting","sentiment":"NEUTRAL"},
                {"tekst":"Accepted","sentiment":"POSITIVE","kode":"ACCEPTED"},
                {"tekst":"Declined","sentiment":"NEGATIVE"}
            ]"""
        )

        val json = jackson3ObjectMapper.writeValueAsString(labels)

        assertThat(jackson3ObjectMapper.readTree(json)).isEqualTo(expected)
    }

    @Test
    fun `should write empty stored label list`() {
        val json = jackson3ObjectMapper.writeValueAsString(emptyList<AktivitetskortEtikett>())

        assertThat(jackson3ObjectMapper.readTree(json)).isEqualTo(jackson3ObjectMapper.readTree("[]"))
    }

    private fun assertKnownFields(event: FeilKøHendelse) {
        assertThat(event.source).isEqualTo("REKRUTTERINGSBISTAND")
        assertThat(event.failingMessage).isEqualTo("""{"messageId":"00000000-0000-0000-0000-000000000001"}""")
        assertThat(event.errorMessage).isEqualTo("Activity not found")
        assertThat(event.errorType).isEqualTo(ErrorType.AKTIVITET_IKKE_FUNNET)
    }
}
