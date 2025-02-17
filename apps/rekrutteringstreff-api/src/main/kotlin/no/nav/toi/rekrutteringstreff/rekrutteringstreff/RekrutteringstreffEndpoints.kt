package no.nav.toi.rekrutteringstreff.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.extractNavIdent
import java.time.ZonedDateTime
import java.util.*

private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

@OpenApi(
    summary = "Opprett rekrutteringstreff",
    operationId = "opprettRekrutteringstreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = OpprettRekrutteringstreffDto::class,
            example = """{
                "tittel": "Sommerjobbtreff",
                "opprettetAvNavkontorEnhetId": "0318",
                "fraTid": "2025-06-15T09:00:00+02:00",
                "tilTid": "2025-06-15T11:00:00+02:00",
                "sted": "NAV Oslo"
            }"""
        )]
    ),
    responses = [OpenApiResponse(
        status = "201",
        content = [OpenApiContent(
            from = String::class,
            example = "Rekrutteringstreff opprettet"
        )]
    )],
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
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = Array<RekrutteringstreffDTO>::class,
            example = """[
                {
                    "id": "d6a587cd-8797-4b9a-a68b-575373f16d65",
                    "tittel": "Sommerjobbtreff",
                    "fraTid": "2025-06-15T09:00:00+02:00",
                    "tilTid": "2025-06-15T11:00:00+02:00",
                    "sted": "NAV Oslo",
                    "status": "Utkast",
                    "opprettetAvPersonNavident": "A123456",
                    "opprettetAvNavkontorEnhetId": "0318"
                },
                {
                    "id": "a7f2387c-4354-4a2e-90a2-fff1a1d83dc6",
                    "tittel": "Høstjobbtreff",
                    "fraTid": "2025-09-10T10:00:00+02:00",
                    "tilTid": "2025-09-10T12:00:00+02:00",
                    "sted": "NAV Bergen",
                    "status": "Publisert",
                    "opprettetAvPersonNavident": "A654321",
                    "opprettetAvNavkontorEnhetId": "1203"
                }
            ]"""
        )]
    )],
    path = endepunktRekrutteringstreff,
    methods = [HttpMethod.GET]
)
private fun hentAlleRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.status(200).json(repo.hentAlle().map { it.tilRekrutteringstreffDTO() })
}

@OpenApi(
    summary = "Hent ett rekrutteringstreff",
    operationId = "hentRekrutteringstreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = RekrutteringstreffDTO::class,
            example = """{
                "id": "d6a587cd-8797-4b9a-a68b-575373f16d65",
                "tittel": "Sommerjobbtreff",
                "fraTid": "2025-06-15T09:00:00+02:00",
                "tilTid": "2025-06-15T11:00:00+02:00",
                "sted": "NAV Oslo",
                "status": "Utkast",
                "opprettetAvPersonNavident": "A123456",
                "opprettetAvNavkontorEnhetId": "0318"
            }"""
        )]
    )],
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
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = OppdaterRekrutteringstreffDto::class,
            example = """{
                    "tittel": "Oppdatert tittel", 
                    "fraTid": "2025-06-15T09:30:00+02:00", 
                    "tilTid": "2025-06-15T11:30:00+02:00", 
                    "sted": "NAV Oslo - rom 101"
            }"""
        )]
    ),
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = RekrutteringstreffDTO::class,
                example = """{"id": "d6a587cd-8797-4b9a-a68b-575373f16d65", 
                    "tittel": "Oppdatert tittel", 
                    "fraTid": "2025-06-15T09:30:00+02:00", 
                    "tilTid": "2025-06-15T11:30:00+02:00", 
                    "sted": "NAV Oslo - rom 101", 
                    "status": "Utkast", 
                    "opprettetAvPersonNavident": "A123456", 
                    "opprettetAvNavkontorEnhetId": "0318"
                }"""
            )]
        )
    ],
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
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = String::class,
            example = "Rekrutteringstreff slettet"
        )]
    )],
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
