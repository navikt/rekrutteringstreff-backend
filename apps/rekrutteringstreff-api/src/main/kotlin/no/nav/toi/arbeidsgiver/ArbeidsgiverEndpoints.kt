package no.nav.toi.arbeidsgiver


import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.*


private const val pathParamTreffId = "id"
private const val arbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver"

private data class LeggTilArbeidsgiverDto(
    val organisasjonsnummer: String,
    val navn: String
) {
    fun somLeggTilArbeidsgiver() = LeggTilArbeidsgiver(Orgnr(organisasjonsnummer), Orgnavn(navn))
}

data class ArbeidsgiverOutboundDto(
    val orgnaisasjonsnummer: String,
    val navn: String,
    val status: String = "TODO" // TODO Are: Default bør ikke settes her
)

@OpenApi(
    summary = "Legg til ny arbeidsgiver til et rekrutteringstreff",
    operationId = "leggTilArbeidsgiver",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(
        name = pathParamTreffId,
        type = UUID::class,
        required = true,
        description = "Rekrutteringstreffets unike identifikator (UUID)"
    )],
    responses = [OpenApiResponse(
        status = "201"
    )],
    path = arbeidsgiverPath,
    methods = [HttpMethod.POST]
)
private fun leggTilArbeidsgiverHandler(repo: ArbeidsgiverRepository): (Context) -> Unit = { ctx ->
    val dto: LeggTilArbeidsgiverDto = ctx.bodyAsClass<LeggTilArbeidsgiverDto>()
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    repo.leggTil(dto.somLeggTilArbeidsgiver(), treff)
    ctx.status(201)
}


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
                    "TODO": "TODO",
                }
            ]"""
        )]
    )],
    path = arbeidsgiverPath,
    methods = [HttpMethod.GET]
)
private fun hentArbeidsgivere(repo: ArbeidsgiverRepository): (Context) -> Unit = { ctx ->
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    val arbeidsgivere = repo.hentArbeidsgivere(treff)
    ctx.status(200).json(arbeidsgivere.toOutboundDto())
}

private fun List<Arbeidsgiver>.toOutboundDto(): List<ArbeidsgiverOutboundDto> =
    map {
        ArbeidsgiverOutboundDto(
            orgnaisasjonsnummer = it.orgnr.asString,
            navn = it.orgnavn.asString
        )
    }


fun Javalin.handleArbeidsgiver(repo: ArbeidsgiverRepository) {
    post(arbeidsgiverPath, leggTilArbeidsgiverHandler(repo))
    get(arbeidsgiverPath, hentArbeidsgivere(repo))
}
