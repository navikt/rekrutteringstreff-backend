package no.nav.toi.rekrutteringstreff.innlegg

import io.javalin.openapi.*
import io.javalin.http.Context
import io.javalin.Javalin
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.ki.KiValideringsService
import java.net.HttpURLConnection.*
import java.util.UUID


class InnleggController(
    private val innleggRepository: InnleggRepository,
    private val kiValideringsService: KiValideringsService,
    javalin: Javalin
) {
    companion object {
        private const val REKRUTTERINGSTREFF_ID_PARAM = "rekrutteringstreffId"
        private const val INNLEGG_ID_PARAM            = "innleggId"

        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

        private const val INNLEGG_BASE_PATH = "$endepunktRekrutteringstreff/{$REKRUTTERINGSTREFF_ID_PARAM}/innlegg"
        private const val INNLEGG_ITEM_PATH = "$INNLEGG_BASE_PATH/{$INNLEGG_ID_PARAM}"
    }

    init {
        javalin.get(INNLEGG_BASE_PATH, hentAlleInnleggForTreff())
        javalin.get(INNLEGG_ITEM_PATH, hentEttInnlegg())
        javalin.post(INNLEGG_BASE_PATH, opprettInnlegg())
        javalin.put(INNLEGG_ITEM_PATH, oppdaterEttInnlegg())
        javalin.delete(INNLEGG_ITEM_PATH, slettEttInnlegg())
    }

    @OpenApi(
        summary = "Hent alle innlegg for et rekrutteringstreff",
        operationId = "hentAlleInnleggForTreff",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true)],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = Array<InnleggResponseDto>::class,
                example = """[
                    {
                        "id": "11111111-1111-1111-1111-111111111111",
                        "treffId": "22222222-2222-2222-2222-222222222222",
                        "tittel": "Tittel",
                        "opprettetAvPersonNavident": "A123456",
                        "opprettetAvPersonNavn": "Ola",
                        "opprettetAvPersonBeskrivelse": "Veileder",
                        "sendesTilJobbsokerTidspunkt": "2025-06-07T10:00:00+02:00",
                        "htmlContent": "<p>x</p>",
                        "opprettetTidspunkt": "2025-06-07T09:00:00+02:00",
                        "sistOppdatertTidspunkt": "2025-06-07T09:00:00+02:00"
                    }
                ]"""
            )]
        )],
        path = INNLEGG_BASE_PATH,
        methods = [HttpMethod.GET]
    )
    private fun hentAlleInnleggForTreff(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET, Rolle.BORGER)
        val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
        ctx.json(innleggRepository.hentForTreff(treffId).map(Innlegg::toResponseDto))
    }

    @OpenApi(
        summary = "Hent et innlegg",
        operationId = "hentEttInnlegg",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [
            OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true),
            OpenApiParam(name = INNLEGG_ID_PARAM, type = UUID::class, required = true)
        ],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = InnleggResponseDto::class,
                example = """{
                    "id": "11111111-1111-1111-1111-111111111111",
                    "treffId": "22222222-2222-2222-2222-222222222222",
                    "tittel": "Tittel",
                    "opprettetAvPersonNavident": "A123456",
                    "opprettetAvPersonNavn": "Ola",
                    "opprettetAvPersonBeskrivelse": "Veileder",
                    "sendesTilJobbsokerTidspunkt": "2025-06-07T10:00:00+02:00",
                    "htmlContent": "<p>x</p>",
                    "opprettetTidspunkt": "2025-06-07T09:00:00+02:00",
                    "sistOppdatertTidspunkt": "2025-06-07T09:00:00+02:00"
                }"""
            )]
        )],
        path = INNLEGG_ITEM_PATH,
        methods = [HttpMethod.GET]
    )
    private fun hentEttInnlegg(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET, Rolle.BORGER)
        val id = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
        ctx.json(innleggRepository.hentById(id)?.toResponseDto() ?: throw NotFoundResponse())
    }

    @OpenApi(
        summary = "Opprett nytt innlegg",
        operationId = "opprettInnlegg",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(
                from = OpprettInnleggRequestDto::class,
                example = """{
                    "tittel": "Tittel",
                    "opprettetAvPersonNavn": "Ola",
                    "opprettetAvPersonBeskrivelse": "Veileder",
                    "sendesTilJobbsokerTidspunkt": "2025-06-07T10:00:00+02:00",
                    "htmlContent": "<p>x</p>"
                }"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "201",
            content = [OpenApiContent(
                from = InnleggResponseDto::class,
                example = """{
                    "id": "11111111-1111-1111-1111-111111111111",
                    "treffId": "22222222-2222-2222-2222-222222222222",
                    "tittel": "Tittel",
                    "opprettetAvPersonNavident": "A123456",
                    "opprettetAvPersonNavn": "Ola",
                    "opprettetAvPersonBeskrivelse": "Veileder",
                    "sendesTilJobbsokerTidspunkt": "2025-06-07T10:00:00+02:00",
                    "htmlContent": "<p>x</p>",
                    "opprettetTidspunkt": "2025-06-07T09:00:00+02:00",
                    "sistOppdatertTidspunkt": "2025-06-07T09:00:00+02:00"
                }"""
            )]
        )],
        path = INNLEGG_BASE_PATH,
        methods = [HttpMethod.POST]
    )
    private fun opprettInnlegg(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
        val dto = ctx.bodyAsClass<OpprettInnleggRequestDto>()
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        kiValideringsService.verifiserKiValidering(
            tekst = dto.htmlContent,
            kiLoggId = dto.innleggKiLoggId,
            lagreLikevel = dto.lagreLikevel,
            feltType = "innlegg",
            forventetTreffId = treffId.somUuid
        )

        try {
            ctx.status(HTTP_CREATED).json(innleggRepository.opprett(treffId, dto, navIdent).toResponseDto())
        } catch (e: IllegalStateException) {
            if (e.message?.contains("finnes ikke") == true)
                throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somUuid} ikke funnet.")
            throw e
        }
    }

    @OpenApi(
        summary = "Oppdater et innlegg",
        operationId = "oppdaterInnlegg",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [
            OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true),
            OpenApiParam(name = INNLEGG_ID_PARAM, type = UUID::class, required = true)
        ],
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(
                from = OppdaterInnleggRequestDto::class,
                example = """{
                    "tittel": "Ny tittel",
                    "opprettetAvPersonNavn": "Kari",
                    "opprettetAvPersonBeskrivelse": "Rådgiver",
                    "sendesTilJobbsokerTidspunkt": null,
                    "htmlContent": "<p>y</p>"
                }"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = InnleggResponseDto::class,
                example = """{
                    "id": "11111111-1111-1111-1111-111111111111",
                    "treffId": "22222222-2222-2222-2222-222222222222",
                    "tittel": "Ny tittel",
                    "opprettetAvPersonNavn": "Kari",
                    "opprettetAvPersonBeskrivelse": "Rådgiver",
                    "sendesTilJobbsokerTidspunkt": null,
                    "htmlContent": "<p>y</p>",
                    "opprettetTidspunkt": "2025-06-07T09:00:00+02:00",
                    "sistOppdatertTidspunkt": "2025-06-07T10:00:00+02:00"
                }"""
            )]
        )],
        path = INNLEGG_ITEM_PATH,
        methods = [HttpMethod.PUT]
    )
    private fun oppdaterEttInnlegg(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
        val innleggId = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
        val dto = ctx.bodyAsClass<OppdaterInnleggRequestDto>()

        val eksisterendeInnlegg = innleggRepository.hentById(innleggId)
        if (eksisterendeInnlegg != null && kiValideringsService.erTekstEndret(eksisterendeInnlegg.htmlContent, dto.htmlContent)) {
            kiValideringsService.verifiserKiValidering(
                tekst = dto.htmlContent,
                kiLoggId = dto.innleggKiLoggId,
                lagreLikevel = dto.lagreLikevel,
                feltType = "innlegg",
                forventetTreffId = treffId.somUuid
            )
        }

        try {
            ctx.status(HTTP_OK).json(innleggRepository.oppdater(innleggId, treffId, dto).toResponseDto())
        } catch (e: IllegalStateException) {
            when {
                e.message?.contains("Treff") == true -> throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somUuid} ikke funnet.")
                e.message?.contains("Update") == true -> throw NotFoundResponse("Innlegg med id $innleggId ikke funnet for treff ${treffId.somUuid}.")
                else -> throw e
            }
        }
    }

    @OpenApi(
        summary = "Slett et innlegg",
        operationId = "slettInnlegg",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [
            OpenApiParam(name = REKRUTTERINGSTREFF_ID_PARAM, type = UUID::class, required = true),
            OpenApiParam(name = INNLEGG_ID_PARAM, type = UUID::class, required = true)
        ],
        responses = [OpenApiResponse(status = "204")],
        path = INNLEGG_ITEM_PATH,
        methods = [HttpMethod.DELETE]
    )
    private fun slettEttInnlegg(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val id = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
        if (innleggRepository.slett(id)) ctx.status(HTTP_NO_CONTENT) else throw NotFoundResponse()
    }
}
