package no.nav.toi

import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTParser
import io.javalin.http.*
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.arbeidsgiver.toi.logging.noClassLogger
import no.nav.security.token.support.core.exceptions.JwtTokenValidatorException
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import no.nav.security.token.support.core.validation.DefaultConfigurableJwtValidator
import no.nav.security.token.support.core.validation.JwtTokenValidator
import no.nav.toi.exception.AktivEnhetManglerException
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import java.net.URI
import java.util.*

private const val NAV_IDENT_CLAIM = "NAVident"
private const val PID_CLAIM = "pid"
private const val GROUPS_CLAIM = "groups"

private val log = noClassLogger()

/**
 * Hvilken identitetstilbyder som utstedte tokenet. Bestemmer hvilken brukertype tokenet kan gi.
 */
enum class IssuerType {
    /** Gir alltid Nav-ansatt, og krever `NAVident`. */
    AZURE_AD,

    /** Gir alltid borger, og krever `pid`. */
    TOKEN_X,

    /** Bare i dev. Brukertypen bestemmes av claims: `NAVident` gir Nav-ansatt, ellers borger. */
    FAKEDINGS,
}

class AuthenticationConfiguration(
    private val issuer: String,
    private val jwksUri: String,
    private val audience: String,
    val issuerType: IssuerType,
) {
    internal fun lagValidator() = IssuerValidator(
        issuer = issuer,
        issuerType = issuerType,
        validator = DefaultConfigurableJwtValidator(
            issuer,
            listOf(audience),
            emptyList(),
            JWKSourceBuilder.create<SecurityContext>(URI(jwksUri).toURL()).build(),
        ),
    )
}

internal class IssuerValidator(
    val issuer: String,
    val issuerType: IssuerType,
    private val validator: JwtTokenValidator,
) {
    fun valider(token: String): JwtToken {
        validator.assertValidToken(token)
        return JwtToken(token)
    }
}

internal class ValidertToken(val issuerType: IssuerType, val token: JwtToken)

interface AuthenticatedUser {
    fun extractNavIdent(): String
    fun extractKontorId(): String?
    fun verifiserAutorisasjon(vararg arbeidsgiverRettet: Rolle)
    fun extractPid(): String
    fun innkommendeToken(): String
    fun erUtvikler(): Boolean = false
    fun harRolle(rolle: Rolle): Boolean = false
    val erBorger: Boolean get() = false
    val erNavAnsatt: Boolean get() = false

    companion object {
        internal fun fromJwt(
            validert: ValidertToken,
            rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
            modiaKlient: ModiaKlient,
        ): AuthenticatedUser {
            val claims = validert.token.jwtTokenClaims
            val token = validert.token.encodedToken
            return when (validert.issuerType) {
                IssuerType.AZURE_AD -> navBruker(claims, token, rolleUuidSpesifikasjon, modiaKlient)
                IssuerType.TOKEN_X -> borger(claims, token)
                IssuerType.FAKEDINGS ->
                    if (claims.get(NAV_IDENT_CLAIM) != null) navBruker(claims, token, rolleUuidSpesifikasjon, modiaKlient)
                    else borger(claims, token)
            }
        }

        private fun navBruker(
            claims: JwtTokenClaims,
            token: String,
            rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
            modiaKlient: ModiaKlient,
        ): AuthenticatedUser = AuthenticatedNavUser(
            navIdent = påkrevdStringClaim(claims, NAV_IDENT_CLAIM),
            roller = rolleUuidSpesifikasjon.rollerForUuider(grupper(claims)),
            token = token,
            modiaKlient = modiaKlient,
        )

        private fun borger(claims: JwtTokenClaims, token: String): AuthenticatedUser =
            AuthenticatedCitizenUser(pid = påkrevdStringClaim(claims, PID_CLAIM), token = token)

        private fun påkrevdStringClaim(claims: JwtTokenClaims, navn: String): String =
            (claims.get(navn) as? String)?.takeIf { it.isNotBlank() }
                ?: throw UnauthorizedResponse("Token mangler gyldig claim $navn")

        private fun grupper(claims: JwtTokenClaims): List<UUID> {
            val verdi = claims.get(GROUPS_CLAIM) ?: return emptyList()
            if (verdi !is List<*>) throw UnauthorizedResponse("Ugyldig groups-claim")
            return verdi.map {
                (it as? String)?.let { uuid -> runCatching { UUID.fromString(uuid) }.getOrNull() }
                    ?: throw UnauthorizedResponse("Ugyldig groups-claim")
            }
        }

        fun Context.extractNavIdent(): String =
            attribute<AuthenticatedUser>("authenticatedUser")?.extractNavIdent()
                ?: throw UnauthorizedResponse("Not authenticated")
    }
}

