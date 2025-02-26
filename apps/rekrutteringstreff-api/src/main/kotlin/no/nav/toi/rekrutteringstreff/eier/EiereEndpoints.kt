package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.bodyAsClass


import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilJson
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.*

private const val eiereEndepunkt = "$endepunktRekrutteringstreff/{id}/eiere"
private const val slettEiereEndepunkt = "$eiereEndepunkt/{navIdent}"

fun Javalin.handleEiere(repo: EierRepository) {
    get(eiereEndepunkt, hentEiere(repo))
    put(eiereEndepunkt, leggTilEiere(repo))
    delete(slettEiereEndepunkt, slettEier(repo))
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
private fun leggTilEiere(repo: EierRepository): (Context) -> Unit = { ctx ->
    val eiere: List<String> = ctx.bodyAsClass<List<String>>()
    val id = TreffId(ctx.pathParam("id"))
    repo.leggTilEiere(id, eiere)
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
private fun hentEiere(repo: EierRepository): (Context) -> Unit = { ctx ->
    val id = TreffId(ctx.pathParam("id"))
    val eiere = repo.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
    ctx.status(200).result(eiere.tilJson())
}


@OpenApi(
    summary = "Slett eier av et rekrutteringstreff",
    operationId = "slettEier",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "id", type = UUID::class), OpenApiParam(name = "navIdent", type = String::class)],
    responses = [OpenApiResponse(status = "200")],
    path = slettEiereEndepunkt,
    methods = [HttpMethod.DELETE]
)
private fun slettEier(repo: EierRepository): (Context) -> Unit = { ctx ->
    val id = TreffId(ctx.pathParam("id"))
    val navIdent = ctx.pathParam("navIdent")
    repo.slett(id, navIdent)
    ctx.status(200)
}

