package no.nav.toi.rekrutteringstreff.eier


import io.javalin.Javalin
import io.javalin.http.*
import io.javalin.openapi.*
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilJson
import java.util.*


class EierController(
    private val eierRepository: EierRepository,
    private val eierService: EierService,
    javalin: Javalin
) {
    companion object {
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val eiereEndepunkt = "$endepunktRekrutteringstreff/{id}/eiere"
        private const val megEndepunkt = "$eiereEndepunkt/meg"
        private const val slettEiereEndepunkt = "$eiereEndepunkt/{navIdent}"
    }

    init {
        javalin.get(eiereEndepunkt, hentEiere())
        javalin.put(eiereEndepunkt, leggTil())
        javalin.put(megEndepunkt, leggTilMeg())
        javalin.delete(slettEiereEndepunkt, slettEier())
    }

    @OpenApi(
        summary = "Legg til en eller flere eiere til et rekrutteringstreff",
        description = "Krever at innlogget bruker er eksisterende eier eller utvikler. Duplikater ignoreres. Hver ny eier genererer en EIER_LAGT_TIL-hendelse.",
        operationId = "leggTilEier",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, description = "Rekrutteringstreffets UUID")],
        requestBody = OpenApiRequestBody(
            description = "Liste med NAV-identer som skal legges til som eiere",
            content = [OpenApiContent(
                from = Array<String>::class,
                example = """["A123456", "Z999999"]"""
            )],
        ),
        responses = [
            OpenApiResponse(status = "201", description = "Eiere lagt til"),
            OpenApiResponse(status = "403", description = "Innlogget bruker er ikke eier eller utvikler"),
            OpenApiResponse(status = "404", description = "Rekrutteringstreff finnes ikke")
        ],
        path = eiereEndepunkt,
        methods = [HttpMethod.PUT]
    )
    private fun leggTil(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val eiere: List<String> = ctx.bodyAsClass<List<String>>()
        val id = TreffId(ctx.pathParam("id"))
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        if (eierService.erEierEllerUtvikler(treffId = id, navIdent = navIdent, context = ctx)) {
            eierService.leggTilEiere(id, eiere, navIdent)
            ctx.status(201)
        } else {
            throw ForbiddenResponse("Bruker har ikke tilgang til å legge til eier på rekrutteringstreff ${id.somString}")
        }
    }

    @OpenApi(
        summary = "Legg til deg selv som eier av et rekrutteringstreff",
        description = "Bruker trenger ikke være eksisterende eier. Idempotent — returnerer 200 hvis allerede eier, 201 hvis ny. Utføres atomisk med FOR UPDATE-lås.",
        operationId = "leggTilMegSomEier",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, description = "Rekrutteringstreffets UUID")],
        responses = [
            OpenApiResponse(status = "200", description = "Allerede eier, ingen endring"),
            OpenApiResponse(status = "201", description = "Lagt til som ny eier. Genererer EIER_LAGT_TIL-hendelse.")
        ],
        path = megEndepunkt,
        methods = [HttpMethod.PUT]
    )
    private fun leggTilMeg(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val id = TreffId(ctx.pathParam("id"))
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        val nyEier = eierService.leggTilMegSomEier(id, navIdent)
        ctx.status(if (nyEier) 201 else 200)
    }

    @OpenApi(
        summary = "Hent eierne til et rekrutteringstreff",
        description = "Returnerer liste med NAV-identer for alle eiere. Tilgjengelig for arbeidsgiverrettet og jobbsøkerrettet rolle.",
        operationId = "hentEiere",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, description = "Rekrutteringstreffets UUID")],
        responses = [
            OpenApiResponse(
                status = "200",
                description = "Liste med NAV-identer",
                content = [OpenApiContent(
                    from = Array<String>::class,
                    example = """["A123456", "Z999999"]"""
                )]
            ),
            OpenApiResponse(status = "404", description = "Rekrutteringstreff finnes ikke")
        ],
        path = eiereEndepunkt,
        methods = [HttpMethod.GET]
    )
    private fun hentEiere(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)

        val id = TreffId(ctx.pathParam("id"))
        val eiere = eierRepository.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
        ctx.status(200).result(eiere.tilJson())
    }


    @OpenApi(
        summary = "Slett eier av et rekrutteringstreff",
        description = "Fjerner en eier. Kan ikke slette siste eier — treffet må alltid ha minst én.",
        operationId = "slettEier",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [
            OpenApiParam(name = "id", type = UUID::class, description = "Rekrutteringstreffets UUID"),
            OpenApiParam(name = "navIdent", type = String::class, description = "NAV-identen som skal fjernes som eier")
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Eier fjernet"),
            OpenApiResponse(status = "400", description = "Kan ikke slette siste eier"),
            OpenApiResponse(status = "403", description = "Innlogget bruker er ikke eier eller utvikler"),
            OpenApiResponse(status = "404", description = "Rekrutteringstreff finnes ikke")
        ],
        path = slettEiereEndepunkt,
        methods = [HttpMethod.DELETE]
    )
    private fun slettEier(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val id = TreffId(ctx.pathParam("id"))
        val navIdentSomSkalSlettes = ctx.pathParam("navIdent")
        val innloggetNavIdent = ctx.authenticatedUser().extractNavIdent()

        if (eierService.erEierEllerUtvikler(id, innloggetNavIdent, ctx)) {
            eierService.slettEier(id, navIdentSomSkalSlettes)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Bruker har ikke tilgang til å slette eier på rekrutteringstreff ${id.somString}")
        }
    }
}

