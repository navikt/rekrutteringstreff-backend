package no.nav.toi.minside

import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTParser
import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.arbeidsgiver.toi.logging.noClassLogger
import no.nav.security.token.support.core.exceptions.JwtTokenValidatorException
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.validation.DefaultConfigurableJwtValidator
import no.nav.security.token.support.core.validation.JwtTokenValidator
import java.net.URI

private const val PERSON_IDENT_CLAIM = "pid"

private val log = noClassLogger()

/**
 * Konfigurasjon for én TokenX-issuer. Minside tar bare imot borgertokens.
 */
class AuthenticationConfiguration(
    private val issuer: String,
    private val jwksUri: String,
    private val audience: String
) {
    internal fun lagValidator() = IssuerValidator(
        issuer = issuer,
        validator = DefaultConfigurableJwtValidator(
            issuer,
            listOf(audience),
            emptyList(),
            JWKSourceBuilder.create<SecurityContext>(URI(jwksUri).toURL()).build(),
        ),
    )
}

internal class IssuerValidator(val issuer: String, private val validator: JwtTokenValidator) {
    fun valider(token: String): JwtToken {
        validator.assertValidToken(token)
        return JwtToken(token)
    }
}

class AuthenticatedUser private constructor(
    private val pid: String,
    val jwt: String
) {
    companion object {
        internal fun fromJwt(token: JwtToken) =
            AuthenticatedUser(
                pid = (token.jwtTokenClaims.get(PERSON_IDENT_CLAIM) as? String)?.takeIf { it.isNotBlank() }
                    ?: throw UnauthorizedResponse("Token mangler gyldig claim $PERSON_IDENT_CLAIM"),
                jwt = token.encodedToken,
            )
        fun Context.extractIdent(): String =
            attribute<AuthenticatedUser>("authenticatedUser")?.pid ?: throw UnauthorizedResponse("Not authenticated")
    }
}


fun JavalinDefaultRoutingApi.leggTilAutensieringPåRekrutteringstreffEndepunkt(authConfigs: List<AuthenticationConfiguration>): JavalinDefaultRoutingApi {
    log.info("Starter autentiseringoppsett")
    val validatorer = authConfigs.map { it.lagValidator() }
    before { ctx ->
        if (ctx.path().matches(Regex("""/api/rekrutteringstreff(?:$|/.*)"""))) {
            val token = ctx.header(Header.AUTHORIZATION)
                ?.removePrefix("Bearer ")
                ?.trim() ?: throw UnauthorizedResponse("Missing token")
            val validert = verifyJwt(validatorer, token)
            val bruker = try {
                AuthenticatedUser.fromJwt(validert)
            } catch (e: UnauthorizedResponse) {
                log.warn("Gyldig token manglet påkrevde claims")
                throw e
            } catch (e: Exception) {
                log.warn("Kunne ikke lese claims fra token: ${e::class.simpleName}")
                throw UnauthorizedResponse("Token verification failed")
            }
            ctx.attribute("authenticatedUser", bruker)
        }
    }
    return this
}

/**
 * Velger validator ut fra `iss` i det uverifiserte tokenet. Det er trygt fordi validatoren selv krever eksakt
 * samme `iss`, og verifiserer signaturen mot JWKS for akkurat den issueren.
 */
internal fun verifyJwt(validatorer: List<IssuerValidator>, token: String): JwtToken {
    val issuer = try {
        JWTParser.parse(token).jwtClaimsSet.issuer
    } catch (e: Exception) {
        log.warn("Token verification failed: token kunne ikke parses (${e::class.simpleName})")
        throw UnauthorizedResponse("Token verification failed")
    }
    val validator = validatorer.firstOrNull { it.issuer == issuer } ?: run {
        log.warn("Token verification failed: ukjent issuer")
        throw UnauthorizedResponse("Token verification failed")
    }
    return try {
        validator.valider(token)
    } catch (e: JwtTokenValidatorException) {
        log.warn("Token verification failed: ${e.cause?.let { it::class.simpleName }}")
        throw UnauthorizedResponse("Token verification failed")
    } catch (e: Exception) {
        log.warn("Token verification failed: ${e::class.simpleName}")
        throw UnauthorizedResponse("Token verification failed")
    }
}

fun Context.authenticatedUser() = attribute<AuthenticatedUser>("authenticatedUser")
    ?: run {
        log.error("No authenticated user found!")
        throw InternalServerErrorResponse()
    }
