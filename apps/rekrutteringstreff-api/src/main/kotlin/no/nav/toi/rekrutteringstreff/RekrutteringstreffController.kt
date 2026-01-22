package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.JacksonConfig
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.dto.*
import no.nav.toi.rekrutteringstreff.eier.EierService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.*

class RekrutteringstreffController(
    private val rekrutteringstreffService: RekrutteringstreffService,
    private val eierService: EierService,
    javalin: Javalin
) {
    companion object {
        private const val pathParamTreffId = "id"
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val endepunktRekrutteringstreffMittKontor = "$endepunktRekrutteringstreff/mittkontor"
        private const val hendelserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/hendelser"
        private const val publiserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/publiser"
        private const val gjenapnPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/gjenapn"
        private const val fullforPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/fullfor"
        private const val avlysPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/avlys"
        private const val avpubliserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/avpubliser"
        private const val endringerPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/endringer"

        private const val fellesPath =
            "$endepunktRekrutteringstreff/{$pathParamTreffId}/allehendelser"

        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    init {
        javalin.post(endepunktRekrutteringstreff, opprettRekrutteringstreffHandler())
        javalin.get(endepunktRekrutteringstreff, hentAlleRekrutteringstreffHandler())
        javalin.get(endepunktRekrutteringstreffMittKontor, hentAlleRekrutteringstreffForMittKontorHandler())
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
        javalin.post(endringerPath, registrerEndringHandler(rekrutteringstreffService))
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
        val id = rekrutteringstreffService.opprett(internalDto)
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
                        "opprettetAvTidspunkt": "2025-06-01T08:00:00+02:00",
                        "antallArbeidsgivere":1,
                        "antallJobbsøkere":1
                    }
                ]"""
            )]
        )],
        path = endepunktRekrutteringstreff,
        methods = [HttpMethod.GET]
    )
    private fun hentAlleRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)
        log.info("Henter alle rekrutteringstreff")
        ctx.status(200).json(rekrutteringstreffService.hentAlleRekrutteringstreff())
    }

       @OpenApi(
        summary = "Hent alle rekrutteringstreff for mitt kontor",
        operationId = "hentAlleRekrutteringstreffForMittKontor",
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
                        "opprettetAvTidspunkt": "2025-06-01T08:00:00+02:00",
                        "antallArbeidsgivere":1,
                        "antallJobbsøkere":1
                    }
                ]"""
            )]
        )],
        path = endepunktRekrutteringstreffMittKontor,
        methods = [HttpMethod.GET]
    )
    private fun hentAlleRekrutteringstreffForMittKontorHandler(): (Context) -> Unit = { ctx ->
       ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)
       val kontorId = ctx.authenticatedUser().extractKontorId()
       if (kontorId.isNullOrEmpty() && ctx.authenticatedUser().erUtvikler()) {
           log.info("Henter alle rekrutteringstreff for kontor $kontorId")
           log.info("Utvikler som ikke har valgt et kontor - henter alle rekrutteringstreff")
           ctx.status(200).json(rekrutteringstreffService.hentAlleRekrutteringstreff())
       }
       if (kontorId.isNullOrEmpty()) {
           throw BadRequestResponse("Veileders kontor er ikke tilgjengelig")
       }
       log.info("Henter alle rekrutteringstreff for kontor $kontorId")
       ctx.status(200).json(rekrutteringstreffService.hentAlleRekrutteringstreffForEttKontor(kontorId))
    }

    @OpenApi(
        summary = "Hent ett rekrutteringstreff",
        operationId = "hentRekrutteringstreff",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = RekrutteringstreffDto::class,
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
                    "status":"UTKAST",
                    "opprettetAvPersonNavident":"A123456",
                    "opprettetAvNavkontorEnhetId":"0318",
                    "opprettetAvTidspunkt":"2025-06-01T08:00:00+02:00",
                    "antallArbeidsgivere":1,
                    "antallJobbsøkere":1
                }
                """
            )]
        )],
        path = "$endepunktRekrutteringstreff/{id}",
        methods = [HttpMethod.GET]
    )
    private fun hentRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER, Rolle.JOBBSØKER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        rekrutteringstreffService.hentRekrutteringstreff(treffId).let { ctx.status(200).json(it) }
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
                    "opprettetAvTidspunkt": "2025-06-01T08:00:00+02:00",
                    "antallArbeidsgivere":1,
                    "antallJobbsøkere":1
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

        val navIdent = ctx.extractNavIdent()

        if (eierService.erEierEllerUtvikler(treffId = id, navIdent = navIdent, context = ctx)) {
            rekrutteringstreffService.oppdater(id, dto, navIdent)
            val updated = rekrutteringstreffService.hentRekrutteringstreff(id)
            ctx.status(200).json(updated)
        } else {
            throw ForbiddenResponse("Bruker er ikke eier av rekrutteringstreffet og kan ikke oppdatere det")
        }
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
        val treffId = TreffId(ctx.pathParam("id"))
        val navIdent = ctx.extractNavIdent()
        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            log.info("Sletter rekrutteringstreff med id $treffId")
            rekrutteringstreffService.markerSlettet(treffId, navIdent)
            ctx.status(200).result("Rekrutteringstreff slettet")
        } else {
            throw ForbiddenResponse("Bruker er ikke eier av rekrutteringstreffet og kan ikke slette det")
        }
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
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        if (eierService.erEierEllerUtvikler(treffId = treff, navIdent = navIdent, context = ctx)) {
            val list = rekrutteringstreffService.hentHendelser(treff).map {
                RekrutteringstreffHendelseOutboundDto(
                    id = it.id.toString(),
                    tidspunkt = it.tidspunkt,
                    hendelsestype = it.hendelsestype.name,
                    opprettetAvAktørType = it.opprettetAvAktørType.name,
                    aktørIdentifikasjon = it.aktørIdentifikasjon
                )
            }
            ctx.status(200).json(list)
        } else {
            throw ForbiddenResponse("Personen har ikke tilgang til å hente rekrutteringstreffhendelser for rekrutteringstreff ${treff.somString}")
        }
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
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.authenticatedUser().extractNavIdent()
        if (eierService.erEierEllerUtvikler(treffId = treff, navIdent = navIdent, context = ctx)) {
            val list = rekrutteringstreffService.hentAlleHendelser(treff)
            ctx.status(200).json(list)
        } else {
            throw ForbiddenResponse("Personen har ikke tilgang til å hente alle hendelser for rekrutteringstreff ${treff.somString}")
        }
    }

    @OpenApi(
        summary = "Publiserer et rekrutteringstreff ved å legge til en publiseringshendelse.",
        operationId = "publiserRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = publiserPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            description = "ID for rekrutteringstreffet"
        )],
        responses = [OpenApiResponse(status = "200", description = "Publiseringshendelse er lagt til.")]
    )
    private fun publiserRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            rekrutteringstreffService.publiser(treffId, navIdent)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Personen har ikke tilgang til å publisere treffet ${treffId.somString}")
        }
    }

    @OpenApi(
        summary = "Gjenåpner et rekrutteringstreff ved å legge til en gjenåpningshendelse.",
        operationId = "gjenapnRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = gjenapnPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            description = "ID for rekrutteringstreffet"
        )],
        responses = [OpenApiResponse(status = "200", description = "Gjenåpningshendelse er lagt til.")]
    )
    private fun gjenåpneRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()

        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            rekrutteringstreffService.gjenåpne(treffId, navIdent)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Personen har ikke tilgang til å gjenåpne treffet ${treffId.somString}")
        }
    }

    @OpenApi(
        summary = "Avlyser et rekrutteringstreff ved å legge til en avlysingshendelse.",
        operationId = "avlysRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = avlysPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            description = "ID for rekrutteringstreffet"
        )],
        responses = [OpenApiResponse(status = "200", description = "Avlysningshendelse er lagt til.")]
    )
    private fun avlysRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            rekrutteringstreffService.avlys(treffId, navIdent)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Personen har ikke tilgang til å avlyse treffet ${treffId.somString}")
        }

    }

    @OpenApi(
        summary = "Avpubliserer et rekrutteringstreff ved å legge til en avpubliseringshendelse.",
        operationId = "avpubliserRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = avpubliserPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            description = "ID for rekrutteringstreffet"
        )],
        responses = [OpenApiResponse(status = "200", description = "Avpubliseringshendelse er lagt til.")]
    )
    private fun avpubliserRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            rekrutteringstreffService.avpubliser(treffId, navIdent)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Personen har ikke tilgang til å avpublisere treffet ${treffId.somString}")
        }
    }

    @OpenApi(
        summary = "Fullfører et rekrutteringstreff ved å legge til en fullføringshendelse.",
        operationId = "fullforRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = fullforPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            description = "ID for rekrutteringstreffet"
        )],
        responses = [OpenApiResponse(status = "200", description = "Fullføringshendelse er lagt til.")]
    )
    private fun fullforRekrutteringstreffHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            rekrutteringstreffService.fullfør(treffId, navIdent)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Personen har ikke tilgang til å fullføre treffet ${treffId.somString}")
        }
    }

    @OpenApi(
        summary = "Registrer endringer til et publisert rekrutteringstreff",
        operationId = "registrerEndring",
        security = [OpenApiSecurity(name = "BearerAuth")],
        path = endringerPath,
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            description = "ID for rekrutteringstreffet"
        )],
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(
                from = Rekrutteringstreffendringer::class,
                example = """{
                    "navn": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel", "skalVarsle": true},
                    "tidspunkt": {"gammelVerdi": "2025-06-15T09:00:00+02:00 til 2025-06-15T11:00:00+02:00", "nyVerdi": "2025-06-15T10:00:00+02:00 til 2025-06-15T12:00:00+02:00", "skalVarsle": true},
                    "svarfrist": {"gammelVerdi": "2025-06-10T23:59:00+02:00", "nyVerdi": "2025-06-12T23:59:00+02:00", "skalVarsle": true},
                    "sted": {"gammelVerdi": "Gammel gate 123, 0566 Oslo", "nyVerdi": "Ny gate 123, 0567 Oslo", "skalVarsle": true},
                    "introduksjon": {"gammelVerdi": "Gammel introduksjon", "nyVerdi": "Ny introduksjon", "skalVarsle": true}
            }"""
            )]
        ),
        responses = [
            OpenApiResponse(status = "201", description = "Endringer er registrert."),
            OpenApiResponse(status = "400", description = "Kan kun registrere endringer for treff som har publisert status."),
            OpenApiResponse(status = "403", description = "Bruker har ikke tilgang til å registrere endringer for treffet."),
            OpenApiResponse(status = "404", description = "Rekrutteringstreff ikke funnet.")]
    )
    private fun registrerEndringHandler(
        service: RekrutteringstreffService
    ): (Context) -> Unit {
        return { ctx ->
            ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
            val treffId = TreffId(ctx.pathParam(pathParamTreffId))
            val navIdent = ctx.extractNavIdent()

            if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
                val treff = service.hentRekrutteringstreff(treffId)
                if (treff.status != RekrutteringstreffStatus.PUBLISERT) {
                    throw BadRequestResponse("Kan kun registrere endringer for treff som har publisert status")
                }

                val endringer = ctx.bodyAsClass<Rekrutteringstreffendringer>()
                val endringerJson = JacksonConfig.mapper.writeValueAsString(endringer)

                service.registrerEndring(treffId, endringerJson, navIdent)
                ctx.status(201)
            } else {
                throw ForbiddenResponse("Personen har ikke tilgang til å registrere endringer for treffet ${treffId.somString}")
            }
        }
    }
}
