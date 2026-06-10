package no.nav.toi.formidling

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.RuteRegistrerer
import no.nav.toi.authenticatedUser
import no.nav.toi.formidling.dto.FormidlingOutboundDto
import no.nav.toi.formidling.dto.OpprettFormidlingDto
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.LoggerFactory
import java.util.*

class FormidlingController(
    private val formidlingService: FormidlingService,
) : RuteRegistrerer {
    companion object {
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val pathParamTreffId = "id"
        private const val formidlingPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/formidling"
    }

    override fun registrer(routes: JavalinDefaultRoutingApi) {
        routes.post(formidlingPath, opprettFormidlingHandler())
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

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
                example = "{\"eierNavKontorEnhetId\":\"1124\",\"orgnr\":\"973626183\",\"fødselsnumre\":[\"12345678901\",\"10987654321\"],\"stilling\":{\"employer\":{\"name\":\"VELFERDSETATEN ADMINISTRASJON\",\"orgnr\":\"973626183\",\"publicName\":\"VELFERDSETATEN ADMINISTRASJON\"},\"locationList\":[{\"county\":\"ROGALAND\",\"municipal\":\"SOLA\",\"municipalCode\":\"1124\",\"country\":\"NORGE\"}],\"categoryList\":[{\"code\":\"19989\",\"categoryType\":\"JANZZ\",\"name\":\"Utvikler (dataspill)\"}],\"properties\":{\"sector\":\"Privat\",\"engagementtype\":\"Fast\",\"extent\":\"Heltid\"}}}"
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
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        logger.info("Opprett en formidling for rekrutteringstreff $treffId")
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)
        val dto = ctx.bodyAsClass<OpprettFormidlingDto>()
        val navIdent = ctx.extractNavIdent()
        val userToken = ctx.authenticatedUser().innkommendeToken()
        try {
            logger.info("Prøver å opprette en formidling for rekrutteringstreff $treffId - arbeidsgiver: ${dto.orgnr}")
            val opprettede = formidlingService.opprettFormidling(treffId, dto, navIdent, userToken)
            logger.info("Opprettet ${opprettede.size} formidlinger for treff $treffId")
            ctx.status(201).json(opprettede.map { it.toOutboundDto() })
        } catch (e: ArbeidsgiverIkkeFunnetException) {
            throw BadRequestResponse(e.message ?: "Arbeidsgiver finnes ikke på treffet")
        } catch (e: JobbsøkerIkkeFunnetPåTreffException) {
            throw BadRequestResponse(e.message ?: "Jobbsøker finnes ikke på treffet")
        }
    }

    private fun Formidling.toOutboundDto() = FormidlingOutboundDto(
        formidlingId = id.toString(),
        stillingId = stillingId.toString(),
        opprettetTidspunkt = opprettetTidspunkt.toString(),
    )
}
