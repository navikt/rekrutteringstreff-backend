package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import java.time.ZonedDateTime
import com.nimbusds.jwt.SignedJWT

private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

@OpenApi(
    summary = "Opprett rekrutteringstreff",
    operationId = "opprettRekrutteringstreff",
    responses = [OpenApiResponse("201", [OpenApiContent(String::class)])],
    path = endepunktRekrutteringstreff,
    methods = [HttpMethod.POST]
)
fun opprettRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit =
    { ctx ->
        val dto = ctx.bodyAsClass<OpprettRekrutteringstreffDto>()
        val navIdent = ctx.extractNavIdent()
        repo.opprett(dto, navIdent)
        ctx.status(201).result("Rekrutteringstreff opprettet")
    }

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpprettRekrutteringstreffDto(
    val tittel: String,
    val kontor: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String
)

fun Context.extractNavIdent(): String {
    val token = header("Authorization")?.removePrefix("Bearer ")?.trim() ?: ""
    return try {
        val jwt = SignedJWT.parse(token)
        jwt.jwtClaimsSet.getStringClaim("NAVident") ?: "ukjent"
    } catch (e: Exception) {
        "ukjent"
    }
}

fun Javalin.handleRekrutteringstreff(repo: RekrutteringstreffRepository) {
    post(endepunktRekrutteringstreff, opprettRekrutteringstreffHandler(repo))
}
