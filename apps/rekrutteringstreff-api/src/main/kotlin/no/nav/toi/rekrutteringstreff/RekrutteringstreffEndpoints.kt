package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import java.time.ZonedDateTime
import java.util.UUID

private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

@OpenApi(
    summary = "Opprett rekrutteringstreff",
    operationId = "opprettRekrutteringstreff",
    responses = [OpenApiResponse("201", [OpenApiContent(String::class)])],
    path = endepunktRekrutteringstreff,
    methods = [HttpMethod.POST]
)
private fun opprettRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val dto = ctx.bodyAsClass<OpprettRekrutteringstreffDto>()
    val navIdent = ctx.extractNavIdent()
    repo.opprett(dto, navIdent)
    ctx.status(201).result("Rekrutteringstreff opprettet")
}

@OpenApi(
    summary = "Hent alle rekrutteringstreff",
    operationId = "hentAlleRekrutteringstreff",
    responses = [OpenApiResponse("200", [OpenApiContent(Array<RekrutteringstreffDTO>::class)])],
    path = endepunktRekrutteringstreff,
    methods = [HttpMethod.GET]
)
private fun hentAlleRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.status(200).json(repo.hentAlle().map { it.tilRekrutteringstreffDTO() })
}

@OpenApi(
    summary = "Hent ett rekrutteringstreff",
    operationId = "hentRekrutteringstreff",
    responses = [OpenApiResponse("200", [OpenApiContent(RekrutteringstreffDTO::class)])],
    path = "$endepunktRekrutteringstreff/{id}",
    methods = [HttpMethod.GET]
)
private fun hentRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val id = UUID.fromString(ctx.pathParam("id"))
    val treff = repo.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
    ctx.status(200).json(treff.tilRekrutteringstreffDTO())
}

@OpenApi(
    summary = "Oppdater rekrutteringstreff",
    operationId = "oppdaterRekrutteringstreff",
    responses = [OpenApiResponse("200", [OpenApiContent(RekrutteringstreffDTO::class)])],
    path = "$endepunktRekrutteringstreff/{id}",
    methods = [HttpMethod.PUT]
)
private fun oppdaterRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val id = UUID.fromString(ctx.pathParam("id"))
    val dto = ctx.bodyAsClass<OppdaterRekrutteringstreffDto>()
    val navIdent = ctx.extractNavIdent()
    repo.oppdater(id, dto, navIdent)
    val updated = repo.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet etter oppdatering")
    ctx.status(200).json(updated.tilRekrutteringstreffDTO())
}

@OpenApi(
    summary = "Slett rekrutteringstreff",
    operationId = "slettRekrutteringstreff",
    responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
    path = "$endepunktRekrutteringstreff/{id}",
    methods = [HttpMethod.DELETE]
)
private fun slettRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val id = UUID.fromString(ctx.pathParam("id"))
    repo.slett(id)
    ctx.status(200).result("Rekrutteringstreff slettet")
}

fun Javalin.handleRekrutteringstreff(repo: RekrutteringstreffRepository) {
    post(endepunktRekrutteringstreff, opprettRekrutteringstreffHandler(repo))
    get(endepunktRekrutteringstreff, hentAlleRekrutteringstreffHandler(repo))
    get("$endepunktRekrutteringstreff/{id}", hentRekrutteringstreffHandler(repo))
    put("$endepunktRekrutteringstreff/{id}", oppdaterRekrutteringstreffHandler(repo))
    delete("$endepunktRekrutteringstreff/{id}", slettRekrutteringstreffHandler(repo))
}


data class RekrutteringstreffDTO(
    val id: UUID,
    val tittel: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String,
    val status: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpprettRekrutteringstreffDto(
    val tittel: String,
    val opprettetAvNavkontorEnhetId: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String
)

data class OppdaterRekrutteringstreffDto(
    val tittel: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String
)