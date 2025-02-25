package no.nav.toi.rekrutteringstreff.rekrutteringstreff.eier
import io.javalin.http.bodyAsClass


import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.eier.Eier.Companion.tilJson
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.*

private const val eiereEndepunkt = "$endepunktRekrutteringstreff/{id}/eiere"

fun Javalin.handleEiere(repo: RekrutteringstreffRepository, endepunktRekrutteringstreff: String) {
    get(eiereEndepunkt, hentEiere(repo))
    put(eiereEndepunkt, leggTilEiere(repo.eierRepository))
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
private fun leggTilEiere(repo: RekrutteringstreffRepository.Companion.EierRepository): (Context) -> Unit = { ctx ->
    val eiere: List<String> = ctx.bodyAsClass<List<String>>()
    repo.leggTilEiere(eiere)
    ctx.status(201)//.result(eiere.tilJson())
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
private fun hentEiere(repo: RekrutteringstreffRepository): (Context) -> Unit = { ctx ->
    val id = UUID.fromString(ctx.pathParam("id"))
    val eiere = repo.eierRepository.hent(id) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
    ctx.status(200).result(eiere.tilJson())
}

