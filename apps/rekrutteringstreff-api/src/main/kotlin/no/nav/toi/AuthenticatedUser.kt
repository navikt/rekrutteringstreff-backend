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
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
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
    fun extractPid(): String

    companion object {
        fun fromJwt(jwt: DecodedJWT, rolleUuidSpesifikasjon: RolleUuidSpesifikasjon, modiaKlient: ModiaKlient, pilotkontorer: List<String>): AuthenticatedUser {
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
                    modiaKlient = modiaKlient,
                    pilotkontorer = pilotkontorer
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
    private val jwt: String,
    private val modiaKlient: ModiaKlient,
    private val pilotkontorer: List<String>
): AuthenticatedUser {
    override fun verifiserAutorisasjon(vararg gyldigeRoller: Rolle) {
        if(!erEnAvRollene(*gyldigeRoller)) {
            throw ForbiddenResponse()
        }
        val veiledersKontor = modiaKlient.hentVeiledersAktivEnhet(jwt)

        if(veiledersKontor.isNullOrEmpty() ) {
            throw ForbiddenResponse("Finner ikke veileders innloggede kontor")
        } else if(veiledersKontor !in pilotkontorer) {
            throw ForbiddenResponse("Veileder er ikke tilknyttet et pilotkontor")
        }
    }

    fun erEnAvRollene(vararg gyldigeRoller: Rolle) = roller.any { it in (gyldigeRoller.toList() + Rolle.UTVIKLER) }
    override fun extractNavIdent() = navIdent
    override fun extractPid(): String = throw ForbiddenResponse("PID is not available for NAV users")
}

private class AuthenticatedCitizenUser(
    private val pid: String
): AuthenticatedUser {
    override fun extractNavIdent() = throw ForbiddenResponse()
    override fun verifiserAutorisasjon(vararg arbeidsgiverRettet: Rolle) {
        if(Rolle.BORGER !in arbeidsgiverRettet) throw ForbiddenResponse()
    }

    override fun extractPid(): String = pid
}


fun Javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(
    authConfigs: List<AuthenticationConfiguration>,
    rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
    modiaKlient: ModiaKlient,
    pilotkontorer: List<String>
): Javalin {
    log.info("Starter autentiseringoppsett")
    val verifiers = authConfigs.flatMap { it.jwtVerifiers() }
    before { ctx ->
        if (ctx.path().matches(Regex("""/api/rekrutteringstreff(?:$|/.*)"""))) {
            val token = ctx.header(HttpHeader.AUTHORIZATION.name)
                ?.removePrefix("Bearer ")
                ?.trim() ?: throw UnauthorizedResponse("Missing token")

            ctx.attribute("raw_token", token)

            val decoded = verifyJwt(verifiers, token)
            ctx.attribute("authenticatedUser", AuthenticatedUser.fromJwt(decoded, rolleUuidSpesifikasjon, modiaKlient, pilotkontorer))
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
        } catch (e: IllegalArgumentException) {
            // Fanges når token er helt ugyldig (f.eks. ikke JWT-format). Prøv neste, til slutt 401.
        }
    }
    log.warn("Token verification failed for provided token (possibly malformed)")
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