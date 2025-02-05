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

private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
private const val endepunktHentAlleRekrutteringstreff = "$endepunktRekrutteringstreff/hentalle"

@OpenApi(
    summary = "Opprett rekrutteringstreff",
    operationId = "opprettRekrutteringstreff",
    responses = [OpenApiResponse("201", [OpenApiContent(String::class)])],
    path = endepunktRekrutteringstreff,
    methods = [HttpMethod.POST]
)
private fun opprettRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit =
    { ctx ->
        val dto = ctx.bodyAsClass<OpprettRekrutteringstreffDto>()
        val navIdent = ctx.extractNavIdent() // Henter ut navIdent fra den autentiserte brukeren
        repo.opprett(dto, navIdent)
        ctx.status(201).result("Rekrutteringstreff opprettet")
    }

@OpenApi(
    summary = "Hent alle rekrutteringstreff",
    operationId = "hentAlleRekrutteringstreff",
    responses = [OpenApiResponse("200", [OpenApiContent(Array<RekrutteringstreffDTO>::class)])],
    path = endepunktHentAlleRekrutteringstreff,
    methods = [HttpMethod.POST]
)
private fun hentAlleRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit =
    { ctx ->
        ctx.status(200).json(repo.hentAlle().map(Rekrutteringstreff::tilRekrutteringstreffDTO))
    }

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpprettRekrutteringstreffDto(
    val tittel: String,
    val opprettetAvNavkontorEnhetId: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String
)

data class RekrutteringstreffDTO(
    val tittel: String
)

fun Javalin.handleRekrutteringstreff(repo: RekrutteringstreffRepository) {
    post(endepunktRekrutteringstreff, opprettRekrutteringstreffHandler(repo))
    post(endepunktHentAlleRekrutteringstreff, hentAlleRekrutteringstreffHandler(repo))
}
