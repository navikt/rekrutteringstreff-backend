package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.noClassLogger
import no.nav.toi.rekrutteringstreff.eier.handleEiere
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.OpenAiClient
import java.time.ZonedDateTime
import java.util.*

private val log = noClassLogger()
const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

@OpenApi(
    summary = "Opprett rekrutteringstreff",
    operationId = "opprettRekrutteringstreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = OpprettRekrutteringstreffDto::class,
            example = """{
                "opprettetAvNavkontorEnhetId": "0318",
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
    val inputDto = ctx.bodyAsClass<OpprettRekrutteringstreffDto>()
    val internalDto = OpprettRekrutteringstreffInternalDto(
        tittel = "Nytt rekrutteringstreff",
        opprettetAvPersonNavident = ctx.extractNavIdent(),
        opprettetAvNavkontorEnhetId = inputDto.opprettetAvNavkontorEnhetId,
        opprettetAvTidspunkt = ZonedDateTime.now()
    )
    val id = repo.opprett(internalDto)
    ctx.status(201).json(mapOf("id" to id.toString()))
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
                    "beskrivelse": "Beskrivelse av Sommerjobbtreff",
                    "fraTid": "2025-06-15T09:00:00+02:00",
                    "tilTid": "2025-06-15T11:00:00+02:00",
                    "sted": "NAV Oslo",
                    "status": "Utkast",
                    "opprettetAvPersonNavident": "A123456",
                    "opprettetAvNavkontorEnhetId": "0318",
                    "opprettetAvTidspunkt": "2025-06-01T08:00:00+02:00"
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
    pathParams = [OpenApiParam(name = "id", type = UUID::class)],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = RekrutteringstreffDTO::class,
            example = """{
                "id": "d6a587cd-8797-4b9a-a68b-575373f16d65",
                "tittel": "Sommerjobbtreff",
                "beskrivelse": "Beskrivelse av Sommerjobbtreff",
                "fraTid": "2025-06-15T09:00:00+02:00",
                "tilTid": "2025-06-15T11:00:00+02:00",
                "sted": "NAV Oslo",
                "status": "Utkast",
                "opprettetAvPersonNavident": "A123456",
                "opprettetAvNavkontorEnhetId": "0318",
                "opprettetAvTidspunkt": "2025-06-01T08:00:00+02:00"
            }"""
        )]
    )],
    path = "$endepunktRekrutteringstreff/{id}",
    methods = [HttpMethod.GET]
)
private fun hentRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val id = TreffId(ctx.pathParam("id"))
    val treff = repo.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
    ctx.status(200).json(treff.tilRekrutteringstreffDTO())
}

@OpenApi(
    summary = "Oppdater ett rekrutteringstreff",
    operationId = "oppdaterRekrutteringstreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "id", type = UUID::class)],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = OppdaterRekrutteringstreffDto::class,
            example = """{
                "tittel": "Oppdatert tittel", 
                "beskrivelse": "Oppdatert beskrivelse",
                "fraTid": "2025-06-15T09:30:00+02:00", 
                "tilTid": "2025-06-15T11:30:00+02:00", 
                "sted": "NAV Oslo - rom 101"
            }"""
        )]
    ),
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = RekrutteringstreffDTO::class,
            example = """{
                "id": "d6a587cd-8797-4b9a-a68b-575373f16d65", 
                "tittel": "Oppdatert tittel", 
                "beskrivelse": "Oppdatert beskrivelse", 
                "fraTid": "2025-06-15T09:30:00+02:00", 
                "tilTid": "2025-06-15T11:30:00+02:00", 
                "sted": "NAV Oslo - rom 101", 
                "status": "Utkast", 
                "opprettetAvPersonNavident": "A123456", 
                "opprettetAvNavkontorEnhetId": "0318",
                "opprettetAvTidspunkt": "2025-06-01T08:00:00+02:00"
            }"""
        )]
    )],
    path = "$endepunktRekrutteringstreff/{id}",
    methods = [HttpMethod.PUT]
)
private fun oppdaterRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val id = TreffId(ctx.pathParam("id"))
    val dto = ctx.bodyAsClass<OppdaterRekrutteringstreffDto>()
    val navIdent = ctx.extractNavIdent()
    repo.oppdater(id, dto)
    val updated = repo.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet etter oppdatering")
    ctx.status(200).json(updated.tilRekrutteringstreffDTO())
}

@OpenApi(
    summary = "Slett ett rekrutteringstreff",
    operationId = "slettRekrutteringstreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "id", type = UUID::class)],
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
    val id = TreffId(ctx.pathParam("id"))
    repo.slett(id)
    ctx.status(200).result("Rekrutteringstreff slettet")
}

@OpenApi(
    summary = "Valider tittel og beskrivelse",
    operationId = "validerRekrutteringstreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = ValiderRekrutteringstreffDto::class,
            example = """{
                "tittel": "Sommerjobbtreff",
                "beskrivelse": "Vi arrangerer et sommerjobbtreff for flere arbeidsgivere."
            }"""
        )]
    ),
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = ValiderRekrutteringstreffResponsDto::class,
            example = """{
                "bryterRetningslinjer": true,
                "begrunnelse": "Sensitiv personinformasjon funnet"
            }"""
        )]
    )],
    path = "$endepunktRekrutteringstreff/valider",
    methods = [HttpMethod.POST]
)
private fun validerRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val dto = ctx.bodyAsClass<ValiderRekrutteringstreffDto>()
    val validationResult = OpenAiClient.validateRekrutteringstreff(dto)
    ctx.status(200).json(validationResult)
}

fun Javalin.handleRekrutteringstreff(repo: RekrutteringstreffRepository) {
    post(endepunktRekrutteringstreff, opprettRekrutteringstreffHandler(repo))
    get(endepunktRekrutteringstreff, hentAlleRekrutteringstreffHandler(repo))
    get("$endepunktRekrutteringstreff/{id}", hentRekrutteringstreffHandler(repo))
    put("$endepunktRekrutteringstreff/{id}", oppdaterRekrutteringstreffHandler(repo))
    delete("$endepunktRekrutteringstreff/{id}", slettRekrutteringstreffHandler(repo))
    post("$endepunktRekrutteringstreff/valider", validerRekrutteringstreffHandler(repo))
    handleEiere(repo.eierRepository)
}

data class RekrutteringstreffDTO(
    val id: UUID,
    val tittel: String,
    val beskrivelse: String?,
    val fraTid: ZonedDateTime?,
    val tilTid: ZonedDateTime?,
    val sted: String?,
    val status: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
)

data class OpprettRekrutteringstreffDto(
    val opprettetAvNavkontorEnhetId: String,
)

data class OpprettRekrutteringstreffInternalDto(
    val tittel: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
)

data class OppdaterRekrutteringstreffDto(
    val tittel: String,
    val beskrivelse: String?,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String
)

data class ValiderRekrutteringstreffDto(
    val tittel: String,
    val beskrivelse: String
)

data class ValiderRekrutteringstreffResponsDto(
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String
)
