package no.nav.toi.rekrutteringstreff.rekrutteringstreff.eier

import io.javalin.http.bodyAsClass


import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.eier.Eier.Companion.tilJson
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.*

private const val eiereEndepunkt = "$endepunktRekrutteringstreff/{id}/eiere"

fun Javalin.handleEiere(repo: EierRepository) {
    get(eiereEndepunkt, hentEiere(repo))
    put(eiereEndepunkt, leggTilEiere(repo))
}

@OpenApi(
    summary = "Legg til ny eier til et rekrutteringstreff",
    operationId = "leggTilEier",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "id", type = UUID::class)],
    responses = [OpenApiResponse(
        status = "201"
    )],
    path = endepunktRekrutteringstreff,
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
    path = endepunktRekrutteringstreff,
    methods = [HttpMethod.GET]
)
private fun hentEiere(repo: EierRepository): (Context) -> Unit = { ctx ->
    val id = TreffId(ctx.pathParam("id"))
    val eiere = repo.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
    ctx.status(200).result(eiere.tilJson())
}

