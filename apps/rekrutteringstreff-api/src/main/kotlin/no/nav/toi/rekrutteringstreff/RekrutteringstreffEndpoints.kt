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
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.OpenAiClient
import java.time.ZonedDateTime
import java.util.*
import kotlin.text.get

private const val pathParamTreffId = "id"
const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
private const val hendelserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/hendelser"
private const val publiserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/publiser"
private const val avsluttInvitasjonPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/avslutt-invitasjon"
private const val avsluttArrangementPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/avslutt-arrangement"
private const val avsluttOppfolgingPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/avslutt-oppfolging"
private const val avsluttPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/avslutt"
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
        tittel = "Nytt rekrutteringstreff",
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
            from = Array<RekrutteringstreffDTO>::class,
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
private fun hentAlleRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    ctx.status(200).json(repo.hentAlle().map { it.tilRekrutteringstreffDTO() })
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
                   "hendelsestype":"OPPRETT",
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
            from = RekrutteringstreffDTO::class,
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
private fun oppdaterRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val id = TreffId(ctx.pathParam("id"))
    val dto = ctx.bodyAsClass<OppdaterRekrutteringstreffDto>()
    repo.oppdater(id, dto, ctx.extractNavIdent())
    val updated = repo.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet etter oppdatering")
    ctx.status(200).json(updated.tilRekrutteringstreffDTO())
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
private fun validerRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
    val dto = ctx.bodyAsClass<ValiderRekrutteringstreffDto>()
    val validationResult = OpenAiClient.validateRekrutteringstreff(dto)
    ctx.status(200).json(validationResult)
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
    summary = "Avslutter invitasjonsfasen for et rekrutteringstreff.",
    operationId = "avsluttInvitasjon",
    security = [OpenApiSecurity(name = "BearerAuth")],
    path = avsluttInvitasjonPath,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
    responses = [OpenApiResponse(status = "200", description = "Hendelse for avsluttet invitasjon er lagt til.")]
)
private fun avsluttInvitasjonHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()
    repo.avsluttInvitasjon(treffId, navIdent)
    ctx.status(200)
}

@OpenApi(
    summary = "Avslutter arrangementsfasen for et rekrutteringstreff.",
    operationId = "avsluttArrangement",
    security = [OpenApiSecurity(name = "BearerAuth")],
    path = avsluttArrangementPath,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
    responses = [OpenApiResponse(status = "200", description = "Hendelse for avsluttet arrangement er lagt til.")]
)
private fun avsluttArrangementHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()
    repo.avsluttArrangement(treffId, navIdent)
    ctx.status(200)
}

@OpenApi(
    summary = "Avslutter oppfølgingsfasen for et rekrutteringstreff.",
    operationId = "avsluttOppfolging",
    security = [OpenApiSecurity(name = "BearerAuth")],
    path = avsluttOppfolgingPath,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
    responses = [OpenApiResponse(status = "200", description = "Hendelse for avsluttet oppfølging er lagt til.")]
)
private fun avsluttOppfolgingHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()
    repo.avsluttOppfolging(treffId, navIdent)
    ctx.status(200)
}

@OpenApi(
    summary = "Avslutter et rekrutteringstreff.",
    operationId = "avsluttRekrutteringstreff",
    security = [OpenApiSecurity(name = "BearerAuth")],
    path = avsluttPath,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet")],
    responses = [OpenApiResponse(status = "200", description = "Hendelse for avsluttet rekrutteringstreff er lagt til.")]
)
private fun avsluttRekrutteringstreffHandler(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val navIdent = ctx.extractNavIdent()
    repo.avslutt(treffId, navIdent)
    ctx.status(200)
}

fun Javalin.handleRekrutteringstreff(repo: RekrutteringstreffRepository) {
    post(endepunktRekrutteringstreff, opprettRekrutteringstreffHandler(repo))
    get(endepunktRekrutteringstreff, hentAlleRekrutteringstreffHandler(repo))
    get("$endepunktRekrutteringstreff/{id}", hentRekrutteringstreffHandler(repo))
    put("$endepunktRekrutteringstreff/{id}", oppdaterRekrutteringstreffHandler(repo))
    delete("$endepunktRekrutteringstreff/{id}", slettRekrutteringstreffHandler(repo))
    post("$endepunktRekrutteringstreff/valider", validerRekrutteringstreffHandler())
    get(hendelserPath, hentHendelserHandler(repo))
    get(fellesPath, hentAlleHendelserHandler(repo))
    post(publiserPath, publiserRekrutteringstreffHandler(repo))
    post(avsluttInvitasjonPath, avsluttInvitasjonHandler(repo))
    post(avsluttArrangementPath, avsluttArrangementHandler(repo))
    post(avsluttOppfolgingPath, avsluttOppfolgingHandler(repo))
    post(avsluttPath, avsluttRekrutteringstreffHandler(repo))
    handleEiere(repo.eierRepository)
    handleInnlegg(repo.innleggRepository)
}

data class RekrutteringstreffDTO(
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
)

data class OpprettRekrutteringstreffDto(
    val opprettetAvNavkontorEnhetId: String,
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

data class ValiderRekrutteringstreffDto(
    val tittel: String?,
    val beskrivelse: String?
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
