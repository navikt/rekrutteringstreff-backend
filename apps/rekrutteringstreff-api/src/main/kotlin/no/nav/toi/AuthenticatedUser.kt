package no.nav.toi

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import org.eclipse.jetty.http.HttpHeader
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.TimeUnit

private const val NAV_IDENT_CLAIM = "NAVident"
private const val PID_CLAIM = "pid"

private val log = noClassLogger()

class AuthenticationConfiguration(
    private val issuer: String,
    private val jwksUri: String,
    private val audience: String
) {
    fun jwtVerifiers() = listOf(NAV_IDENT_CLAIM, PID_CLAIM).map { identClaim ->
        JWT.require(algorithm(jwksUri))
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaimPresence(identClaim)
            .build()
    }
}

interface AuthenticatedUser {
    fun extractNavIdent(): String
    fun verifiserAutorisasjon(vararg arbeidsgiverRettet: Rolle)

    companion object {
        fun fromJwt(jwt: DecodedJWT, rolleUuidSpesifikasjon: RolleUuidSpesifikasjon): AuthenticatedUser {
            val navIdentClaim = jwt.getClaim(NAV_IDENT_CLAIM)
            return if(navIdentClaim.isMissing) {
                AuthenticatedCitizenUser(jwt.getClaim(PID_CLAIM).asString())
            } else {
                AuthenticatedNavUser(
                    navIdent = navIdentClaim.asString(),
                    roller = jwt.getClaim("groups")
                        .asList(UUID::class.java)
                        .let(rolleUuidSpesifikasjon::rollerForUuider),
                    jwt = jwt.token,
                )
            }
        }
        fun Context.extractNavIdent(): String =
            attribute<AuthenticatedUser>("authenticatedUser")?.extractNavIdent() ?: throw UnauthorizedResponse("Not authenticated")
    }
}

private class AuthenticatedNavUser(
    private val navIdent: String,
    private val roller: Set<Rolle>,
    private val jwt: String
): AuthenticatedUser {
    override fun verifiserAutorisasjon(vararg gyldigeRoller: Rolle) {
        if(!erEnAvRollene(*gyldigeRoller)) {
            throw ForbiddenResponse()
        }
    }

    fun erEnAvRollene(vararg gyldigeRoller: Rolle) = roller.any { it in (gyldigeRoller.toList() + Rolle.UTVIKLER) }
    override fun extractNavIdent() = navIdent
}

private class AuthenticatedCitizenUser(
    private val pid: String
): AuthenticatedUser {
    override fun extractNavIdent() = throw ForbiddenResponse()
    override fun verifiserAutorisasjon(vararg arbeidsgiverRettet: Rolle) = throw ForbiddenResponse()
}


fun Javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(authConfigs: List<AuthenticationConfiguration>,
                                                             rolleUuidSpesifikasjon: RolleUuidSpesifikasjon): Javalin {
    log.info("Starter autentiseringoppsett")
    val verifiers = authConfigs.flatMap { it.jwtVerifiers() }
    before { ctx ->
        if (ctx.path().matches(Regex("""/api/rekrutteringstreff(?:$|/.*)"""))) {
            val token = ctx.header(HttpHeader.AUTHORIZATION.name)
                ?.removePrefix("Bearer ")
                ?.trim() ?: throw UnauthorizedResponse("Missing token")
            val decoded = verifyJwt(verifiers, token)
            ctx.attribute("authenticatedUser", AuthenticatedUser.fromJwt(decoded, rolleUuidSpesifikasjon))
        }
    }
    return this
}

private fun verifyJwt(verifiers: List<JWTVerifier>, token: String): DecodedJWT {
    for (verifier in verifiers) {
        try {
            return verifier.verify(token)
        } catch (e: JWTVerificationException) {
            // prøv neste verifier
        } catch (e: SigningKeyNotFoundException) {
            // prøv neste verifier
        }
    }
    log.error("Token verification failed")
    throw UnauthorizedResponse("Token verification failed")
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

fun Context.authenticatedUser() = attribute<AuthenticatedUser>("authenticatedUser")
    ?: run {
        log.error("No authenticated user found!")
        throw InternalServerErrorResponse()
    }