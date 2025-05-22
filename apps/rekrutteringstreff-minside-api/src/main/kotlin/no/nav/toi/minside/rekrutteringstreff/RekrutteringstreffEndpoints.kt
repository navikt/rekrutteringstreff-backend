package no.nav.toi.minside.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import no.nav.toi.minside.authenticatedUser
import java.time.ZonedDateTime
import java.util.UUID

const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
private const val pathParamTreffId = "id"
private const val hentRekrutteringsTreff = "$endepunktRekrutteringstreff/{$pathParamTreffId}"


@OpenApi(
    summary = "Hent ett rekrutteringstreff",
    operationId = "hentRekrutteringstreff",
    security = [OpenApiSecurity("BearerAuth")],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = RekrutteringstreffOutboundDto::class,
            example = """{
               "id":"d6a587cd-8797-4b9a-a68b-575373f16d65",
               "tittel":"Sommerjobbtreff",
               "beskrivelse":null,
               "fraTid":null,
               "tilTid":null,
               "sted":null,
            }"""
        )]
    )],
    path = hentRekrutteringsTreff,
    methods = [HttpMethod.GET]
)
private fun hentRekrutteringstreffHandler(treffKlient: RekrutteringstreffKlient): (Context) -> Unit = { ctx ->
    val id = ctx.pathParam(pathParamTreffId)
    treffKlient.hent(id, ctx.authenticatedUser().jwt)?.let { ctx.status(200).json(it.tilDTOForBruker().json()) }
        ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
}
fun Javalin.rekrutteringstreffendepunkt(treffKlient: RekrutteringstreffKlient) = get(hentRekrutteringsTreff, hentRekrutteringstreffHandler(treffKlient))

class RekrutteringstreffOutboundDto(
    private val id: UUID,
    private val tittel: String,
    private val beskrivelse: String?,
    private val fraTid: ZonedDateTime?,
    private val tilTid: ZonedDateTime?,
    private val sted: String?,
) {
    fun json() = """
        {
            "id": "$id",
            "tittel": "$tittel",
            "beskrivelse": ${beskrivelse?.let { "\"$it\"" } },
            "fraTid": ${fraTid?.let { "\"$it\"" } },
            "tilTid": ${tilTid?.let { "\"$it\"" } },
            "sted": ${sted?.let { "\"$it\"" } }
        }
    """.trimIndent()
}