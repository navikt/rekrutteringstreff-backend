package no.nav.toi.oppfølging

import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.RuteRegistrerer
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.eier.krevEierEllerUtvikler
import no.nav.toi.treffgjennomføring.TreffgjennomføringController.Companion.AGGREGAT_EKSEMPEL
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import java.util.*

class OppfølgingController(
    private val oppfølgingService: OppfølgingService,
    private val eierService: EierService,
) : RuteRegistrerer {

    companion object {
        private const val oppfølgingPath = "/api/rekrutteringstreff/{id}/oppfolging"
        const val VURDERINGER = "$oppfølgingPath/vurderinger"
    }

    override fun registrer(routes: JavalinDefaultRoutingApi) {
        routes.put(VURDERINGER, vurderingHandler())
    }

    @OpenApi(
        summary = "Sett eller fjern vurdering og oppfølging for ett par",
        description = "En rad uten vurdering, notater, 2. intervju eller jobbtilbud slettes.",
        operationId = "lagreVurdering",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = "id", type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = VurderingDto::class,
            example = """{"personTreffId": "11111111-1111-1111-1111-111111111111", "arbeidsgiverTreffId": "22222222-2222-2222-2222-222222222222", "vurdering": "AKTUELL", "notater": ["AG_GODT_INNTRYKK"], "andregangsintervju": true, "andregangsintervjuDato": "2026-09-01", "jobbtilbud": false}""",
        )]),
        responses = [OpenApiResponse(status = "200", content = [OpenApiContent(from = TreffgjennomføringDto::class, example = AGGREGAT_EKSEMPEL)])],
        path = VURDERINGER,
        methods = [HttpMethod.PUT],
    )
    private fun vurderingHandler(): (Context) -> Unit = { ctx ->
        val treffId = TreffId(ctx.pathParam("id"))
        val navIdent = ctx.krevEierEllerUtvikler(eierService, treffId)
        val dto = ctx.bodyAsClass<VurderingDto>()
        ctx.status(200).json(oppfølgingService.lagreVurdering(treffId, dto, navIdent))
    }
}
