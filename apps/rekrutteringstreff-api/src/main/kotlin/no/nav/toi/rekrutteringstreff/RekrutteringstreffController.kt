package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.dto.FellesHendelseOutboundDto
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import java.time.ZonedDateTime
import java.util.*

class RekrutteringstreffController(
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val rekrutteringstreffService: RekrutteringstreffService,
    javalin: Javalin
) {
    companion object {
        private const val pathParamTreffId = "id"
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val hendelserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/hendelser"
        private const val publiserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/publiser"
        private const val gjenapnPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/gjenapn"
        private const val fullforPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/fullfor"
        private const val avlysPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/avlys"
        private const val avpubliserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/avpubliser"
        private const val fellesPath =
            "$endepunktRekrutteringstreff/{$pathParamTreffId}/allehendelser"
    }

    init {
        javalin.post(endepunktRekrutteringstreff, opprettRekrutteringstreffHandler())
        javalin.get(endepunktRekrutteringstreff, hentAlleRekrutteringstreffHandler())
        javalin.get("${endepunktRekrutteringstreff}/{id}", hentRekrutteringstreffHandler())
        javalin.put("${endepunktRekrutteringstreff}/{id}", oppdaterRekrutteringstreffHandler())
        javalin.delete("${endepunktRekrutteringstreff}/{id}", slettRekrutteringstreffHandler())
        javalin.get(hendelserPath, hentHendelserHandler())
        javalin.get(fellesPath, hentAlleHendelserHandler())
        javalin.post(publiserPath, publiserRekrutteringstreffHandler())
        javalin.post(gjenapnPath, gjenåpneRekrutteringstreffHandler())
        javalin.post(avlysPath, avlysRekrutteringstreffHandler())
        javalin.post(avpubliserPath, avpubliserRekrutteringstreffHandler())
        javalin.post(fullforPath, fullforRekrutteringstreffHandler())
    }

    @OpenApi(
        summary = "Opprett rekrutteringstreff",
        operationId = "opprettRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(
                from = OpprettRekrutteringstreffDto::class,
                example = """
                    {
                        "opprettetAvNavkontorEnhetId": "0315"
                    }
                """
            )]
        ),
        responses = [OpenApiResponse(
            status = "201",
            content = [OpenApiContent(
                from = Map::class,
                example = """{"id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"}"""
            )]
        )],
        path = endepunktRekrutteringstreff,
        methods = [HttpMethod.POST]
    )
    private fun opprettRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val inputDto = ctx.bodyAsClass<OpprettRekrutteringstreffDto>()
        val internalDto = OpprettRekrutteringstreffInternalDto(
            tittel = inputDto.tittel,
            opprettetAvPersonNavident = ctx.extractNavIdent(),
            opprettetAvNavkontorEnhetId = inputDto.opprettetAvNavkontorEnhetId,
            opprettetAvTidspunkt = ZonedDateTime.now(),
        )
        val id = rekrutteringstreffRepository.opprett(internalDto)
        ctx.status(201).json(mapOf("id" to id.toString()))
    }

    @OpenApi(
        summary = "Hent alle rekrutteringstreff",
        operationId = "hentAlleRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = Array<RekrutteringstreffDto>::class,
                example = """[
                    {
                        "id": "d6a587cd-8797-4b9a-a68b-575373f16d65",
                        "tittel": "Sommerjobbtreff",
                        "beskrivelse": "Beskrivelse av Sommerjobbtreff",
                        "fraTid": "2025-06-15T09:00:00+02:00",
                        "tilTid": "2025-06-15T11:00:00+02:00",
                        "svarfrist": "2025-06-14T11:00:00+02:00",
                        "gateadresse": "Malmøgata 1",
                        "postnummer": "0566",
                        "poststed": "Oslo",
                        "status": "UTKAST",
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
    private fun hentAlleRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        ctx.status(200).json(rekrutteringstreffService.hentAlleRekrutteringstreff())
    }

    @OpenApi(
        summary = "Hent ett rekrutteringstreff",
        operationId = "hentRekrutteringstreff",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = RekrutteringstreffDetaljOutboundDto::class,
                example = """{
                   "rekrutteringstreff":
                    {
                       "id":"d6a587cd-8797-4b9a-a68b-575373f16d65",
                       "tittel":"Sommerjobbtreff",
                       "beskrivelse":null,
                       "fraTid":null,
                       "tilTid":null,
                       "svarfrist":null,
                       "gateadresse": null,
                       "postnummer": null,
                       "poststed": null,
                       "status":"UTKAST",
                       "opprettetAvPersonNavident":"A123456",
                       "opprettetAvNavkontorEnhetId":"0318",
                       "opprettetAvTidspunkt":"2025-06-01T08:00:00+02:00",
                    },
                   "hendelser":[
                     {
                       "id":"any-uuid",
                       "tidspunkt":"2025-06-01T08:00:00Z",
                       "hendelsestype":"OPPRETTET",
                       "opprettetAvAktørType":"ARRANGØR",
                       "aktørIdentifikasjon":"A123456"
                     }
                   ]
                }"""
            )]
        )],
        path = "$endepunktRekrutteringstreff/{id}",
        methods = [HttpMethod.GET]
    )
    private fun hentRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER)
        val id = TreffId(ctx.pathParam(pathParamTreffId))
        rekrutteringstreffRepository.hentMedHendelser(id)?.let { ctx.status(200).json(it) }
            ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
    }

    @OpenApi(
        summary = "Oppdater ett rekrutteringstreff",
        operationId = "oppdaterRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            required = true,
            description = "Rekrutteringstreffets unike identifikator (UUID)"
        )],
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(
                from = OppdaterRekrutteringstreffDto::class,
                example = """{
                    "tittel": "Oppdatert tittel", 
                    "beskrivelse": "Oppdatert beskrivelse",
                    "fraTid": "2025-06-15T09:30:00+02:00", 
                    "tilTid": "2025-06-15T11:30:00+02:00", 
                    "svarfrist": "2025-06-14T11:30:00+02:00", 
                    "gateadresse": "Malmøgata 1",
                    "postnummer": "0566",
                    "poststed": "Oslo"
                }"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = RekrutteringstreffDto::class,
                example = """{
                    "id": "d6a587cd-8797-4b9a-a68b-575373f16d65", 
                    "tittel": "Oppdatert tittel", 
                    "beskrivelse": "Oppdatert beskrivelse", 
                    "fraTid": "2025-06-15T09:30:00+02:00", 
                    "tilTid": "2025-06-15T11:30:00+02:00", 
                    "svarfrist": "2025-06-14T11:30:00+02:00", 
                    "gateadresse": "Malmøgata 1",
                    "postnummer": "0566",
                    "poststed": "Oslo",
                    "status": "UTKAST", 
                    "opprettetAvPersonNavident": "A123456", 
                    "opprettetAvNavkontorEnhetId": "0318",
                    "opprettetAvTidspunkt": "2025-06-01T08:00:00+02:00"
                }"""
            )]
        )],
        path = "$endepunktRekrutteringstreff/{id}",
        methods = [HttpMethod.PUT]
    )
    private fun oppdaterRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val id = TreffId(ctx.pathParam("id"))
        val dto = ctx.bodyAsClass<OppdaterRekrutteringstreffDto>()
        rekrutteringstreffRepository.oppdater(id, dto, ctx.extractNavIdent())
        val updated = rekrutteringstreffService.hentRekrutteringstreff(id)
        ctx.status(200).json(updated)
    }

    @OpenApi(
        summary = "Slett ett rekrutteringstreff",
        operationId = "slettRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            required = true,
            description = "Rekrutteringstreffets unike identifikator (UUID)"
        )],
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
    private fun slettRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val id = TreffId(ctx.pathParam("id"))
        rekrutteringstreffRepository.slett(id)
        ctx.status(200).result("Rekrutteringstreff slettet")
    }

    @OpenApi(
        summary = "Hent hendelser for rekrutteringstreff, nyeste først",
        operationId = "hentRekrutteringstreffHendelser",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = Array<RekrutteringstreffHendelseOutboundDto>::class)]
        )],
        path = hendelserPath,
        methods = [HttpMethod.GET]
    )
    private fun hentHendelserHandler(): (Context) -> Unit = { ctx ->
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val list = rekrutteringstreffRepository.hentHendelser(treff).map {
            RekrutteringstreffHendelseOutboundDto(
                id = it.id.toString(),
                tidspunkt = it.tidspunkt,
                hendelsestype = it.hendelsestype.name,
                opprettetAvAktørType = it.opprettetAvAktørType.name,
                aktørIdentifikasjon = it.aktørIdentifikasjon
            )
        }
        ctx.status(200).json(list)
    }

    @OpenApi(
        summary = "Hent ALLE hendelser for et rekrutteringstreff (jobbsøker, arbeidsgiver, treff)",
        operationId = "hentAlleHendelser",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = Array<FellesHendelseOutboundDto>::class)]
        )],
        path = fellesPath,
        methods = [HttpMethod.GET]
    )
    private fun hentAlleHendelserHandler(): (Context) -> Unit = { ctx ->
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val list  = rekrutteringstreffRepository.hentAlleHendelser(treff)
        ctx.status(200).json(list)
    }

    @OpenApi(
        summary = "Publiserer et rekrutteringstreff ved å legge til en publiseringshendelse.",
        operationId = "publiserRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = publiserPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
        responses = [OpenApiResponse(status = "200", description = "Publiseringshendelse er lagt til.")]
    )
    private fun publiserRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()

        rekrutteringstreffRepository.publiser(treffId, navIdent)
        ctx.status(200)
    }

    @OpenApi(
        summary = "Gjenåpner et rekrutteringstreff ved å legge til en gjenåpningshendelse.",
        operationId = "gjenapnRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = gjenapnPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
        responses = [OpenApiResponse(status = "200", description = "Gjenåpningshendelse er lagt til.")]
    )
    private fun gjenåpneRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        rekrutteringstreffRepository.gjenåpne(treffId, navIdent)
        ctx.status(200)
    }

    @OpenApi(
        summary = "Avlyser et rekrutteringstreff ved å legge til en avlysingshendelse.",
        operationId = "avlysRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = avlysPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
        responses = [OpenApiResponse(status = "200", description = "Avlysningshendelse er lagt til.")]
    )
    private fun avlysRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        rekrutteringstreffService.avlys(treffId, navIdent)
        ctx.status(200)
    }

    @OpenApi(
        summary = "Avpubliserer et rekrutteringstreff ved å legge til en avpubliseringshendelse.",
        operationId = "avpubliserRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = avpubliserPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
        responses = [OpenApiResponse(status = "200", description = "Avpubliseringshendelse er lagt til.")]
    )
    private fun avpubliserRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        rekrutteringstreffRepository.avpubliser(treffId, navIdent)
        ctx.status(200)
    }

    @OpenApi(
        summary = "Fullfører et rekrutteringstreff ved å legge til en fullføringshendelse.",
        operationId = "fullforRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = fullforPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
        responses = [OpenApiResponse(status = "200", description = "Fullføringshendelse er lagt til.")]
    )
    private fun fullforRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        rekrutteringstreffService.fullfør(treffId, navIdent)
        ctx.status(200)
    }
}
