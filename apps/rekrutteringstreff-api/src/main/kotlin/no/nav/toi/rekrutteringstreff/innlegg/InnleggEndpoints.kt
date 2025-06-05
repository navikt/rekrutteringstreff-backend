package no.nav.toi.rekrutteringstreff.innlegg

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_OK
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
        "200", [OpenApiContent(
            Array<InnleggResponseDto>::class,
            example = """
            [
              {
                "id":"11111111-2222-3333-4444-555555555555",
                "treffId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "tittel":"Velkommen!",
                "opprettetAvPersonNavident":"A123456",
                "opprettetAvPersonNavn":"Ola Nordmann",
                "opprettetAvPersonBeskrivelse":"Veileder",
                "sendesTilJobbsokerTidspunkt":"2025-06-05T12:00:00+02:00",
                "htmlContent":"<p>Hei og velkommen til treffet!</p>",
                "opprettetTidspunkt":"2025-06-05T10:00:00+02:00",
                "sistOppdatertTidspunkt":"2025-06-05T10:00:00+02:00"
              }
            ]
            """
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
    summary = "Hent ett innlegg",
    operationId = "hentEttInnlegg",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [
        OpenApiParam(REKRUTTERINGSTREFF_ID_PARAM, UUID::class),
        OpenApiParam(INNLEGG_ID_PARAM,            UUID::class)
    ],
    responses = [
        OpenApiResponse(
            "200", [OpenApiContent(
                InnleggResponseDto::class,
                example = """
                {
                  "id":"11111111-2222-3333-4444-555555555555",
                  "treffId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                  "tittel":"Velkommen!",
                  "opprettetAvPersonNavident":"A123456",
                  "opprettetAvPersonNavn":"Ola Nordmann",
                  "opprettetAvPersonBeskrivelse":"Veileder",
                  "sendesTilJobbsokerTidspunkt":"2025-06-05T12:00:00+02:00",
                  "htmlContent":"<p>Hei og velkommen til treffet!</p>",
                  "opprettetTidspunkt":"2025-06-05T10:00:00+02:00",
                  "sistOppdatertTidspunkt":"2025-06-05T10:00:00+02:00"
                }
                """
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
    requestBody = OpenApiRequestBody([OpenApiContent(
        OpprettInnleggRequestDto::class,
        example = """
        {
          "tittel":"Velkommen!",
          "opprettetAvPersonNavident":"A123456",
          "opprettetAvPersonNavn":"Ola Nordmann",
          "opprettetAvPersonBeskrivelse":"Veileder",
          "sendesTilJobbsokerTidspunkt":"2025-06-05T12:00:00+02:00",
          "htmlContent":"<p>Hei og velkommen til treffet!</p>"
        }
        """
    )]),
    responses = [OpenApiResponse(
        "201", [OpenApiContent(
            InnleggResponseDto::class,
            example = """
            {
              "id":"11111111-2222-3333-4444-555555555555",
              "treffId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
              "tittel":"Velkommen!",
              "opprettetAvPersonNavident":"A123456",
              "opprettetAvPersonNavn":"Ola Nordmann",
              "opprettetAvPersonBeskrivelse":"Veileder",
              "sendesTilJobbsokerTidspunkt":"2025-06-05T12:00:00+02:00",
              "htmlContent":"<p>Hei og velkommen til treffet!</p>",
              "opprettetTidspunkt":"2025-06-05T10:00:00+02:00",
              "sistOppdatertTidspunkt":"2025-06-05T10:00:00+02:00"
            }
            """
        )]
    ),
        OpenApiResponse("404")
    ],
    path = INNLEGG_BASE_PATH,
    methods = [HttpMethod.POST]
)
private fun opprettInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
    val body    = ctx.bodyAsClass<OpprettInnleggRequestDto>()
    try {
        val innlegg = repo.opprett(treffId, body)
        ctx.status(HTTP_CREATED).json(innlegg.toResponseDto())
    } catch (e: IllegalStateException) {
        if (e.message?.contains("finnes ikke") == true)
            throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somUuid} ikke funnet.")
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
    requestBody = OpenApiRequestBody([OpenApiContent(
        OpprettInnleggRequestDto::class,
        example = """
        {
          "tittel":"Oppdatert tittel",
          "opprettetAvPersonNavident":"A123456",
          "opprettetAvPersonNavn":"Ola Nordmann",
          "opprettetAvPersonBeskrivelse":"Veileder",
          "sendesTilJobbsokerTidspunkt":"2025-06-05T13:00:00+02:00",
          "htmlContent":"<p>Oppdatert innhold</p>"
        }
        """
    )]),
    responses = [
        OpenApiResponse(
            "200", [OpenApiContent(
                InnleggResponseDto::class,
                example = """
                {
                  "id":"11111111-2222-3333-4444-555555555555",
                  "treffId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                  "tittel":"Oppdatert tittel",
                  "opprettetAvPersonNavident":"A123456",
                  "opprettetAvPersonNavn":"Ola Nordmann",
                  "opprettetAvPersonBeskrivelse":"Veileder",
                  "sendesTilJobbsokerTidspunkt":"2025-06-05T13:00:00+02:00",
                  "htmlContent":"<p>Oppdatert innhold</p>",
                  "opprettetTidspunkt":"2025-06-05T10:00:00+02:00",
                  "sistOppdatertTidspunkt":"2025-06-05T13:00:00+02:00"
                }
                """
            )]
        ),
        OpenApiResponse(
            "201", [OpenApiContent( InnleggResponseDto::class )] // eksempelet over holder
        ),
        OpenApiResponse("404")
    ],
    path = INNLEGG_ITEM_PATH,
    methods = [HttpMethod.PUT]
)
private fun oppdaterEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    val treffId   = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
    val innleggId = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
    val body      = ctx.bodyAsClass<OpprettInnleggRequestDto>()

    try {
        val (innlegg, created) = repo.oppdater(innleggId, treffId, body)
        ctx.status(if (created) HTTP_CREATED else HTTP_OK).json(innlegg.toResponseDto())
    } catch (e: IllegalStateException) {
        if (e.message?.contains("finnes ikke") == true)
            throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somUuid} ikke funnet.")
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
    if (repo.slett(id)) ctx.status(204) else throw NotFoundResponse()
}
