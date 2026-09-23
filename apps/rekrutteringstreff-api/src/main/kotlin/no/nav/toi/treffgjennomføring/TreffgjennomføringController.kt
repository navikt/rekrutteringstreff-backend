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
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.oppmøte.OppmøteService
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.eier.krevEierEllerUtvikler
import no.nav.toi.treffgjennomføring.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomføring.dto.FlyttJobbsøkerRomRequestDto
import no.nav.toi.treffgjennomføring.dto.InteresseRequestDto
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.OppmøteBlokkertDto
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomføring.dto.StegRequestDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.matching.MatchingService
import no.nav.toi.treffgjennomføring.møteplan.MøteplanService
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
        const val FLYTT_JOBBSØKER_ROM = "$skrivPath/romfordeling/{personTreffId}"
        const val FORDEL_ROM = "$skrivPath/romfordeling/fordel"
        const val INTERESSE = "$skrivPath/interesse"
        const val INTERVJUFORDELING = "$skrivPath/intervjufordeling"
        const val FORDEL_INTERVJUER = "$INTERVJUFORDELING/fordel"
        const val STEG = "$skrivPath/steg"
        const val HENT = lesPath

        private const val PERSON_ID = "11111111-1111-1111-1111-111111111111"
        private const val ARBEIDSGIVER_ID = "22222222-2222-2222-2222-222222222222"

        const val AGGREGAT_EKSEMPEL = """{
              "rekrutteringstreffId": "33333333-3333-3333-3333-333333333333",
              "gjeldendeSteg": "VURDERING",
              "antallRom": 1,
              "starttidspunkt": "09:00",
              "varighetPerMøteMinutter": 15,
              "oppmøte": ["$PERSON_ID"],
              "deltakernummer": [{"personTreffId": "$PERSON_ID", "deltakernummer": 1}],
              "rom": [{"romnummer": 1, "jobbsøkere": ["$PERSON_ID"]}],
              "arbeidsgiverRekkefølge": [{"arbeidsgiverTreffId": "$ARBEIDSGIVER_ID", "førsteRomnummer": 1}],
              "interesser": [{"personTreffId": "$PERSON_ID", "arbeidsgiverTreffId": "$ARBEIDSGIVER_ID"}],
              "intervjufordelinger": [{
                "arbeidsgiverTreffId": "$ARBEIDSGIVER_ID",
                "inkludertePersonTreffIder": ["$PERSON_ID"],
                "ekskludertePersonTreffIder": []
              }],
              "vurderinger": [{
                "personTreffId": "$PERSON_ID",
                "arbeidsgiverTreffId": "$ARBEIDSGIVER_ID",
                "vurderingsstatus": "AKTUELL",
                "vurderingsnotat": ["AG_GODT_INNTRYKK"],
                "avtaltIntervju": true,
                "avtaltIntervjuDato": "2026-09-01",
                "jobbtilbud": false
              }]
            }"""
    }

    override fun registrer(routes: JavalinDefaultRoutingApi) {
        routes.get(HENT, hentHandler())
        routes.put(OPPMØTE, oppmøteHandler())
        routes.put(MØTEOPPSETT, møteoppsettHandler())
        routes.put(FLYTT_JOBBSØKER_ROM, flyttJobbsøkerRomHandler())
        routes.post(FORDEL_ROM, fordelRomHandler())
        routes.put(INTERESSE, interesseHandler())
        routes.put(INTERVJUFORDELING, intervjufordelingHandler())
        routes.post(FORDEL_INTERVJUER, fordelIntervjuerHandler())
        routes.put(STEG, stegHandler())
    }

    private fun Context.treffId() = TreffId(pathParam("id"))

    /** Alle skriveendepunktene krever eier eller utvikler og svarer med hele det oppdaterte aggregatet. */
    private fun skrivHandler(
        operasjon: (ctx: Context, treffId: TreffId, navIdent: String) -> TreffgjennomføringDto,
    ): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        ctx.status(200).json(operasjon(ctx, treffId, navIdent))
    }

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
        description = "Oppmøtet kan bare fjernes når jobbsøkeren ikke har interesser, intervjufordelinger eller vurderinger, ellers 409.",
        operationId = "oppdaterOppmote",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = OppmøteRequestDto::class,
            example = """{"personTreffId": "$PERSON_ID", "møtt": true}""",
        )]),
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)]),
            OpenApiResponse(
                status = "409",
                description = "Jobbsøkeren har registreringer som må ryddes før oppmøtet kan fjernes.",
                content = [OpenApiContent(
                    from = OppmøteBlokkertDto::class,
                    example = """{"feil": "Jobbsøkeren har registreringer og oppmøtet kan derfor ikke fjernes.", "hint": "Fjern registrerte intervjufordelinger først.", "registreringer": {"interesser": 0, "intervjufordelinger": 1, "vurderinger": 0}}""",
                )],
            ),
        ],
        path = OPPMØTE,
        methods = [HttpMethod.PUT],
    )
    private fun oppmøteHandler() = skrivHandler { ctx, treffId, navIdent ->
        oppmøteService.oppdaterOppmøte(treffId, ctx.bodyAsClass<OppmøteRequestDto>(), navIdent)
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
    private fun møteoppsettHandler() = skrivHandler { ctx, treffId, navIdent ->
        møteplanService.lagreMøteoppsett(treffId, ctx.bodyAsClass<MøteoppsettRequestDto>(), navIdent)
    }

    @OpenApi(
        summary = "Flytt én jobbsøker til et rom. Kun WorkOp",
        description = "Flytter valgt jobbsøker til angitt romnummer basert på fersk servertilstand. Overskriver ikke andre endringer.",
        operationId = "flyttJobbsøkerRom",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [
            OpenApiParam(name = "id", type = UUID::class, required = true),
            OpenApiParam(name = "personTreffId", type = UUID::class, required = true),
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = FlyttJobbsøkerRomRequestDto::class,
            example = """{"romnummer": 2}""",
        )]),
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)]),
            OpenApiResponse(status = "400", description = "Ugyldig person eller målrom, manglende oppmøte eller møteoppsett, eller treffet er ikke WorkOp."),
            OpenApiResponse(status = "403", description = "Bruker er ikke eier eller utvikler."),
        ],
        path = FLYTT_JOBBSØKER_ROM,
        methods = [HttpMethod.PUT],
    )
    private fun flyttJobbsøkerRomHandler() = skrivHandler { ctx, treffId, _ ->
        val personTreffId = PersonTreffId(ctx.pathParam("personTreffId"))
        val dto = ctx.bodyAsClass<FlyttJobbsøkerRomRequestDto>()
        møteplanService.flyttJobbsøkerTilRom(treffId, personTreffId, dto.romnummer)
    }

    @OpenApi(
        summary = "Fordel fremmøtte jobbsøkere på rom på nytt. Kun WorkOp",
        description = "Tom body. Fordeler alle fremmøtte jevnt på treffets rom og erstatter romfordelingen.",
        operationId = "fordelRomPåNytt",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)]),
            OpenApiResponse(status = "400", description = "Møteoppsettet mangler, eller treffet er ikke WorkOp."),
        ],
        path = FORDEL_ROM,
        methods = [HttpMethod.POST],
    )
    private fun fordelRomHandler() = skrivHandler { _, treffId, _ ->
        møteplanService.fordelRomPåNytt(treffId)
    }

    @OpenApi(
        summary = "Sett eller fjern ett interessepar",
        operationId = "settInteresse",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = InteresseRequestDto::class, example = """{"personTreffId": "$PERSON_ID", "arbeidsgiverTreffId": "$ARBEIDSGIVER_ID", "interessert": true}""")]),
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = INTERESSE,
        methods = [HttpMethod.PUT],
    )
    private fun interesseHandler() = skrivHandler { ctx, treffId, _ ->
        matchingService.settInteresse(treffId, ctx.bodyAsClass<InteresseRequestDto>())
    }

    @OpenApi(
        summary = "Flytt gjeldende steg framover. Brukes når arrangøren går videre til et steg som ikke skriver data",
        operationId = "settGjeldendeSteg",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = StegRequestDto::class, example = """{"steg": "OPPSUMMERING"}""")]),
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = STEG,
        methods = [HttpMethod.PUT],
    )
    private fun stegHandler() = skrivHandler { ctx, treffId, _ ->
        treffgjennomføringService.settGjeldendeSteg(treffId, ctx.bodyAsClass<StegRequestDto>().steg)
    }

    @OpenApi(
        summary = "Lagre intervjurekkefølgen for én arbeidsgiver. Kun WorkOp",
        operationId = "lagreIntervjufordeling",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = ArbeidsgiverIntervjufordelingDto::class,
            example = """{"arbeidsgiverTreffId": "$ARBEIDSGIVER_ID", "inkludertePersonTreffIder": ["$PERSON_ID"], "ekskludertePersonTreffIder": []}""",
        )]),
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = INTERVJUFORDELING,
        methods = [HttpMethod.PUT],
    )
    private fun intervjufordelingHandler() = skrivHandler { ctx, treffId, _ ->
        matchingService.lagreIntervjufordeling(treffId, ctx.bodyAsClass<ArbeidsgiverIntervjufordelingDto>())
    }

    @OpenApi(
        summary = "Fordel intervjuene på nytt. Kun WorkOp",
        description = "Tom body — alt som trengs er allerede lagret. Erstatter hele fordelingen i én transaksjon.",
        operationId = "fordelIntervjuer",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = FORDEL_INTERVJUER,
        methods = [HttpMethod.POST],
    )
    private fun fordelIntervjuerHandler() = skrivHandler { _, treffId, navIdent ->
        matchingService.fordelIntervjuer(treffId, navIdent)
    }
}
