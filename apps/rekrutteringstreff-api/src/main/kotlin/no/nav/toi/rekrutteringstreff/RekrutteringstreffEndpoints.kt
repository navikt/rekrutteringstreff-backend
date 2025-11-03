package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.eier.handleEiere
import no.nav.toi.rekrutteringstreff.innlegg.handleInnlegg
import java.time.ZonedDateTime
import java.util.*

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
private fun opprettRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val inputDto = ctx.bodyAsClass<OpprettRekrutteringstreffDto>()
    val internalDto = OpprettRekrutteringstreffInternalDto(
        tittel = inputDto.tittel,
        opprettetAvPersonNavident = ctx.extractNavIdent(),
        opprettetAvNavkontorEnhetId = inputDto.opprettetAvNavkontorEnhetId,
        opprettetAvTidspunkt = ZonedDateTime.now(),
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
private fun hentAlleRekrutteringstreffHandler(service: RekrutteringstreffService): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    ctx.status(200).json(service.hentAlleRekrutteringstreff())
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
               "id":"d6a587cd-8797-4b9a-a68b-575373f16d65",
               "tittel":"Sommerjobbtreff",
               "beskrivelse":null,
               "fraTid":null,
               "tilTid":null,
               "svarfrist":null,
               "gateadresse": null,
               "postnummer": null,
               "poststed": null,
               "status":"Utkast",
               "opprettetAvPersonNavident":"A123456",
               "opprettetAvNavkontorEnhetId":"0318",
               "opprettetAvTidspunkt":"2025-06-01T08:00:00+02:00",
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
private fun hentRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER)
    val id = TreffId(ctx.pathParam(pathParamTreffId))
    repo.hentMedHendelser(id)?.let { ctx.status(200).json(it) }
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
private fun oppdaterRekrutteringstreffHandler(repo: RekrutteringstreffRepository, service: RekrutteringstreffService): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val id = TreffId(ctx.pathParam("id"))
    val dto = ctx.bodyAsClass<OppdaterRekrutteringstreffDto>()
    repo.oppdater(id, dto, ctx.extractNavIdent())
    val updated = service.hentRekrutteringstreff(id)
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
private fun slettRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val id = TreffId(ctx.pathParam("id"))
    repo.slett(id)
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
private fun hentHendelserHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    val list = repo.hentHendelser(treff).map {
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
private fun hentAlleHendelserHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    val list  = repo.hentAlleHendelser(treff)
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
private fun publiserRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()

    repo.publiser(treffId, navIdent)
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
private fun gjenapnRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()
    repo.gjenapn(treffId, navIdent)
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
private fun avlysRekrutteringstreffHandler(service: RekrutteringstreffService): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()
    service.avlys(treffId, navIdent)
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
private fun avpubliserRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()
    repo.avpubliser(treffId, navIdent)
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
private fun fullforRekrutteringstreffHandler(service: RekrutteringstreffService): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()
    service.fullfør(treffId, navIdent)
    ctx.status(200)
}

fun Javalin.handleRekrutteringstreff(repo: RekrutteringstreffRepository, service: RekrutteringstreffService) {
    post(endepunktRekrutteringstreff, opprettRekrutteringstreffHandler(repo))
    get(endepunktRekrutteringstreff, hentAlleRekrutteringstreffHandler(service))
    get("$endepunktRekrutteringstreff/{id}", hentRekrutteringstreffHandler(repo))
    put("$endepunktRekrutteringstreff/{id}", oppdaterRekrutteringstreffHandler(repo, service))
    delete("$endepunktRekrutteringstreff/{id}", slettRekrutteringstreffHandler(repo))
    get(hendelserPath, hentHendelserHandler(repo))
    get(fellesPath, hentAlleHendelserHandler(repo))
    post(publiserPath, publiserRekrutteringstreffHandler(repo))
    post(gjenapnPath, gjenapnRekrutteringstreffHandler(repo))
    post(avlysPath, avlysRekrutteringstreffHandler(service))
    post(avpubliserPath, avpubliserRekrutteringstreffHandler(repo))
    post(fullforPath, fullforRekrutteringstreffHandler(service))
    handleEiere(repo.eierRepository)
    handleInnlegg(repo.innleggRepository)

}

data class RekrutteringstreffDto(
    val id: UUID,
    val tittel: String,
    val beskrivelse: String?,
    val fraTid: ZonedDateTime?,
    val tilTid: ZonedDateTime?,
    val svarfrist: ZonedDateTime?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val status: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
    val antallArbeidsgivere: Int,
    val antallJobbsøkere: Int,
)

data class OpprettRekrutteringstreffDto(
    val opprettetAvNavkontorEnhetId: String,
    val tittel: String = "Nytt rekrutteringstreff"
)

data class OpprettRekrutteringstreffInternalDto( // TODO Are: Finne et bedre navn? Ligger den i feil klasse?
    val tittel: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
)

data class OppdaterRekrutteringstreffDto(
    val tittel: String,
    val beskrivelse: String?,
    val fraTid: ZonedDateTime?,
    val tilTid: ZonedDateTime?,
    val svarfrist: ZonedDateTime?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?
)

data class ValiderRekrutteringstreffResponsDto(
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String
)

data class FellesHendelseOutboundDto(
    val id: String,
    val ressurs: HendelseRessurs,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?
)
