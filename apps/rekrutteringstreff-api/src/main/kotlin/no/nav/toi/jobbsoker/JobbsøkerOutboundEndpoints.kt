package no.nav.toi.jobbsoker

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.openapi.*
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.kandidatsok.KandidatsøkKlient
import java.util.*

private const val pathParamPersonTreffId = "personTreffId"
private const val eksternKandidatnummerPath = "/api/jobbsoker/{$pathParamPersonTreffId}/kandidatnummer"

data class KandidatnummerDto(val kandidatnummer: String)

@OpenApi(
    summary = "Hent kandidatnummer for en jobbsøker basert på personTreffId",
    operationId = "hentKandidatnummerForPersonTreffId",
    tags = ["jobbsøker-outbound"],
    pathParams = [OpenApiParam(
        name = pathParamPersonTreffId,
        type = UUID::class,
        required = true,
        description = "Jobbsøkerens unike identifikator for treffet (UUID)"
    )],
    responses = [
        OpenApiResponse("200", content = [OpenApiContent(from = KandidatnummerDto::class)]),
        OpenApiResponse("404", description = "Jobbsøker eller kandidatnummer ikke funnet")
    ],
    path = eksternKandidatnummerPath,
    methods = [HttpMethod.GET]
)
private fun hentKandidatnummerHandler(
    repo: JobbsøkerRepository,
    client: KandidatsøkKlient
): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.UTVIKLER)
    val personTreffId = PersonTreffId(ctx.pathParam(pathParamPersonTreffId))

    repo.hentFødselsnummer(personTreffId)?.let { fødselsnummer ->
        client.hentKandidatnummer(fødselsnummer)?.let { kandidatnummer ->
            ctx.json(KandidatnummerDto(kandidatnummer.asString))
        } ?: ctx.status(HttpStatus.NOT_FOUND)
    } ?: ctx.status(HttpStatus.NOT_FOUND)
}

fun Javalin.handleJobbsøkerOutbound(repo: JobbsøkerRepository, client: KandidatsøkKlient) {
    get(eksternKandidatnummerPath, hentKandidatnummerHandler(repo, client))
}