package no.nav.toi.rekrutteringstreff.ki

import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.JacksonConfig
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.dto.ValiderRekrutteringstreffResponsDto
import no.nav.toi.rekrutteringstreff.ki.dto.KiLoggOutboundDto
import java.time.ZonedDateTime
import java.util.*

class KiController (
    private val kiLoggRepository: KiLoggRepository,
    javalin: Javalin
) {
    companion object {
        private const val pathParamTreffId = "id"
        private const val oldBase = "/api/rekrutteringstreff/ki"
        private const val newBase = "/api/rekrutteringstreff/{$pathParamTreffId}/ki"
    }

    init {
        // New endpoints with treffId in path
        javalin.post("$newBase/valider", validerOgLoggHandlerNy())
        javalin.put("$newBase/logg/{loggId}/lagret", oppdaterLagretHandler())
        javalin.put("$newBase/logg/{loggId}/manuell", oppdaterManuellHandler())
        javalin.get("$newBase/logg", listHandler())
        javalin.get("$newBase/logg/{loggId}", getHandler())

        // Backward compatible old endpoints (deprecated)
        //@Deprecated("Bruk /api/rekrutteringstreff/{id}/ki")
        javalin.post("$oldBase/valider", validerOgLoggHandlerGammel())
        javalin.put("$oldBase/logg/{id}/lagret", oppdaterLagretHandler())
        javalin.put("$oldBase/logg/{id}/manuell", oppdaterManuellHandler())
        javalin.get("$oldBase/logg", listHandler())
        javalin.get("$oldBase/logg/{id}", getHandler())
    }

    @Deprecated("Bruk nytt endepunkt med treffId i path")
    @OpenApi(
        summary = "Valider tekst via KI og logg spørringen (gammelt endepunkt).",
        security = [OpenApiSecurity(name = "BearerAuth")],
        requestBody = OpenApiRequestBody(
            required = true,
            content = [OpenApiContent(
                from = ValiderMedLoggRequestDto::class,
                example = """{
              "treffId": "550e8400-e29b-41d4-a716-446655440000",
              "feltType": "tittel",
              "tekst": "Vi søker etter en blid og motivert medarbeider."
            }"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "200",
            description = "Resultat fra validering og referanse til logglinje.",
            content = [OpenApiContent(
                from = ValiderMedLoggResponseDto::class,
                example = """{
              "loggId": "7f1f5a2c-6d2a-4a7b-9c2b-1f0d2a3b4c5d",
              "bryterRetningslinjer": false,
              "begrunnelse": "Ingen sensitive opplysninger eller diskriminerende formuleringer."
            }"""
            )]
        )],
        path = "$oldBase/valider",
        methods = [HttpMethod.POST]
    )
    private fun validerOgLoggHandlerGammel(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        log.info("Gammelt endepunkt for validering KI")
        val req = ctx.bodyAsClass<ValiderMedLoggRequestDto>()
        val treffId = UUID.fromString(req.treffId)
        val (result: ValiderRekrutteringstreffResponsDto, loggId: UUID?) =
            OpenAiClient(repo = kiLoggRepository).validateRekrutteringstreffOgLogg(treffId, req.feltType, req.tekst)
        ctx.status(200).json(
            ValiderMedLoggResponseDto(
                loggId = loggId?.toString() ?: "",
                bryterRetningslinjer = result.bryterRetningslinjer,
                begrunnelse = result.begrunnelse
            )
        )
    }

    @OpenApi(
        summary = "Valider tekst via KI og logg spørringen (nytt endepunkt).",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true, example = "550e8400-e29b-41d4-a716-446655440000")],
        requestBody = OpenApiRequestBody(
            required = true,
            content = [OpenApiContent(
                from = ValiderMedLoggRequestUtenTreffIdDto::class,
                example = """{
              "feltType": "tittel",
              "tekst": "Vi søker etter en blid og motivert medarbeider."
            }"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "200",
            description = "Resultat fra validering og referanse til logglinje.",
            content = [OpenApiContent(
                from = ValiderMedLoggResponseDto::class,
                example = """{
              "loggId": "7f1f5a2c-6d2a-4a7b-9c2b-1f0d2a3b4c5d",
              "bryterRetningslinjer": false,
              "begrunnelse": "Ingen sensitive opplysninger eller diskriminerende formuleringer."
            }"""
            )]
        )],
        path = "$newBase/valider",
        methods = [HttpMethod.POST]
    )
    private fun validerOgLoggHandlerNy(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val req = ctx.bodyAsClass<ValiderMedLoggRequestUtenTreffIdDto>()
        val treffId = UUID.fromString(ctx.pathParam(pathParamTreffId))
        val (result: ValiderRekrutteringstreffResponsDto, loggId: UUID?) =
            OpenAiClient(repo = kiLoggRepository).validateRekrutteringstreffOgLogg(treffId, req.feltType, req.tekst)
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
        pathParams = [OpenApiParam(
            name = "id", type = UUID::class, required = true,
            example = "7f1f5a2c-6d2a-4a7b-9c2b-1f0d2a3b4c5d"
        )],
        requestBody = OpenApiRequestBody(
            required = true,
            content = [OpenApiContent(
                from = OppdaterLagretRequestDto::class,
                example = """{ "lagret": true }"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "200",
            description = "Oppdatert.",
            content = [OpenApiContent(from = Map::class, example = "{}")]
        )],
        path = "$newBase/logg/{loggId}/lagret",
        methods = [HttpMethod.PUT]
    )
    private fun oppdaterLagretHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER)
        val id = UUID.fromString(runCatching { ctx.pathParam("loggId") }.getOrElse { ctx.pathParam("id") })
        val req = ctx.bodyAsClass<OppdaterLagretRequestDto>()
        if (kiLoggRepository.setLagret(id, req.lagret) == 0) throw NotFoundResponse("Logg ikke funnet")
        ctx.status(200).json(emptyMap<String, String>())
    }

    @OpenApi(
        summary = "Registrer resultat av manuell kontroll.",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(
            name = "id", type = UUID::class, required = true,
            example = "7f1f5a2c-6d2a-4a7b-9c2b-1f0d2a3b4c5d"
        )],
        requestBody = OpenApiRequestBody(
            required = true,
            content = [OpenApiContent(
                from = OppdaterManuellRequestDto::class,
                example = """{ "bryterRetningslinjer": false }"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "200",
            description = "Oppdatert.",
            content = [OpenApiContent(from = Map::class, example = "{}")]
        )],
        path = "$newBase/logg/{loggId}/manuell",
        methods = [HttpMethod.PUT]
    )
    private fun oppdaterManuellHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER)
        val id = UUID.fromString(runCatching { ctx.pathParam("loggId") }.getOrElse { ctx.pathParam("id") })
        val req = ctx.bodyAsClass<OppdaterManuellRequestDto>()

        val ident: String?
        val now: ZonedDateTime?

        if (req.bryterRetningslinjer == null) {
            ident = null
            now = null
        } else {
            ident = ctx.extractNavIdent()
            now = ZonedDateTime.now()
        }

        if (kiLoggRepository.setManuellKontroll(id, req.bryterRetningslinjer, ident, now) == 0) {
            throw NotFoundResponse("Logg ikke funnet")
        }
        ctx.status(200).json(emptyMap<String, String>())
    }

    @OpenApi(
        summary = "List logglinjer (filtrerbar på TreffId og feltType).",
        security = [OpenApiSecurity(name = "BearerAuth")],
        queryParams = [
            OpenApiParam(name = "treffId", type = String::class, required = false, example = "550e8400-e29b-41d4-a716-446655440000"),
            OpenApiParam(name = "feltType", type = String::class, required = false, example = "innlegg"),
            OpenApiParam(name = "limit", type = Int::class, required = false, example = "50"),
            OpenApiParam(name = "offset", type = Int::class, required = false, example = "0")
        ],
        responses = [OpenApiResponse(
            status = "200",
            description = "Liste over logglinjer. prompt*-feltene returneres slik de ble lagret. For gamle rader kan de være null.",
            content = [OpenApiContent(
                from = Array<KiLoggOutboundDto>::class,
                example = """[
              {
                "id": "3f9e8f0a-12ab-4c3d-9f45-2b34c6d7e890",
                "opprettetTidspunkt": "2025-08-20T12:34:56.789+02:00[Europe/Oslo]",
                "treffId": "550e8400-e29b-41d4-a716-446655440000",
                "tittel": "Sommerjobbmesse på NAV",
                "feltType": "innlegg",
                "spørringFraFrontend": "{\"treffId\":\"...\"}",
                "spørringFiltrert": "{\"feltType\":\"...\"}",
                "systemprompt": "Du er en ekspert på å vurdere informasjon, ...",
                "bryterRetningslinjer": false,
                "begrunnelse": "Ingen brudd oppdaget.",
                "kiNavn": "azure-openai",
                "kiVersjon": "toi-gpt-4.1",
                "svartidMs": 420,
                "lagret": true,
                "manuellKontrollBryterRetningslinjer": null,
                "manuellKontrollUtfortAv": null,
                "manuellKontrollTidspunkt": null,
                "promptVersjonsnummer": "1",
                "promptEndretTidspunkt": "2025-08-22T12:00:00+02:00[Europe/Oslo]",
                "promptHash": "a61803"
              },
              {
                "id": "old-row-without-extra",
                "opprettetTidspunkt": "2024-05-01T08:00:00+02:00[Europe/Oslo]",
                "treffId": "",
                "tittel": null,
                "feltType": "tittel",
                "spørringFraFrontend": "…",
                "spørringFiltrert": "…",
                "systemprompt": null,
                "bryterRetningslinjer": false,
                "begrunnelse": null,
                "kiNavn": "azure-openai",
                "kiVersjon": "toi-gpt-4.1",
                "svartidMs": 200,
                "lagret": false,
                "manuellKontrollBryterRetningslinjer": null,
                "manuellKontrollUtfortAv": null,
                "manuellKontrollTidspunkt": null,
                "promptVersjonsnummer": null,
                "promptEndretTidspunkt": null,
                "promptHash": null
              }
            ]"""
            )]
        )],
        path = "$newBase/logg",
        methods = [HttpMethod.GET]
    )
    private fun listHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER)
        val treffId = ctx.queryParam("treffId")?.let(UUID::fromString)
        val feltType = ctx.queryParam("feltType")
        val limit = ctx.queryParam("limit")?.toInt() ?: 50
        val offset = ctx.queryParam("offset")?.toInt() ?: 0

        val rows = kiLoggRepository.list(treffId, feltType, limit, offset)
        val mapper = JacksonConfig.mapper

        val out = rows.map { row ->
            val meta = row.ekstraParametreJson?.let {
                try { mapper.readValue<EkstraMeta>(it) } catch (_: Exception) { null }
            }
            KiLoggOutboundDto(
                id = row.id.toString(),
                opprettetTidspunkt = row.opprettetTidspunkt,
                treffId = row.treffId?.toString() ?: "",
                tittel = row.tittel,
                feltType = row.feltType,
                spørringFraFrontend = row.spørringFraFrontend,
                spørringFiltrert = row.spørringFiltrert,
                systemprompt = row.systemprompt,
                bryterRetningslinjer = row.bryterRetningslinjer,
                begrunnelse = row.begrunnelse,
                kiNavn = row.kiNavn,
                kiVersjon = row.kiVersjon,
                svartidMs = row.svartidMs,
                lagret = row.lagret,
                manuellKontrollBryterRetningslinjer = row.manuellKontrollBryterRetningslinjer,
                manuellKontrollUtfortAv = row.manuellKontrollUtfortAv,
                manuellKontrollTidspunkt = row.manuellKontrollTidspunkt,
                promptVersjonsnummer = meta?.promptVersjonsnummer,
                promptEndretTidspunkt = meta?.promptEndretTidspunkt?.let(ZonedDateTime::parse),
                promptHash = meta?.promptHash
            )
        }

        ctx.status(200).json(out)
    }

    @OpenApi(
        summary = "Hent én logglinje.",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(
            name = "id", type = UUID::class, required = true,
            example = "3f9e8f0a-12ab-4c3d-9f45-2b34c6d7e890"
        )],
        responses = [OpenApiResponse(
            status = "200",
            description = "Returnerer logglinje. prompt*-feltene er de lagrede verdiene (kan være null for eldre rader).",
            content = [OpenApiContent(
                from = KiLoggOutboundDto::class,
                example = """{
              "id": "3f9e8f0a-12ab-4c3d-9f45-2b34c6d7e890",
              "opprettetTidspunkt": "2025-08-20T12:34:56.789+02:00[Europe/Oslo]",
              "treffId": "550e8400-e29b-41d4-a716-446655440000",
              "tittel": "Sommerjobbmesse på NAV",
              "feltType": "innlegg",
              "spørringFraFrontend": "{\"treffId\":\"...\"}",
              "spørringFiltrert": "{\"feltType\":\"...\"}",
              "systemprompt": "Du er en ekspert på å vurdere informasjon, ...",
              "bryterRetningslinjer": false,
              "begrunnelse": "Ingen brudd oppdaget.",
              "kiNavn": "azure-openai",
              "kiVersjon": "toi-gpt-4.1",
              "svartidMs": 420,
              "lagret": true,
              "manuellKontrollBryterRetningslinjer": null,
              "manuellKontrollUtfortAv": null,
              "manuellKontrollTidspunkt": null,
              "promptVersjonsnummer": "1",
              "promptEndretTidspunkt": "2025-08-22T12:00:00+02:00[Europe/Oslo]",
              "promptHash": "a61803"
            }"""
            )]
        )],
        path = "$newBase/logg/{id}",
        methods = [HttpMethod.GET]
    )
    private fun getHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER)
        val id = UUID.fromString(runCatching { ctx.pathParam("loggId") }.getOrElse { ctx.pathParam("id") })
        val row = kiLoggRepository.findById(id) ?: throw NotFoundResponse("Logg ikke funnet")

        val mapper = JacksonConfig.mapper
        val meta = row.ekstraParametreJson?.let {
            try { mapper.readValue<EkstraMeta>(it) } catch (_: Exception) { null }
        }

        val dto = KiLoggOutboundDto(
            id = row.id.toString(),
            opprettetTidspunkt = row.opprettetTidspunkt,
            treffId = row.treffId?.toString() ?: "",
            tittel = row.tittel,
            feltType = row.feltType,
            spørringFraFrontend = row.spørringFraFrontend,
            spørringFiltrert = row.spørringFiltrert,
            systemprompt = row.systemprompt,
            bryterRetningslinjer = row.bryterRetningslinjer,
            begrunnelse = row.begrunnelse,
            kiNavn = row.kiNavn,
            kiVersjon = row.kiVersjon,
            svartidMs = row.svartidMs,
            lagret = row.lagret,
            manuellKontrollBryterRetningslinjer = row.manuellKontrollBryterRetningslinjer,
            manuellKontrollUtfortAv = row.manuellKontrollUtfortAv,
            manuellKontrollTidspunkt = row.manuellKontrollTidspunkt,
            promptVersjonsnummer = meta?.promptVersjonsnummer,
            promptEndretTidspunkt = meta?.promptEndretTidspunkt?.let(ZonedDateTime::parse),
            promptHash = meta?.promptHash
        )

        ctx.status(200).json(dto)
    }

    private data class EkstraMeta(
        val promptVersjonsnummer: Int?,
        val promptEndretTidspunkt: String?,
        val promptHash: String?
    )
}

data class OppdaterManuellRequestDto(val bryterRetningslinjer: Boolean?)
data class OppdaterLagretRequestDto(val lagret: Boolean)

data class ValiderMedLoggResponseDto(
    val loggId: String,
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String
)

data class ValiderMedLoggRequestDto(
    val treffId: String,
    val feltType: String,
    val tekst: String
)

data class ValiderMedLoggRequestUtenTreffIdDto(
    val feltType: String,
    val tekst: String
)
