package no.nav.toi.rekrutteringstreff.arbeidsgiver


import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.*


private const val pathParamTreffId = "treffId"
private const val leggTilArbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver"

data class LeggTilArbeidsgiverDto(
    val treffId: String,
    val orgnr: String,
    val orgnavn: String
) // TODO Are: Takler Javalin bruke sterkere typer her?


@OpenApi(
    summary = "Legg til ny arbeidsgiver til et rekrutteringstreff",
    operationId = "leggTilArbeidsgiver",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class)],
    responses = [OpenApiResponse(
        status = "201"
    )],
    path = leggTilArbeidsgiverPath,
    methods = [HttpMethod.POST]
)
private fun leggTilArbeidsgiverHandler(repo: ArbeidsgiverRepository): (Context) -> Unit = { ctx ->
    val arbeidsgiver: LeggTilArbeidsgiverDto = ctx.bodyAsClass<LeggTilArbeidsgiverDto>()
    val treff = TreffId(ctx.pathParam("treffId"))
    repo.leggTil(arbeidsgiver, treff)
    ctx.status(201) // TODO: Inkluidere ID til ny arbeidsgiver i HTTP response?
}


fun Javalin.handleArbeidsgiver(repo: ArbeidsgiverRepository) {
    post(leggTilArbeidsgiverPath, leggTilArbeidsgiverHandler(repo))
}
