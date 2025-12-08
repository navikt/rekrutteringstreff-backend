package no.nav.toi.rekrutteringstreff.eier


import io.javalin.Javalin
import io.javalin.http.*
import io.javalin.openapi.*
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilJson
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import java.util.*


class EierController(
    private val eierRepository: EierRepository,
    private val eierService: EierService,
    javalin: Javalin
) {
    companion object {
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val eiereEndepunkt = "$endepunktRekrutteringstreff/{id}/eiere"
        private const val slettEiereEndepunkt = "$eiereEndepunkt/{navIdent}"
    }

    init {
        javalin.get(eiereEndepunkt, hentEiere())
        javalin.put(eiereEndepunkt, leggTil())
        javalin.delete(slettEiereEndepunkt, slettEier())
    }

    @OpenApi(
        summary = "Legg til ny eier til et rekrutteringstreff",
        operationId = "leggTilEier",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class)],
        responses = [OpenApiResponse(
            status = "201"
        )],
        path = eiereEndepunkt,
        methods = [HttpMethod.PUT]
    )
    private fun leggTil(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val eiere: List<String> = ctx.bodyAsClass<List<String>>()
        val id = TreffId(ctx.pathParam("id"))
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        if(eierService.erEierEllerUtvikler(treffId = id, navIdent = navIdent, context = ctx)) {
            eierRepository.leggTil(id, eiere)
            ctx.status(201)
        } else {
            throw ForbiddenResponse("Bruker har ikke tilgang til å legge til eier på rekrutteringstreff ${id.somString}")
        }
    }

    @OpenApi(
        summary = "Hent eierne til et rekrutteringstreff",
        operationId = "hentEiere",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class)],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = Array<String>::class,
                example = """
                [
                    "A123456",
                    "Z999999"
                ]
                """
            )]
        )],
        path = eiereEndepunkt,
        methods = [HttpMethod.GET]
    )
    private fun hentEiere(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)

        val id = TreffId(ctx.pathParam("id"))
        val eiere = eierRepository.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
        ctx.status(200).result(eiere.tilJson())
    }


    @OpenApi(
        summary = "Slett eier av et rekrutteringstreff",
        operationId = "slettEier",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class), OpenApiParam(
            name = "navIdent",
            type = String::class
        )],
        responses = [OpenApiResponse(status = "200")],
        path = slettEiereEndepunkt,
        methods = [HttpMethod.DELETE]
    )
    private fun slettEier(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val id = TreffId(ctx.pathParam("id"))
        val navIdentSomSkalSlettes = ctx.pathParam("navIdent")
        val innloggetNavIdent = ctx.authenticatedUser().extractNavIdent()

        val eiere = eierRepository.hent(id)?.tilNavIdenter() ?: throw IllegalStateException("Rekrutteringstreff med id ${id.somString} har ingen eiere")
        if(eierService.erEierEllerUtvikler(id, innloggetNavIdent, ctx)) {
            if(eiere.size <= 1) {
                throw BadRequestResponse("Kan ikke slette siste eier for rekrutteringstreff ${id.somString}")
            }

            eierRepository.slett(id, navIdentSomSkalSlettes)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Bruker har ikke tilgang til å slette eier på rekrutteringstreff ${id.somString}")
        }
    }
}

