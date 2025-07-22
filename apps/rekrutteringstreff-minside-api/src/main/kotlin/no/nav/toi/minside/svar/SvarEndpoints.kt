package no.nav.toi.minside.svar

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
import no.nav.toi.minside.svar.SvarEndpoints.Companion.HENT_REKRUTTERINGSTREFF_SVAR
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

class SvarEndpoints {
    companion object {
        private const val ENDEPUNKT_REKRUTTERINGSTREFF = "/api/rekrutteringstreff"
        private const val PATH_PARAM_TREFFID = "id"
        const val HENT_REKRUTTERINGSTREFF_SVAR = "$ENDEPUNKT_REKRUTTERINGSTREFF/{$PATH_PARAM_TREFFID}/svar"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @OpenApi(
        summary = "Hent svar for ett rekrutteringstreff",
        operationId = "hentRekrutteringstreffSvar",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = PATH_PARAM_TREFFID, type = UUID::class, required = true)],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = RekrutteringstreffSvarOutboundDto::class,
                example = """{
               "påmeldt":true,
            }"""
            )]
        )],
        path = HENT_REKRUTTERINGSTREFF_SVAR,
        methods = [HttpMethod.GET]
    )
    fun hentRekrutteringstreffSvarHandler(treffKlient: RekrutteringstreffKlient): (Context) -> Unit = { ctx ->
        val id = ctx.pathParam(PATH_PARAM_TREFFID)

        // Sjekker om treffet finnes
        treffKlient.hent(id, ctx.authenticatedUser().jwt)?.tilDTOForBruker()
            ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")

        // TODO: Gir foreløpig et tilfeldig svar for demonstrasjon i påvente av endelig endepunkt i rekrutteringstreff-api
        val svar = RekrutteringstreffSvarOutboundDto(Random.nextBoolean())

        log.info("Henter svar for rekrutteringstreff med id: $id, svar: ${svar.json()}")

        ctx.status(200).json(svar.json())
    }
}

fun Javalin.rekrutteringstreffSvarEndepunkt(treffKlient: RekrutteringstreffKlient) = get(HENT_REKRUTTERINGSTREFF_SVAR, SvarEndpoints().hentRekrutteringstreffSvarHandler(treffKlient))

data class RekrutteringstreffSvarOutboundDto(
    private val påmeldt: Boolean,
) {
    fun json() = """
        {
            "påmeldt": $påmeldt
        }
    """.trimIndent()
}
