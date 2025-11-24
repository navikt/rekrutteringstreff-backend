package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.bodyAsClass


import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilJson
import java.util.*


class EierController(
    private val eierRepository: EierRepository,
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
        eierRepository.leggTil(id, eiere)
        ctx.status(201)
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
        log.info("Henter eiere for rekrutteringstreff")

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
        val navIdent = ctx.pathParam("navIdent")
        // TODO skal eier få lov til å slette seg selv? hva skal skje hvis siste eier sletter seg selv?

        eierRepository.slett(id, navIdent)
        ctx.status(200)
    }
}

