package no.nav.toi.rekrutteringstreff.ki

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto
import java.time.ZonedDateTime
import java.util.*

private const val base = "/api/rekrutteringstreff/ki"

fun Javalin.handleKi(repo: KiLoggRepository) {
    OpenAiClient.configureKiRepository(repo)

    post("$base/valider", validerOgLoggHandler())
    patch("$base/logg/{id}/lagret", oppdaterLagretHandler(repo))
    patch("$base/logg/{id}/manuell", oppdaterManuellHandler(repo))
    get("$base/logg", listHandler(repo))
    get("$base/logg/{id}", getHandler(repo))
}

@OpenApi(
    summary = "Valider tekst via KI og logg spørringen.",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody([OpenApiContent(from = ValiderMedLoggRequestDto::class)]),
    responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = ValiderMedLoggResponseDto::class)])],
    path = "$base/valider",
    methods = [HttpMethod.POST]
)
private fun validerOgLoggHandler(): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val req = ctx.bodyAsClass<ValiderMedLoggRequestDto>()
    val (result: ValiderRekrutteringstreffResponsDto, loggId: UUID?) =
        OpenAiClient.validateRekrutteringstreffOgLogg(req.treffDbId, req.feltType, req.tekst)
    ctx.status(200).json(
        ValiderMedLoggResponseDto(
            loggId = loggId?.toString() ?: "",
            bryterRetningslinjer = result.bryterRetningslinjer,
            begrunnelse = result.begrunnelse
        )
    )
}

@OpenApi(
    summary = "Oppdater 'lagret' for en logglinje.",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
    requestBody = OpenApiRequestBody([OpenApiContent(from = OppdaterLagretRequestDto::class)]),
    responses = [OpenApiResponse(status = "204")],
    path = "$base/logg/{id}/lagret",
    methods = [HttpMethod.PATCH]
)
private fun oppdaterLagretHandler(repo: KiLoggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER)
    val id = UUID.fromString(ctx.pathParam("id"))
    val req = ctx.bodyAsClass<OppdaterLagretRequestDto>()
    if (repo.setLagret(id, req.lagret) == 0) throw NotFoundResponse("Logg ikke funnet")
    ctx.status(204)
}

@OpenApi(
    summary = "Registrer resultat av manuell kontroll.",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
    requestBody = OpenApiRequestBody([OpenApiContent(from = OppdaterManuellRequestDto::class)]),
    responses = [OpenApiResponse(status = "204")],
    path = "$base/logg/{id}/manuell",
    methods = [HttpMethod.PATCH]
)
private fun oppdaterManuellHandler(repo: KiLoggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER)
    val id = UUID.fromString(ctx.pathParam("id"))
    val req = ctx.bodyAsClass<OppdaterManuellRequestDto>()
    val ident = ctx.extractNavIdent()
    val now = ZonedDateTime.now()
    if (repo.setManuellKontroll(id, req.bryterRetningslinjer, ident, now) == 0) {
        throw NotFoundResponse("Logg ikke funnet")
    }
    ctx.status(204)
}

@OpenApi(
    summary = "List logglinjer for et rekrutteringstreff.",
    security = [OpenApiSecurity(name = "BearerAuth")],
    queryParams = [
        OpenApiParam(name = "treffDbId", type = Long::class, required = false),
        OpenApiParam(name = "feltType", type = String::class, required = false),
        OpenApiParam(name = "limit", type = Int::class, required = false),
        OpenApiParam(name = "offset", type = Int::class, required = false)
    ],
    responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<KiLoggOutboundDto>::class)])],
    path = "$base/logg",
    methods = [HttpMethod.GET]
)
private fun listHandler(repo: KiLoggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER)
    val treffDbId = ctx.queryParam("treffDbId")?.toLong()
    val feltType = ctx.queryParam("feltType")
    val limit = ctx.queryParam("limit")?.toInt() ?: 50
    val offset = ctx.queryParam("offset")?.toInt() ?: 0

    val rows = repo.list(treffDbId, feltType, limit, offset)

    val out = rows.map {
        KiLoggOutboundDto(
            id = it.id.toString(),
            opprettetTidspunkt = it.opprettetTidspunkt,
            treffDbId = it.treffDbId,
            feltType = it.feltType,
            spørringFraFrontend = it.spørringFraFrontend,
            spørringFiltrert = it.spørringFiltrert,
            systemprompt = it.systemprompt,
            ekstraParametreJson = it.ekstraParametreJson,
            bryterRetningslinjer = it.bryterRetningslinjer,
            begrunnelse = it.begrunnelse,
            kiNavn = it.kiNavn,
            kiVersjon = it.kiVersjon,
            svartidMs = it.svartidMs,
            lagret = it.lagret,
            manuellKontrollBryterRetningslinjer = it.manuellKontrollBryterRetningslinjer,
            manuellKontrollUtfortAv = it.manuellKontrollUtfortAv,
            manuellKontrollTidspunkt = it.manuellKontrollTidspunkt
        )
    }
    ctx.status(200).json(out)
}

@OpenApi(
    summary = "Hent én logglinje.",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
    responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = KiLoggOutboundDto::class)])],
    path = "$base/logg/{id}",
    methods = [HttpMethod.GET]
)
private fun getHandler(repo: KiLoggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER)
    val id = UUID.fromString(ctx.pathParam("id"))
    val it = repo.findById(id) ?: throw NotFoundResponse("Logg ikke funnet")
    ctx.status(200).json(
        KiLoggOutboundDto(
            id = it.id.toString(),
            opprettetTidspunkt = it.opprettetTidspunkt,
            treffDbId = it.treffDbId,
            feltType = it.feltType,
            spørringFraFrontend = it.spørringFraFrontend,
            spørringFiltrert = it.spørringFiltrert,
            systemprompt = it.systemprompt,
            ekstraParametreJson = it.ekstraParametreJson,
            bryterRetningslinjer = it.bryterRetningslinjer,
            begrunnelse = it.begrunnelse,
            kiNavn = it.kiNavn,
            kiVersjon = it.kiVersjon,
            svartidMs = it.svartidMs,
            lagret = it.lagret,
            manuellKontrollBryterRetningslinjer = it.manuellKontrollBryterRetningslinjer,
            manuellKontrollUtfortAv = it.manuellKontrollUtfortAv,
            manuellKontrollTidspunkt = it.manuellKontrollTidspunkt
        )
    )
}

data class OppdaterManuellRequestDto(
    val bryterRetningslinjer: Boolean
)

data class OppdaterLagretRequestDto(
    val lagret: Boolean
)

data class ValiderMedLoggResponseDto(
    val loggId: String,
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String
)

data class ValiderMedLoggRequestDto(
    val treffDbId: Long,
    val feltType: String, // 'tittel' | 'innlegg'
    val tekst: String
)

data class KiLoggOutboundDto(
    val id: String,
    val opprettetTidspunkt: ZonedDateTime,
    val treffDbId: Long,
    val feltType: String,
    val spørringFraFrontend: String,
    val spørringFiltrert: String,
    val systemprompt: String?,
    val ekstraParametreJson: String?,
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String?,
    val kiNavn: String,
    val kiVersjon: String,
    val svartidMs: Int,
    val lagret: Boolean,
    val manuellKontrollBryterRetningslinjer: Boolean?,
    val manuellKontrollUtfortAv: String?,
    val manuellKontrollTidspunkt: ZonedDateTime?
)