private class AuthenticatedNavUser(
    private val navIdent: String,
    private val roller: Set<Rolle>,
    private val token: String,
    private val modiaKlient: ModiaKlient,
) : AuthenticatedUser {
    var veiledersKontor: String? = null

    override fun verifiserAutorisasjon(vararg gyldigeRoller: Rolle) {
        if (!erEnAvRollene(*gyldigeRoller)) {
            throw ForbiddenResponse()
        } else if (erUtvikler()) {
            return
        } else {
            veiledersKontor = modiaKlient.hentVeiledersAktivEnhet(token)
            if (veiledersKontor.isNullOrEmpty()) {
                throw AktivEnhetManglerException()
            }
        }
    }

    fun erEnAvRollene(vararg gyldigeRoller: Rolle) = roller.any { it in (gyldigeRoller.toList() + Rolle.UTVIKLER) }
    override fun extractNavIdent() = navIdent
    override fun extractKontorId() = veiledersKontor ?: modiaKlient.hentVeiledersAktivEnhet(token).also { veiledersKontor = it }
    override fun extractPid(): String = throw ForbiddenResponse("PID is not available for NAV users")
    override fun innkommendeToken() = token
    override fun erUtvikler() = roller.any { it == Rolle.UTVIKLER }
    override fun harRolle(rolle: Rolle) = rolle in roller
    override val erNavAnsatt = true
}

private class AuthenticatedCitizenUser(
    private val pid: String,
    private val token: String,
) : AuthenticatedUser {
    override fun extractNavIdent() = throw ForbiddenResponse()
    override fun extractKontorId() = throw ForbiddenResponse()
    override fun verifiserAutorisasjon(vararg arbeidsgiverRettet: Rolle) {
        if (Rolle.BORGER !in arbeidsgiverRettet) throw ForbiddenResponse()
    }

    override fun extractPid(): String = pid
    override fun innkommendeToken() = token
    override val erBorger = true
}


fun JavalinDefaultRoutingApi.leggTilAutensieringPåRekrutteringstreffEndepunkt(
    authConfigs: List<AuthenticationConfiguration>,
    rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
    modiaKlient: ModiaKlient,
): JavalinDefaultRoutingApi {
    log.info("Starter autentiseringoppsett")
    val validatorer = authConfigs.map { it.lagValidator() }
    before { ctx ->
        if (ctx.path().matches(Regex("""/api/rekrutteringstreff(?:$|/.*)"""))) {
            val token = ctx.header(Header.AUTHORIZATION)
                ?.removePrefix("Bearer ")
                ?.trim() ?: throw UnauthorizedResponse("Missing token")

            val validert = verifyJwt(validatorer, token)
            val bruker = try {
                AuthenticatedUser.fromJwt(validert, rolleUuidSpesifikasjon, modiaKlient)
            } catch (e: UnauthorizedResponse) {
                log.warn("Gyldig token fra ${validert.issuerType} manglet påkrevde claims")
                throw e
            } catch (e: Exception) {
                log.warn("Kunne ikke lese claims fra token fra ${validert.issuerType}: ${e::class.simpleName}")
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
internal fun verifyJwt(validatorer: List<IssuerValidator>, token: String): ValidertToken {
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
        ValidertToken(validator.issuerType, validator.valider(token))
    } catch (e: JwtTokenValidatorException) {
        log.warn("Token verification failed for ${validator.issuerType}: ${e.cause?.let { it::class.simpleName }}")
        throw UnauthorizedResponse("Token verification failed")
    } catch (e: Exception) {
        log.warn("Token verification failed for ${validator.issuerType}: ${e::class.simpleName}")
        throw UnauthorizedResponse("Token verification failed")
    }
}

fun Context.authenticatedUser() = attribute<AuthenticatedUser>("authenticatedUser")
    ?: run {
        log.error("No authenticated user found!")
        throw InternalServerErrorResponse()
    }
