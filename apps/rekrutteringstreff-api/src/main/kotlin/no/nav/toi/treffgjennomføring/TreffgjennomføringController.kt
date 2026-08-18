package no.nav.toi.treffgjennomføring

import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.AuditLog
import no.nav.toi.RuteRegistrerer
import no.nav.toi.jobbsoker.oppmøte.OppmøteService
import no.nav.toi.treffgjennomføring.matching.MatchingService
import no.nav.toi.treffgjennomføring.møteplan.MøteplanService
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.eier.krevEierEllerUtvikler
import no.nav.toi.treffgjennomføring.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomføring.dto.InteresseRequestDto
import no.nav.toi.treffgjennomføring.dto.KaskadeAdvarselDto
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomføring.dto.RomDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import java.util.*

class TreffgjennomføringController(
    private val treffgjennomføringService: TreffgjennomføringService,
    private val møteplanService: MøteplanService,
    private val matchingService: MatchingService,
    private val oppmøteService: OppmøteService,
    private val eierService: EierService,
) : RuteRegistrerer {

    companion object {
        private const val basis = "/api/rekrutteringstreff/{id}"
        private const val lesPath = "$basis/treffgjennomforing-og-oppfolging"
        private const val skrivPath = "$basis/treffgjennomforing"

        const val OPPMØTE = "$skrivPath/oppmote"
        const val MØTEOPPSETT = "$skrivPath/moteoppsett"
        const val ROMFORDELING = "$skrivPath/romfordeling"
        const val INTERESSE = "$skrivPath/interesse"
        const val INTERVJUFORDELING = "$skrivPath/intervjufordeling"
        const val FORDEL = "$INTERVJUFORDELING/fordel"
        const val HENT = lesPath

        private const val PERSON_ID = "11111111-1111-1111-1111-111111111111"
        private const val ARBEIDSGIVER_ID = "22222222-2222-2222-2222-222222222222"

        const val AGGREGAT_EKSEMPEL = """{
              "rekrutteringstreffId": "33333333-3333-3333-3333-333333333333",
              "fase": "VURDERING",
              "antallRom": 1,
              "starttidspunkt": "09:00",
              "varighetPerMøteMinutter": 15,
              "oppmøte": ["$PERSON_ID"],
              "deltakernummer": [{"personTreffId": "$PERSON_ID", "nummer": 1}],
              "rom": [{"romnummer": 1, "jobbsøkere": ["$PERSON_ID"]}],
              "arbeidsgiverRekkefølge": [{"arbeidsgiverTreffId": "$ARBEIDSGIVER_ID", "startPosisjon": 0}],
              "interesser": [{"personTreffId": "$PERSON_ID", "arbeidsgiverTreffId": "$ARBEIDSGIVER_ID"}],
              "intervjufordelinger": [{
                "arbeidsgiverTreffId": "$ARBEIDSGIVER_ID",
                "inkludertePersonTreffIder": ["$PERSON_ID"],
                "ekskludertePersonTreffIder": []
              }],
              "vurderinger": [{
                "personTreffId": "$PERSON_ID",
                "arbeidsgiverTreffId": "$ARBEIDSGIVER_ID",
                "vurdering": "AKTUELL",
                "notater": ["AG_GODT_INNTRYKK"],
                "andregangsintervju": true,
                "andregangsintervjuDato": "2026-09-01",
                "jobbtilbud": false
              }]
            }"""
    }

    override fun registrer(routes: JavalinDefaultRoutingApi) {
        routes.get(HENT, hentHandler())
        routes.put(OPPMØTE, oppmøteHandler())
        routes.put(MØTEOPPSETT, møteoppsettHandler())
        routes.put(ROMFORDELING, romfordelingHandler())
        routes.put(INTERESSE, interesseHandler())
        routes.put(INTERVJUFORDELING, intervjufordelingHandler())
        routes.post(FORDEL, fordelHandler())
    }

    private fun Context.treffId() = TreffId(pathParam("id"))

    @OpenApi(
        summary = "Hent hele treffgjennomføringen og oppfølgingen for et rekrutteringstreff",
        description = "Rent lesende. Finnes ingen lagret treffgjennomføring returneres et tomt aggregat med 200.",
        operationId = "hentTreffgjennomforing",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)]),
            OpenApiResponse(status = "403", description = "Bruker er ikke eier av treffet."),
        ],
        path = lesPath,
        methods = [HttpMethod.GET],
    )
    private fun hentHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        AuditLog.loggVisningAvJobbsøkereTilhørendesRekrutteringstreff(navIdent, treffId)
        ctx.status(200).json(treffgjennomføringService.hent(treffId))
    }

    @OpenApi(
        summary = "Registrer eller angre oppmøte for én jobbsøker",
        description = "Fjerning når det finnes registreringer krever bekreftSlettRegistreringer=true, ellers 409.",
        operationId = "oppdaterOppmote",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = OppmøteRequestDto::class,
            example = """{"personTreffId": "11111111-1111-1111-1111-111111111111", "møtt": true, "bekreftSlettRegistreringer": false}""",
        )]),
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)]),
            OpenApiResponse(
                status = "409",
                description = "Oppmøtet har registreringer som må bekreftes slettet.",
                content = [OpenApiContent(
                    from = KaskadeAdvarselDto::class,
                    example = """{"feil": "Jobbsøkeren har registreringer som slettes hvis oppmøtet fjernes.", "hint": "Bekreft med bekreftSlettRegistreringer=true.", "registreringer": {"interesser": 2, "intervjuplasser": 1, "vurderinger": 0}}""",
                )],
            ),
        ],
        path = OPPMØTE,
        methods = [HttpMethod.PUT],
    )
    private fun oppmøteHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        val dto = ctx.bodyAsClass<OppmøteRequestDto>()
        ctx.status(200).json(oppmøteService.oppdaterOppmøte(treffId, dto, navIdent))
    }

    @OpenApi(
        summary = "Sett møtetidene. Første kall oppretter romfordeling og rotasjon. Kun WorkOp",
        operationId = "lagreMoteoppsett",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = MøteoppsettRequestDto::class, example = """{"starttidspunkt": "09:00", "varighetPerMøteMinutter": 15}""")]),
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = MØTEOPPSETT,
        methods = [HttpMethod.PUT],
    )
    private fun møteoppsettHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        val dto = ctx.bodyAsClass<MøteoppsettRequestDto>()
        ctx.status(200).json(møteplanService.lagreMøteoppsett(treffId, dto, navIdent))
    }

    @OpenApi(
        summary = "Erstatt hele romfordelingen. Kun WorkOp",
        description = "Bodyen er en liste av rom på rotnivå, og må inneholde alle rom — også de tomme.",
        operationId = "lagreRomfordeling",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = Array<RomDto>::class,
            example = """[{"romnummer": 1, "jobbsøkere": ["11111111-1111-1111-1111-111111111111"]}, {"romnummer": 2, "jobbsøkere": []}]""",
        )]),
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = ROMFORDELING,
        methods = [HttpMethod.PUT],
    )
    private fun romfordelingHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        val rom = ctx.bodyAsClass<Array<RomDto>>().toList()
        ctx.status(200).json(møteplanService.lagreRomfordeling(treffId, rom, navIdent))
    }

    @OpenApi(
        summary = "Sett eller fjern ett interessepar",
        operationId = "settInteresse",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = InteresseRequestDto::class, example = """{"personTreffId": "11111111-1111-1111-1111-111111111111", "arbeidsgiverTreffId": "22222222-2222-2222-2222-222222222222", "interessert": true}""")]),
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = INTERESSE,
        methods = [HttpMethod.PUT],
    )
    private fun interesseHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        val dto = ctx.bodyAsClass<InteresseRequestDto>()
        ctx.status(200).json(matchingService.settInteresse(treffId, dto, navIdent))
    }

    @OpenApi(
        summary = "Lagre intervjurekkefølgen for én arbeidsgiver. Kun WorkOp",
        operationId = "lagreIntervjufordeling",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = ArbeidsgiverIntervjufordelingDto::class,
            example = """{"arbeidsgiverTreffId": "22222222-2222-2222-2222-222222222222", "inkludertePersonTreffIder": ["11111111-1111-1111-1111-111111111111"], "ekskludertePersonTreffIder": []}""",
        )]),
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = INTERVJUFORDELING,
        methods = [HttpMethod.PUT],
    )
    private fun intervjufordelingHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        val dto = ctx.bodyAsClass<ArbeidsgiverIntervjufordelingDto>()
        ctx.status(200).json(matchingService.lagreIntervjufordeling(treffId, dto, navIdent))
    }

    @OpenApi(
        summary = "Fordel intervjuene på nytt. Kun WorkOp",
        description = "Tom body — alt som trengs er allerede lagret. Erstatter hele fordelingen i én transaksjon.",
        operationId = "fordelIntervjuer",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = FORDEL,
        methods = [HttpMethod.POST],
    )
    private fun fordelHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        ctx.status(200).json(matchingService.fordelIntervjuer(treffId, navIdent))
    }

}
