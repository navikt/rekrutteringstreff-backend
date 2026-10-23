package no.nav.toi

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
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Låser hvilke tokens autentiseringsfilteret godtar og avviser, og hvilken brukertype et gyldig token gir.
 * Kjører filteret alene i en Javalin uten database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutentiseringFilterTest {

    private val authPort = ubruktPortnr()
    private val appPort = ubruktPortnr()
    private val authServer = MockOAuth2Server()
    private val audience = "rekrutteringstreff-audience"
    private lateinit var javalin: Javalin

    private val url = "http://localhost:$appPort/api/rekrutteringstreff/hvem"

    private val rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
        jobbsøkerrettet = AzureAdRoller.jobbsøkerrettet,
        arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
        utvikler = AzureAdRoller.utvikler,
        modiaGenerell = AzureAdRoller.modiaGenerell,
        modiaOppfølging = AzureAdRoller.modiaOppfølging,
    )

    @BeforeAll
    fun setUp() {
        authServer.start(port = authPort)
        val accessTokenClient = AccessTokenClient(clientId = "test", secret = "test", azureUrl = "", httpClient = httpClient)
        javalin = Javalin.create { config ->
            config.routes.apply {
                leggTilAutensieringPåRekrutteringstreffEndepunkt(
                    authConfigs = authConfigs(),
                    rolleUuidSpesifikasjon = rolleUuidSpesifikasjon,
                    modiaKlient = ModiaKlient("", "", accessTokenClient, httpClient),
                )
                get("/api/rekrutteringstreff/hvem") { ctx ->
                    val bruker = ctx.authenticatedUser()
                    ctx.result(
                        if (bruker.erNavAnsatt) "nav:${bruker.extractNavIdent()}:utvikler=${bruker.erUtvikler()}"
                        else "borger:${bruker.extractPid()}"
                    )
                }
            }
        }.start(appPort)
    }

    @AfterAll
    fun tearDown() {
        javalin.stop()
        authServer.shutdown()
    }

    private fun authConfigs() = listOf(
        AuthenticationConfiguration(
            issuer = "http://localhost:$authPort/azure",
            jwksUri = "http://localhost:$authPort/azure/jwks",
            audience = audience,
            issuerType = IssuerType.AZURE_AD,
        ),
        AuthenticationConfiguration(
            issuer = "http://localhost:$authPort/tokenx",
            jwksUri = "http://localhost:$authPort/tokenx/jwks",
            audience = audience,
            issuerType = IssuerType.TOKEN_X,
        ),
    )

    private fun azureToken(claims: Map<String, Any>, expiry: Long = 3600, aud: String = audience) =
        authServer.issueToken(issuerId = "azure", subject = "subject", audience = aud, claims = claims, expiry = expiry)

    private fun tokenxToken(claims: Map<String, Any>) =
        authServer.issueToken(issuerId = "tokenx", subject = "subject", audience = audience, claims = claims, expiry = 3600)

    private val navClaims = mapOf("NAVident" to "A000001", "groups" to listOf(AzureAdRoller.utvikler.toString()))

    private fun claimsSet(issuer: String) = JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(audience)
        .subject("subject")
        .issueTime(Date.from(Instant.now()))
        .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
        .claim("NAVident", "A000001")
        .claim("groups", listOf(AzureAdRoller.utvikler.toString()))
        .build()

    @Test
    fun `gyldig Azure-token med NAVident gir Nav-ansatt`() {
        val respons = httpGet(url, azureToken(navClaims).serialize())
        assertThat(respons.statusCode()).isEqualTo(HTTP_OK)
        assertThat(respons.body()).isEqualTo("nav:A000001:utvikler=true")
    }

    @Test
    fun `gyldig TokenX-token med pid gir borger`() {
        val respons = httpGet(url, tokenxToken(mapOf("pid" to "12345678910")).serialize())
        assertThat(respons.statusCode()).isEqualTo(HTTP_OK)
        assertThat(respons.body()).isEqualTo("borger:12345678910")
    }

    @Test
    fun `manglende token gir 401`() {
        assertThat(httpGet(url).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `token som ikke er en JWT gir 401`() {
        assertThat(httpGet(url, "abc.def.ghi").statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
        assertThat(httpGet(url, "ikke-et-token").statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `ukjent issuer gir 401`() {
        val token = authServer.issueToken(issuerId = "annen", subject = "subject", audience = audience, claims = navClaims)
        assertThat(httpGet(url, token.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `feil audience gir 401`() {
        assertThat(httpGet(url, azureToken(navClaims, aud = "feil").serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `utløpt token gir 401`() {
        assertThat(httpGet(url, azureToken(navClaims, expiry = -3600).serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `nbf langt fram i tid gir 401`() {
        val token = azureToken(navClaims + ("nbf" to Instant.now().plusSeconds(3600).epochSecond))
        assertThat(token.jwtClaimsSet.notBeforeTime.toInstant()).isAfter(Instant.now().plusSeconds(600))
        assertThat(httpGet(url, token.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `alg none gir 401`() {
        val gyldig = azureToken(navClaims).serialize().split(".")
        val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        assertThat(httpGet(url, "$header.${gyldig[1]}.").statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `HS256-signert token gir 401`() {
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claimsSet("http://localhost:$authPort/azure"))
        jwt.sign(MACSigner(ByteArray(64) { it.toByte() }))
        assertThat(httpGet(url, jwt.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `token signert med annen nøkkel men samme kid gir 401`() {
        val kid = azureToken(navClaims).header.keyID
        val falskNøkkel = RSAKeyGenerator(2048).keyID(kid).generate()
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).type(JOSEObjectType.JWT).build(),
            claimsSet("http://localhost:$authPort/azure"),
        )
        jwt.sign(RSASSASigner(falskNøkkel))
        assertThat(httpGet(url, jwt.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `token med ukjent kid gir 401`() {
        val ukjentNøkkel = RSAKeyGenerator(2048).keyID("ukjent-kid").generate()
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID("ukjent-kid").type(JOSEObjectType.JWT).build(),
            claimsSet("http://localhost:$authPort/azure"),
        )
        jwt.sign(RSASSASigner(ukjentNøkkel))
        assertThat(httpGet(url, jwt.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `Azure-token uten NAVident og pid gir 401`() {
        assertThat(httpGet(url, azureToken(mapOf("groups" to listOf<String>())).serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `TokenX-token uten pid gir 401`() {
        assertThat(httpGet(url, tokenxToken(emptyMap()).serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `Azure-token med pid men uten NAVident gir 401`() {
        assertThat(httpGet(url, azureToken(mapOf("pid" to "12345678910")).serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `TokenX-token med NAVident og groups gir borger, ikke Nav-ansatt`() {
        val respons = httpGet(url, tokenxToken(navClaims + ("pid" to "12345678910")).serialize())
        assertThat(respons.statusCode()).isEqualTo(HTTP_OK)
        assertThat(respons.body()).isEqualTo("borger:12345678910")
    }

    @Test
    fun `TokenX-token med NAVident men uten pid gir 401`() {
        assertThat(httpGet(url, tokenxToken(navClaims).serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `Azure-token uten groups gir Nav-ansatt uten roller`() {
        val respons = httpGet(url, azureToken(mapOf("NAVident" to "A000001")).serialize())
        assertThat(respons.statusCode()).isEqualTo(HTTP_OK)
        assertThat(respons.body()).isEqualTo("nav:A000001:utvikler=false")
    }

    @Test
    fun `groups med ugyldig UUID gir 401`() {
        val token = azureToken(mapOf("NAVident" to "A000001", "groups" to listOf("ikke-en-uuid")))
        assertThat(httpGet(url, token.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `groups som ikke er en liste gir 401`() {
        val token = azureToken(mapOf("NAVident" to "A000001", "groups" to AzureAdRoller.utvikler.toString()))
        assertThat(httpGet(url, token.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `NAVident som ikke er en streng gir 401`() {
        val token = azureToken(mapOf("NAVident" to 123, "groups" to listOf(AzureAdRoller.utvikler.toString())))
        assertThat(httpGet(url, token.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun `gjentatte tokens med ukjent kid gir høyst én ny JWKS-henting`() {
        assertThat(httpGet(url, azureToken(navClaims).serialize()).statusCode()).isEqualTo(HTTP_OK)
        jwksHentinger()

        val ukjentNøkkel = RSAKeyGenerator(2048).keyID("rate-limit-kid").generate()
        repeat(5) {
            val jwt = SignedJWT(
                JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rate-limit-kid").type(JOSEObjectType.JWT).build(),
                claimsSet("http://localhost:$authPort/azure"),
            )
            jwt.sign(RSASSASigner(ukjentNøkkel))
            assertThat(httpGet(url, jwt.serialize()).statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
        }

        assertThat(jwksHentinger()).isLessThanOrEqualTo(1)
    }

    /** Tømmer køen av forespørsler mot mock-oauth2-server og teller JWKS-hentinger for Azure-issueren. */
    private fun jwksHentinger(): Int {
        var antall = 0
        while (true) {
            val forespørsel = runCatching { authServer.takeRequest(200, TimeUnit.MILLISECONDS) }.getOrNull() ?: return antall
            if (forespørsel.path == "/azure/jwks") antall++
        }
    }
}

class FakedingsKonfigurasjonTest {
    private val env = mapOf(
        "AZURE_APP_CLIENT_ID" to "azure-klient",
        "AZURE_OPENID_CONFIG_ISSUER" to "https://azure.example/issuer",
        "AZURE_OPENID_CONFIG_JWKS_URI" to "https://azure.example/jwks",
        "TOKEN_X_CLIENT_ID" to "tokenx-klient",
        "TOKEN_X_ISSUER" to "https://tokenx.example/issuer",
        "TOKEN_X_JWKS_URI" to "https://tokenx.example/jwks",
    )

    @Test
    fun `Fakedings er bare med i dev-gcp`() {
        assertThat(InfrastructureContext(env + ("NAIS_CLUSTER_NAME" to "dev-gcp")).authConfigs.map { it.issuerType })
            .containsExactly(IssuerType.AZURE_AD, IssuerType.TOKEN_X, IssuerType.FAKEDINGS)
        assertThat(InfrastructureContext(env + ("NAIS_CLUSTER_NAME" to "prod-gcp")).authConfigs.map { it.issuerType })
            .containsExactly(IssuerType.AZURE_AD, IssuerType.TOKEN_X)
        assertThat(InfrastructureContext(env).authConfigs.map { it.issuerType })
            .containsExactly(IssuerType.AZURE_AD, IssuerType.TOKEN_X)
    }
}
