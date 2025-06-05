package no.nav.toi.rekrutteringstreff.innlegg

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.net.HttpURLConnection
import java.util.UUID

private const val REKRUTTERINGSTREFF_ID_PARAM = "rekrutteringstreffId"
private const val INNLEGG_ID_PARAM            = "innleggId"

private const val INNLEGG_BASE_PATH = "$endepunktRekrutteringstreff/{$REKRUTTERINGSTREFF_ID_PARAM}/innlegg"
private const val INNLEGG_ITEM_PATH = "$INNLEGG_BASE_PATH/{$INNLEGG_ID_PARAM}"

fun Javalin.handleInnlegg(repo: InnleggRepository) {
    get   (INNLEGG_BASE_PATH, hentAlleInnleggForTreff(repo))
    get   (INNLEGG_ITEM_PATH, hentEttInnlegg(repo))
    post  (INNLEGG_BASE_PATH, opprettInnlegg(repo))
    put   (INNLEGG_ITEM_PATH, oppdaterEttInnlegg(repo))
    delete(INNLEGG_ITEM_PATH, slettEttInnlegg(repo))
}

@OpenApi(
    summary = "Hent alle innlegg for et rekrutteringstreff",
    operationId = "hentAlleInnleggForTreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(REKRUTTERINGSTREFF_ID_PARAM, UUID::class)],
    responses = [OpenApiResponse(
        "200",
        [OpenApiContent(
            Array<InnleggResponseDto>::class,
            example = """[{"id":"...","treffId":"...","tittel":"...","opprettetAvPersonNavident":"...","opprettetAvPersonNavn":"...","opprettetAvPersonBeskrivelse":"...","sendesTilJobbsokerTidspunkt":"...","htmlContent":"...","opprettetTidspunkt":"...","sistOppdatertTidspunkt":"..."}]"""
        )]
    )],
    path = INNLEGG_BASE_PATH,
    methods = [HttpMethod.GET]
)
private fun hentAlleInnleggForTreff(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
    ctx.json(repo.hentForTreff(treffId).map(Innlegg::toResponseDto))
}

@OpenApi(
    summary = "Hent innlegg",
    operationId = "hentEttInnlegg",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [
        OpenApiParam(REKRUTTERINGSTREFF_ID_PARAM, UUID::class),
        OpenApiParam(INNLEGG_ID_PARAM,            UUID::class)
    ],
    responses = [
        OpenApiResponse(
            "200",
            [OpenApiContent(
                InnleggResponseDto::class,
                example = """{"id":"...","treffId":"...","tittel":"...","opprettetAvPersonNavident":"...","opprettetAvPersonNavn":"...","opprettetAvPersonBeskrivelse":"...","sendesTilJobbsokerTidspunkt":"...","htmlContent":"...","opprettetTidspunkt":"...","sistOppdatertTidspunkt":"..."}"""
            )]
        ),
        OpenApiResponse("404")
    ],
    path = INNLEGG_ITEM_PATH,
    methods = [HttpMethod.GET]
)
private fun hentEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val id = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
    ctx.json(repo.hentById(id)?.toResponseDto() ?: throw NotFoundResponse())
}

