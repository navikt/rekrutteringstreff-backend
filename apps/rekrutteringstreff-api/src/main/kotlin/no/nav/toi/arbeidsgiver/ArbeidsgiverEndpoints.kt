package no.nav.toi.arbeidsgiver


import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.*


private const val pathParamTreffId = "id"
private const val leggTilArbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver"

private data class LeggTilArbeidsgiverDto(
    val orgnr: String,
    val orgnavn: String
) {
    fun somLeggTilArbeidsgiver() = LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn(orgnavn))
}


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
    val dto: LeggTilArbeidsgiverDto = ctx.bodyAsClass<LeggTilArbeidsgiverDto>()
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    repo.leggTil(dto.somLeggTilArbeidsgiver(), treff)
    ctx.status(201)
}


fun Javalin.handleArbeidsgiver(repo: ArbeidsgiverRepository) {
    post(leggTilArbeidsgiverPath, leggTilArbeidsgiverHandler(repo))
}
