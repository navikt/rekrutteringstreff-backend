package no.nav.toi.minside.svar

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import no.nav.toi.minside.authenticatedUser
import no.nav.toi.minside.rekrutteringstreff.RekrutteringstreffKlient
import no.nav.toi.minside.svar.SvarEndpoints.Companion.REKRUTTERINGSTREFF_SVAR_URL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class SvarEndpoints {
    companion object {
        private const val ENDEPUNKT_REKRUTTERINGSTREFF = "/api/rekrutteringstreff"
        private const val PATH_PARAM_TREFFID = "id"
        const val REKRUTTERINGSTREFF_SVAR_URL = "$ENDEPUNKT_REKRUTTERINGSTREFF/{$PATH_PARAM_TREFFID}/svar"
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
                    "erInvitert": true,
                    "erPåmeldt": true,
                    "harSvart": true
                }"""
            )]
        )],
        path = REKRUTTERINGSTREFF_SVAR_URL,
        methods = [HttpMethod.GET]
    )
    fun hentRekrutteringstreffSvarHandler(treffKlient: RekrutteringstreffKlient, borgerKlient: BorgerKlient): (Context) -> Unit = { ctx ->
        val rekrutteringstreffId = ctx.pathParam(PATH_PARAM_TREFFID)

        // Sjekker om treffet finnes
        val rekrutteringstreff = treffKlient.hent(rekrutteringstreffId, ctx.authenticatedUser().jwt)?.tilDTOForBruker()
        if (rekrutteringstreff == null) {
            log.info("Rekrutteringstreff med id: $rekrutteringstreffId finnes ikke, så kan ikke hente svar")
            ctx.status(404)
        } else {
            log.info("Hentet rekrutteringstreff med id: $rekrutteringstreffId for å hente svar, treffet finnes")
            // hent påmeldingsstatus for treffet
            val jobbsøkerMedStatuser = borgerKlient.hentJobbsøkerMedStatuser(rekrutteringstreffId, ctx.authenticatedUser().jwt)
            val statuser = jobbsøkerMedStatuser.statuser

            val svar = RekrutteringstreffSvarOutboundDto(
                erInvitert = statuser.erInvitert,
                erPåmeldt = statuser.erPåmeldt,
                harSvart = statuser.harSvart,
            )

            log.info("Jobbsøker har svart følgende på rekkrutteringstreffet med id: $rekrutteringstreffId, svar: ${svar.json()}")
            ctx.status(200).json(svar.json())
        }
    }

    @OpenApi(
        summary = "Avgi svar for ett rekrutteringstreff",
        operationId = "avgiSvar",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = PATH_PARAM_TREFFID, type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = AvgiSvarInputDto::class,
            example = """
                {
                    "erPåmeldt": true
                }
            """
        )]
        ),
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = RekrutteringstreffSvarOutboundDto::class,
                example = """{
                    "rekrutteringstreffId": 123,
                    "erPåmeldt": true
                }"""
            )]
        )],
        path = REKRUTTERINGSTREFF_SVAR_URL,
        methods = [HttpMethod.PUT]
    )
    fun putRekrutteringstreffSvarHandler(treffKlient: RekrutteringstreffKlient, borgerKlient: BorgerKlient): (Context) -> Unit = { ctx ->
        log.info("putRekrutteringstreffSvarHandler()")
        val rekrutteringstreffId = ctx.pathParam(PATH_PARAM_TREFFID)
        val inputDto = ctx.bodyAsClass<AvgiSvarInputDto>()
        log.info("Mottatt svar for rekrutteringstreff med id: ${rekrutteringstreffId} erPåmeldt: ${inputDto.erPåmeldt}")

        // Sjekker om treffet finnes
        treffKlient.hent(rekrutteringstreffId, ctx.authenticatedUser().jwt)?.tilDTOForBruker()
           ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")


         log.info("rekrutteringstreff funnet for id: ${rekrutteringstreffId}, skal nå lagre svar for treffet")

        try {
            borgerKlient.svarPåTreff(rekrutteringstreffId, ctx.authenticatedUser().jwt, inputDto.erPåmeldt)
            log.info("Svarer 200 OK på svar for rekrutteringstreff med id: ${rekrutteringstreffId} erPåmeldt: ${inputDto.erPåmeldt}")

            ctx.status(200).json(AvgiSvarOutputDto(
                rekrutteringstreffId = rekrutteringstreffId,
                erPåmeldt = inputDto.erPåmeldt
             ))
        } catch (e: Exception) {
            log.info("Svarer med statuskode 500 - Fikk følgende exception ved svar på treff ${rekrutteringstreffId}", e)
            ctx.status(500)
        }
    }
}

fun Javalin.rekrutteringstreffSvarEndepunkt(treffKlient: RekrutteringstreffKlient, borgerKlient: BorgerKlient) {
    put(REKRUTTERINGSTREFF_SVAR_URL, SvarEndpoints().putRekrutteringstreffSvarHandler(treffKlient, borgerKlient))
    get(REKRUTTERINGSTREFF_SVAR_URL, SvarEndpoints().hentRekrutteringstreffSvarHandler(treffKlient, borgerKlient))
}

data class RekrutteringstreffSvarOutboundDto(
    private val erInvitert: Boolean,
    private val erPåmeldt: Boolean,
    private val harSvart: Boolean,
) {
    fun json() = """
        {
            "erInvitert": $erInvitert,
            "erPåmeldt": $erPåmeldt,
            "harSvart": $harSvart
        }
    """.trimIndent()
}

data class AvgiSvarInputDto(
    val erPåmeldt: Boolean,
)

data class AvgiSvarOutputDto(
    val rekrutteringstreffId: String,
    val erPåmeldt: Boolean,
)
