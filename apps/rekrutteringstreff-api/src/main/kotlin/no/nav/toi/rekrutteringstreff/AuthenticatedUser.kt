package no.nav.toi.rekrutteringstreff

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import org.eclipse.jetty.http.HttpHeader
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

private const val NAV_IDENT_CLAIM = "NAVident"

data class AuthenticationConfiguration(
    val issuer: String,
    val jwksUri: String,
    val audience: String
)

data class AuthenticatedUser(
    val navIdent: String,
    val jwt: String
) {
    companion object {
        fun fromJwt(jwt: DecodedJWT): AuthenticatedUser {
            val navIdent = jwt.getClaim(NAV_IDENT_CLAIM).asString()
                ?: throw UnauthorizedResponse("Missing claim: $NAV_IDENT_CLAIM")
            return AuthenticatedUser(navIdent, jwt.token)
        }
    }
}

fun Context.extractNavIdent(): String =
    attribute<AuthenticatedUser>("authenticatedUser")?.navIdent ?: throw UnauthorizedResponse("Not authenticated")

fun Javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(authConfigs: List<AuthenticationConfiguration>): Javalin {
    val verifiers = authConfigs.map { jwtVerifier(it) }
    before("/api/rekrutteringstreff") { ctx ->
        val token = ctx.header(HttpHeader.AUTHORIZATION.name)?.removePrefix("Bearer ")?.trim()
            ?: throw UnauthorizedResponse("Missing token")
        val decoded = verifyJwt(verifiers, token)
        ctx.attribute("authenticatedUser", AuthenticatedUser.fromJwt(decoded))
    }
    return this
}

private fun verifyJwt(verifiers: List<JWTVerifier>, token: String): DecodedJWT {
    for (verifier in verifiers) {
        try {
            return verifier.verify(token)
        } catch (e: JWTVerificationException) {
            // prøv neste verifier
        }
    }
    throw UnauthorizedResponse("Token verification failed")
}

private fun jwtVerifier(config: AuthenticationConfiguration): JWTVerifier {
    val algorithm = algorithm(config.jwksUri)
    return JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .withClaimPresence(NAV_IDENT_CLAIM)
        .build()
}

private fun algorithm(jwksUri: String): Algorithm {
    val jwkProvider = JwkProviderBuilder(URI(jwksUri).toURL())
        .cached(10, 1, TimeUnit.HOURS)
        .rateLimited(false)
        .build()
    return Algorithm.RSA256(object : RSAKeyProvider {
        override fun getPublicKeyById(keyId: String): RSAPublicKey =
            jwkProvider.get(keyId).publicKey as RSAPublicKey
        override fun getPrivateKey() = throw UnsupportedOperationException()
        override fun getPrivateKeyId() = throw UnsupportedOperationException()
    })
}
