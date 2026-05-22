package no.nav.toi.formidling

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.formidling.dto.FormidlingOutboundDto
import no.nav.toi.formidling.dto.OpprettFormidlingDto
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.LoggerFactory
import java.util.*

class FormidlingController(
    private val formidlingService: FormidlingService,
    routes: JavalinDefaultRoutingApi,
) {
    companion object {
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val pathParamTreffId = "id"
        private const val formidlingPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/formidling"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        routes.post(formidlingPath, opprettFormidlingHandler())
    }

    @OpenApi(
        summary = "Opprett en formidling for et rekrutteringstreff",
        operationId = "opprettFormidling",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            required = true,
            description = "Rekrutteringstreffets unike identifikator (UUID)"
        )],
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(
                from = OpprettFormidlingDto::class,
                example = """{ "fødselsnumre": ["12345678901", "10987654321"], "orgnr": "123456789" }"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "201",
            content = [OpenApiContent(from = FormidlingOutboundDto::class)]
        )],
        path = formidlingPath,
        methods = [HttpMethod.POST]
    )
    private fun opprettFormidlingHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val dto = ctx.bodyAsClass<OpprettFormidlingDto>()
        val navIdent = ctx.extractNavIdent()
        val userToken = ctx.attribute<String>("raw_token")
            ?: throw io.javalin.http.UnauthorizedResponse("Missing token")
        try {
            val opprettede = formidlingService.opprettFormidling(dto, navIdent, userToken)
            logger.info("Opprettet ${opprettede.size} formidlinger for treff $treffId")
            ctx.status(201).json(opprettede.map { it.toOutboundDto() })
        } catch (e: ArbeidsgiverIkkeFunnetException) {
            throw BadRequestResponse(e.message ?: "Arbeidsgiver finnes ikke på treffet")
        } catch (e: JobbsøkerIkkeFunnetPåTreffException) {
            throw BadRequestResponse(e.message ?: "Jobbsøker finnes ikke på treffet")
        }
    }

    private fun Formidling.toOutboundDto() = FormidlingOutboundDto(
        id = id.toString(),
        opprettetTidspunkt = opprettetTidspunkt.toString(),
    )
}
