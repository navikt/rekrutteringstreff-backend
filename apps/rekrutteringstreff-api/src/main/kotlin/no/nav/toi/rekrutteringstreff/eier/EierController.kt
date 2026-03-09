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
        javalin.put(megEndepunkt, leggTilMeg())
        javalin.delete(slettEiereEndepunkt, slettEier())
    }

    @OpenApi(
        summary = "Legg til deg selv som eier av et rekrutteringstreff",
        description = "Bruker trenger ikke være eksisterende eier. Idempotent — returnerer alltid 200. Utføres atomisk med FOR UPDATE-lås.",
        operationId = "leggTilMegSomEier",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, description = "Rekrutteringstreffets UUID")],
        responses = [
            OpenApiResponse(status = "200", description = "Eier lagt til (eller allerede eier). Genererer EIER_LAGT_TIL-hendelse hvis ny.")
        ],
        path = megEndepunkt,
        methods = [HttpMethod.PUT]
    )
    private fun leggTilMeg(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val id = TreffId(ctx.pathParam("id"))
        val navIdent = ctx.authenticatedUser().extractNavIdent()
        val kontorId = ctx.authenticatedUser().extractKontorId()

        eierService.leggTilEierMedKontor(id, navIdent, kontorId)
        ctx.status(200)
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
        val eiere = eierService.hentEiere(id)
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
            eierService.slettEier(id, navIdentSomSkalSlettes, innloggetNavIdent)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Bruker har ikke tilgang til å slette eier på rekrutteringstreff ${id.somString}")
        }
    }
}

