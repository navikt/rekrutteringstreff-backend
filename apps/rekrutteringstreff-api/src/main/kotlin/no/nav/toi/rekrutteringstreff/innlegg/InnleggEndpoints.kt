package no.nav.toi.rekrutteringstreff.innlegg

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.UUID

private const val REKRUTTERINGSTREFF_ID_PARAM = "rekrutteringstreffId"
private const val INNLEGG_ID_PARAM = "innleggId"

private const val INNLEGG_BASE_PATH = "$endepunktRekrutteringstreff/{$REKRUTTERINGSTREFF_ID_PARAM}/innlegg"
private const val INNLEGG_ITEM_PATH = "$INNLEGG_BASE_PATH/{$INNLEGG_ID_PARAM}"

fun Javalin.handleInnlegg(repo: InnleggRepository) {
    get(INNLEGG_BASE_PATH, hentAlleInnleggForTreff(repo))
    post(INNLEGG_BASE_PATH, opprettInnleggForTreff(repo))
    get(INNLEGG_ITEM_PATH, hentEttInnlegg(repo))
    put(INNLEGG_ITEM_PATH, oppdaterEttInnlegg(repo))
    delete(INNLEGG_ITEM_PATH, slettEttInnlegg(repo))
}

@OpenApi(
    summary = "Hent alle innlegg for et rekrutteringstreff",
    operationId = "hentAlleInnleggForTreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true, description = "Rekrutteringstreffets ID")],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(from = Array<InnleggResponseDto>::class)]
    )],
    path = INNLEGG_BASE_PATH,
    methods = [HttpMethod.GET]
)
private fun hentAlleInnleggForTreff(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
    val innleggListe = repo.hentForTreff(treffId).map { it.toResponseDto() }
    ctx.status(200).json(innleggListe)
}

@OpenApi(
    summary = "Opprett et nytt innlegg for et rekrutteringstreff",
    operationId = "opprettInnleggForTreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true, description = "Rekrutteringstreffets ID")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = OpprettInnleggRequestDto::class)]),
    responses = [OpenApiResponse(
        status = "201",
        content = [OpenApiContent(from = InnleggResponseDto::class)]
    )],
    path = INNLEGG_BASE_PATH,
    methods = [HttpMethod.POST]
)
private fun opprettInnleggForTreff(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
    val requestDto = ctx.bodyAsClass<OpprettInnleggRequestDto>()
    val nyttInnlegg = repo.opprett(treffId, requestDto)
    ctx.status(201).json(nyttInnlegg.toResponseDto())
}

@OpenApi(
    summary = "Hent et spesifikt innlegg",
    operationId = "hentEttInnlegg",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [
        OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true, description = "Rekrutteringstreffets ID"),
        OpenApiParam(name = INNLEGG_ID_PARAM, type = Long::class, required = true, description = "Innleggets ID")
    ],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = InnleggResponseDto::class)]),
        OpenApiResponse(status = "404", description = "Innlegg ikke funnet")
    ],
    path = INNLEGG_ITEM_PATH,
    methods = [HttpMethod.GET]
)
private fun hentEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val innleggId = ctx.pathParam(INNLEGG_ID_PARAM).toLong()
    val innlegg = repo.hentById(innleggId) ?: throw NotFoundResponse("Innlegg ikke funnet")
    ctx.status(200).json(innlegg.toResponseDto())
}

@OpenApi(
    summary = "Oppdater et spesifikt innlegg",
    operationId = "oppdaterEttInnlegg",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [
        OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true, description = "Rekrutteringstreffets ID"),
        OpenApiParam(name = INNLEGG_ID_PARAM, type = Long::class, required = true, description = "Innleggets ID")
    ],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = OpprettInnleggRequestDto::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = InnleggResponseDto::class)]),
        OpenApiResponse(status = "404", description = "Innlegg ikke funnet")
    ],
    path = INNLEGG_ITEM_PATH,
    methods = [HttpMethod.PUT]
)
private fun oppdaterEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    // val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM)) // Kan brukes til validering
    val innleggId = ctx.pathParam(INNLEGG_ID_PARAM).toLong()
    val requestDto = ctx.bodyAsClass<OpprettInnleggRequestDto>()
    val oppdatertInnlegg = repo.oppdater(innleggId, requestDto) ?: throw NotFoundResponse("Innlegg ikke funnet for oppdatering")
    ctx.status(200).json(oppdatertInnlegg.toResponseDto())
}

@OpenApi(
    summary = "Slett et spesifikt innlegg",
    operationId = "slettEttInnlegg",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [
        OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true, description = "Rekrutteringstreffets ID"),
        OpenApiParam(name = INNLEGG_ID_PARAM, type = Long::class, required = true, description = "Innleggets ID")
    ],
    responses = [
        OpenApiResponse(status = "204", description = "Innlegg slettet"),
        OpenApiResponse(status = "404", description = "Innlegg ikke funnet")
    ],
    path = INNLEGG_ITEM_PATH,
    methods = [HttpMethod.DELETE]
)
private fun slettEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val innleggId = ctx.pathParam(INNLEGG_ID_PARAM).toLong()
    val slettet = repo.slett(innleggId)
    if (slettet) {
        ctx.status(204)
    } else {
        throw NotFoundResponse("Innlegg ikke funnet for sletting")
    }
}