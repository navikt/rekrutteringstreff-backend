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
import kotlin.random.Random

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
        val id = ctx.pathParam(PATH_PARAM_TREFFID)

        // Sjekker om treffet finnes
        treffKlient.hent(id, ctx.authenticatedUser().jwt)?.tilDTOForBruker()
            ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")

        // hent påmeldingsstatus for treffet
        try {
            val jobbsøkerMedStatuser = borgerKlient.hentJobbsøkerMedStatuser(id, ctx.authenticatedUser().jwt)
            log.info("jobbsøkerMedStatuser: $jobbsøkerMedStatuser");
            // TODO: Bruk statusen til å sende svar tilbake
        } catch (e: Exception) {
            log.error("Feil ved henting av svar for rekrutteringstreff med id: $id", e)
        }

        // TODO: Gir foreløpig et tilfeldig svar for demonstrasjon i påvente av endelig endepunkt i rekrutteringstreff-api
        val svar = RekrutteringstreffSvarOutboundDto(Random.nextBoolean(), Random.nextBoolean(), Random.nextBoolean())

        log.info("Henter svar for rekrutteringstreff med id: $id, svar: ${svar.json()}")

        ctx.status(200).json(svar.json())
    }

    @OpenApi(
        summary = "Abgi svar for ett rekrutteringstreff",
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
        methods = [HttpMethod.POST]
    )
    fun postRekrutteringstreffSvarHandler(treffKlient: RekrutteringstreffKlient, borgerKlient: BorgerKlient): (Context) -> Unit = { ctx ->
        val id = ctx.pathParam(PATH_PARAM_TREFFID)
        val inputDto = ctx.bodyAsClass<AvgiSvarInputDto>()

        log.info("Mottatt svar for rekrutteringstreff med id: $id erPåmeldt: ${inputDto.erPåmeldt}")

        // Sjekker om treffet finnes
        treffKlient.hent(id, ctx.authenticatedUser().jwt)?.tilDTOForBruker()
           ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")

        // TODO: Kall på rekrutteringstreff-api for å lagre påmeldingsstatus. Foreløpig bare en stub for å teste frontend

        ctx.status(200).json(AvgiSvarOutputDto(
            rekrutteringstreffId = id,
            erPåmeldt = inputDto.erPåmeldt
        ))
    }
}

fun Javalin.rekrutteringstreffSvarEndepunkt(treffKlient: RekrutteringstreffKlient, borgerKlient: BorgerKlient) {
    post(REKRUTTERINGSTREFF_SVAR_URL, SvarEndpoints().postRekrutteringstreffSvarHandler(treffKlient, borgerKlient))
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
