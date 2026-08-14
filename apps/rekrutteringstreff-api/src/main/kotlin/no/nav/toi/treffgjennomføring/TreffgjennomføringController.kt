package no.nav.toi.treffgjennomføring

import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.AuditLog
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.RuteRegistrerer
import no.nav.toi.authenticatedUser
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.treffgjennomføring.matching.MatchingService
import no.nav.toi.treffgjennomføring.møteplan.MøteplanService
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.eier.krevEierEllerUtvikler
import no.nav.toi.treffgjennomføring.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomføring.dto.InteresseRequestDto
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomføring.dto.RomDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import java.util.*

class TreffgjennomføringController(
    private val treffgjennomføringService: TreffgjennomføringService,
    private val møteplanService: MøteplanService,
    private val matchingService: MatchingService,
    private val jobbsøkerService: JobbsøkerService,
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
            OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class)]),
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
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class)]),
            OpenApiResponse(status = "409", description = "Oppmøtet har registreringer som må bekreftes slettet."),
        ],
        path = OPPMØTE,
        methods = [HttpMethod.PUT],
    )
    private fun oppmøteHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        val dto = ctx.bodyAsClass<OppmøteRequestDto>()
        ctx.status(200).json(jobbsøkerService.oppdaterOppmøte(treffId, dto, navIdent))
    }

    @OpenApi(
        summary = "Sett møtetidene. Første kall oppretter romfordeling og rotasjon. Kun WorkOp",
        operationId = "lagreMoteoppsett",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class)])],
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
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class)])],
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
        summary = "Sett eller fjern ett interessepar. Idempotent",
        operationId = "settInteresse",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class)])],
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
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class)])],
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
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class)])],
        path = FORDEL,
        methods = [HttpMethod.POST],
    )
    private fun fordelHandler(): (Context) -> Unit = { ctx ->
        val treffId = ctx.treffId()
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        ctx.status(200).json(matchingService.fordelIntervjuer(treffId, navIdent))
    }

}
