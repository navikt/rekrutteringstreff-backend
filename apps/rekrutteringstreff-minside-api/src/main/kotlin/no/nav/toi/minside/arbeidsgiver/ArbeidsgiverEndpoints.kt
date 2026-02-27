package no.nav.toi.minside.arbeidsgiver

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
import no.nav.toi.minside.rekrutteringstreff.RekrutteringstreffKlient
import no.nav.toi.minside.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.UUID

private const val pathParamTreffId = "id"
private const val arbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver"


@OpenApi(
    summary = "Hent alle arbeidsgivere for et rekrutteringstreff",
    operationId = "hentArbeidsgivere",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(
        name = pathParamTreffId,
        type = UUID::class,
        required = true,
        description = "Rekrutteringstreffets unike identifikator (UUID)"
    )],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = Array<ArbeidsgiverOutboundDto>::class,
            example = """[
                {
                    "organisasjonsnummer": "123456789",
                    "navn": "Example Company"
                },
                {
                    "organisasjonsnummer": "987654321",
                    "navn": "Another Company"
                }
            ]"""
        )]
    )],
    path = arbeidsgiverPath,
    methods = [HttpMethod.GET]
)
private fun hentArbeidsgivereHandler(treffKlient: RekrutteringstreffKlient): (Context) -> Unit = { ctx ->
    val id = ctx.pathParam(pathParamTreffId)
    treffKlient.hentArbeidsgivere(id, ctx.authenticatedUser().jwt)?.let { ctx.status(200).json(it.map { it.tilDTOForBruker() }.json() ) }
        ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
}

fun Javalin.arbeidsgiverendepunkt(treffKlient: RekrutteringstreffKlient) = get(arbeidsgiverPath, hentArbeidsgivereHandler(treffKlient))


data class ArbeidsgiverOutboundDto(
    private val organisasjonsnummer: String,
    private val navn: String
) {
    fun json() = """
        {
            "organisasjonsnummer": "$organisasjonsnummer",
            "navn": "$navn"
        }
    """.trimIndent()
}

private fun List<ArbeidsgiverOutboundDto>.json() = joinToString{ it.json() }