@OpenApi(
    summary = "Opprett nytt innlegg",
    operationId = "opprettInnlegg",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(REKRUTTERINGSTREFF_ID_PARAM, UUID::class)],
    requestBody = OpenApiRequestBody([
        OpenApiContent(
            OpprettInnleggRequestDto::class,
            example = """{"tittel":"...","opprettetAvPersonNavident":"...","opprettetAvPersonNavn":"...","opprettetAvPersonBeskrivelse":"...","sendesTilJobbsokerTidspunkt":"...","htmlContent":"..."}"""
        )
    ]),
    responses = [
        OpenApiResponse(
            "201",
            [OpenApiContent(
                InnleggResponseDto::class,
                example = """{"id":"...","treffId":"...","tittel":"...","opprettetAvPersonNavident":"...","opprettetAvPersonNavn":"...","opprettetAvPersonBeskrivelse":"...","sendesTilJobbsokerTidspunkt":"...","htmlContent":"...","opprettetTidspunkt":"...","sistOppdatertTidspunkt":"..."}"""
            )]
        ),
        OpenApiResponse("404")
    ],
    path = INNLEGG_BASE_PATH,
    methods = [HttpMethod.POST]
)
private fun opprettInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val treffIdParam = ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM)
    val treffId = TreffId(treffIdParam)
    val body = ctx.bodyAsClass<OpprettInnleggRequestDto>()
    try {
        val innleggId = UUID.randomUUID()
        val (innlegg, _) = repo.oppdater(innleggId, treffId, body)
        ctx.status(HttpURLConnection.HTTP_CREATED).json(innlegg.toResponseDto())
    } catch (e: IllegalStateException) {
        if (e.message?.contains("finnes ikke") == true) {
            throw NotFoundResponse("Rekrutteringstreff med id $treffIdParam ikke funnet.")
        }
        throw e
    }
}

@OpenApi(
    summary = "Oppdater et eksisterende innlegg",
    operationId = "oppdaterEttInnlegg",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [
        OpenApiParam(REKRUTTERINGSTREFF_ID_PARAM, UUID::class),
        OpenApiParam(INNLEGG_ID_PARAM,            UUID::class)
    ],
    requestBody = OpenApiRequestBody([
        OpenApiContent(
            OpprettInnleggRequestDto::class,
            example = """{"tittel":"...","opprettetAvPersonNavident":"...","opprettetAvPersonNavn":"...","opprettetAvPersonBeskrivelse":"...","sendesTilJobbsokerTidspunkt":"...","htmlContent":"..."}"""
        )
    ]),
    responses = [
        OpenApiResponse(
            "200",
            [OpenApiContent(
                InnleggResponseDto::class,
                example = """{"id":"...","treffId":"...","tittel":"...","opprettetAvPersonNavident":"...","opprettetAvPersonNavn":"...","opprettetAvPersonBeskrivelse":"...","sendesTilJobbsokerTidspunkt":"...","htmlContent":"...","opprettetTidspunkt":"...","sistOppdatertTidspunkt":"..."}"""
            )]
        ),
        OpenApiResponse("404")
    ],
    path = INNLEGG_ITEM_PATH,
    methods = [HttpMethod.PUT]
)
private fun oppdaterEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val treffIdParam = ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM)
    val innleggIdParam = ctx.pathParam(INNLEGG_ID_PARAM)
    val innleggId = UUID.fromString(innleggIdParam)
    val treffId = TreffId(treffIdParam)
    val body = ctx.bodyAsClass<OpprettInnleggRequestDto>()

    try {
        val (innlegg, created) = repo.oppdater(innleggId, treffId, body)
        val status = if (created) HttpURLConnection.HTTP_CREATED else HttpURLConnection.HTTP_OK
        ctx.status(status).json(innlegg.toResponseDto())
    } catch (e: IllegalStateException) {
        if (e.message?.contains("finnes ikke") == true) {
            throw NotFoundResponse("Rekrutteringstreff med id $treffIdParam ikke funnet.")
        }
        throw e
    }
}

@OpenApi(
    summary = "Slett innlegg",
    operationId = "slettEttInnlegg",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [
        OpenApiParam(REKRUTTERINGSTREFF_ID_PARAM, UUID::class),
        OpenApiParam(INNLEGG_ID_PARAM,            UUID::class)
    ],
    responses = [
        OpenApiResponse("204"),
        OpenApiResponse("404")
    ],
    path = INNLEGG_ITEM_PATH,
    methods = [HttpMethod.DELETE]
)
private fun slettEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val id = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
    if (repo.slett(id)) ctx.status(HttpURLConnection.HTTP_NO_CONTENT) else throw NotFoundResponse()
}