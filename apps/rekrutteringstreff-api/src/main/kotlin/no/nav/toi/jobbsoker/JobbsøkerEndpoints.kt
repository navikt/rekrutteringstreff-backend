package no.nav.toi.jobbsoker


import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.util.*


private const val pathParamTreffId = "id"
private const val jobbsøkerPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker"

private data class LeggTilJobbsøkerDto(
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String
) {
    fun somLeggTilJobbsøker() = LeggTilJobbsøker(
        Fødselsnummer(fødselsnummer),
        kandidatnummer?.let { Kandidatnummer(it) },
        Fornavn(fornavn),
        Etternavn(etternavn)
    )
}

data class JobbsøkerOutboundDto(
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String
)

@OpenApi(
    summary = "Legg til ny jobbsøker til et rekrutteringstreff",
    operationId = "leggTilJobbsøker",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(
        name = pathParamTreffId,
        type = UUID::class,
        required = true,
        description = "Rekrutteringstreffets unike identifikator (UUID)"
    )],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = LeggTilJobbsøkerDto::class,
            example = """{"fødselsnummer": "12345678901", "fornavn": "Ola", "etternavn": "Nordmann"}"""
        )]
    ),
    responses = [OpenApiResponse(
        status = "201"
    )],
    path = jobbsøkerPath,
    methods = [HttpMethod.POST]
)
private fun leggTilJobbsøkerHandler(repo: JobbsøkerRepository): (Context) -> Unit = { ctx ->
    val dto: LeggTilJobbsøkerDto = ctx.bodyAsClass<LeggTilJobbsøkerDto>()
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    repo.leggTil(dto.somLeggTilJobbsøker(), treff)
    ctx.status(201)
}


@OpenApi(
    summary = "Hent alle jobbsøkere for et rekrutteringstreff",
    operationId = "hentJobbsøkere",
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
            from = Array<JobbsøkerOutboundDto>::class,
            example = """[
                {
                    "fødselsnummer": "12345678901",
                    "fornavn": "Ola",
                    "etternavn": "Nordmann"
                },
                {
                    "fødselsnummer": "10987654321",
                    "fornavn": "Kari",
                    "etternavn": "Nordmann"
                }
            ]"""
        )]
    )],
    path = jobbsøkerPath,
    methods = [HttpMethod.GET]
)
private fun hentJobbsøkere(repo: JobbsøkerRepository): (Context) -> Unit = { ctx ->
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    val jobbsøkere = repo.hentJobbsøkere(treff)
    ctx.status(200).json(jobbsøkere.toOutboundDto())
}

private fun List<Jobbsøker>.toOutboundDto(): List<JobbsøkerOutboundDto> =
    map {
        JobbsøkerOutboundDto(
            fødselsnummer = it.fødselsnummer.asString,
            kandidatnummer = it.kandidatnummer?.asString,
            fornavn = it.fornavn.asString,
            etternavn = it.etternavn.asString
        )
    }


fun Javalin.handleJobbsøker(repo: JobbsøkerRepository) {
    post(jobbsøkerPath, leggTilJobbsøkerHandler(repo))
    get(jobbsøkerPath, hentJobbsøkere(repo))
}
