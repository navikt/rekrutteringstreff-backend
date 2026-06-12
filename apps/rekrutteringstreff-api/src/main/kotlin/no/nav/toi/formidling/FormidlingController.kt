package no.nav.toi.formidling

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.AuditLog
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.RuteRegistrerer
import no.nav.toi.authenticatedUser
import no.nav.toi.formidling.dto.FormidlingMedPersonOgArbeidsgiver
import no.nav.toi.formidling.dto.FormidlingOutboundDto
import no.nav.toi.formidling.dto.OpprettFormidlingDto
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.slf4j.LoggerFactory
import java.util.*

class FormidlingController(
    private val formidlingService: FormidlingService,
    private val eierService: EierService,
    private val modiaKlient: ModiaKlient,
) : RuteRegistrerer {
    companion object {
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val pathParamTreffId = "id"
        private const val formidlingPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/formidling"
        private const val formidlingListeAllePath = "$formidlingPath/liste/alle"
        private const val formidlingListeEgnePath = "$formidlingPath/liste/egne"
    }

    override fun registrer(routes: JavalinDefaultRoutingApi) {
        routes.post(formidlingPath, opprettFormidlingHandler())
        routes.get(formidlingListeAllePath, hentAlleFormidlingerHandler())
        routes.get(formidlingListeEgnePath, hentEgneFormidlingerHandler())
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
                example = """
                    {
                        "eierNavKontorEnhetId": "1124",
                        "orgnr": "973626183",
                        "fødselsnumre": ["41017512345", "42026598765"],
                        "stilling": {
                            "employer": {
                                "name": "VELFERDSETATEN ADMINISTRASJON",
                                "orgnr": "973626183",
                                "publicName": "VELFERDSETATEN ADMINISTRASJON"
                            },
                            "locationList": [{
                                "county": "ROGALAND",
                                "municipal": "SOLA",
                                "municipalCode": "1124",
                                "country": "NORGE"
                            }],
                            "categoryList": [{
                                "code": "19989",
                                "categoryType": "JANZZ",
                                "name": "Utvikler (dataspill)"
                            }],
                            "properties": {
                                "sector": "Privat",
                                "engagementtype": "Fast",
                                "extent": "Heltid"
                            }
                        }
                    }
                """
            )]
        ),
        responses = [OpenApiResponse(
            status = "201",
            content = [OpenApiContent(
from = Array<FormidlingOutboundDto>::class,
                example = """
                    [
                        {
                            "id": "f1b2c3d4-0000-0000-0000-000000000001",
                            "stillingId": "c1d2e3f4-0000-0000-0000-000000000002",
                            "opprettetTidspunkt": "2026-01-15T10:30:00+01:00[Europe/Oslo]"
                        },
                        {
                            "id": "f1b2c3d4-0000-0000-0000-000000000003",
                            "stillingId": "c1d2e3f4-0000-0000-0000-000000000002",
                            "opprettetTidspunkt": "2026-01-15T10:30:01+01:00[Europe/Oslo]"
                        }
                    ]
                """
            )]
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

    @OpenApi(
        summary = "Hent alle formidlinger for et rekrutteringstreff",
        description = "Returnerer alle ikke-slettede formidlinger på treffet. " +
            "Tilgang for arbeidsgiverrettet rolle: enten er innlogget bruker eier/utvikler, " +
            "eller treffet er tilknyttet minst ett av kontorene brukeren er tilknyttet i Modia.",
        operationId = "hentAlleFormidlinger",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [
            OpenApiResponse(
                status = "200",
                content = [OpenApiContent(
                    from = Array<FormidlingMedPersonOgArbeidsgiver>::class,
                    example = """
                        [
                            {
                                "id": "a1b2c3d4-0000-0000-0000-000000000001",
                                "opprettetTidspunkt": "2026-01-15T10:30:00+01:00[Europe/Oslo]",
                                "fødselsnummer": "41017512345",
                                "fornavn": "Testperson",
                                "etternavn": "Én",
                                "orgnr": "999999991",
                                "orgnavn": "Test Arbeidsgiver AS",
                                "stillingId": "c1d2e3f4-0000-0000-0000-000000000002"
                            }
                        ]
                    """
                )]
            ),
            OpenApiResponse(status = "403", description = "Bruker har ikke tilgang til formidlingslisten."),
        ],
        path = formidlingListeAllePath,
        methods = [HttpMethod.GET]
    )
    private fun hentAlleFormidlingerHandler(): (Context) -> Unit = { ctx ->
        val innloggetBruker = ctx.authenticatedUser()
        innloggetBruker.verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()

        val harTilgang = eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)
            || run {
                val tilknyttedeEnheter = modiaKlient.hentMineEnheter(innloggetBruker.innkommendeToken())
                eierService.harTilgangViaTreffkontor(treffId, tilknyttedeEnheter)
            }
        if (!harTilgang) {
            throw ForbiddenResponse("Personen har ikke tilgang til formidlingslisten for rekrutteringstreffet")
        }

        AuditLog.loggVisningAvJobbsøkereTilhørendesRekrutteringstreff(navIdent, treffId)
        ctx.status(200).json(formidlingService.hentAlleFormidlingerForTreff(treffId))
    }

    @OpenApi(
        summary = "Hent egne formidlinger for et rekrutteringstreff",
        description = "Returnerer formidlinger på treffet der innlogget bruker er veileder for jobbsøkeren " +
            "eller tilhører samme kontor som jobbsøkeren. Krever jobbsøkerrettet rolle.",
        operationId = "hentEgneFormidlinger",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [
            OpenApiResponse(
                status = "200",
                content = [OpenApiContent(
                    from = Array<FormidlingMedPersonOgArbeidsgiver>::class,
                    example = """
                        [
                            {
                                "id": "a1b2c3d4-0000-0000-0000-000000000001",
                                "opprettetTidspunkt": "2026-01-15T10:30:00+01:00[Europe/Oslo]",
                                "fødselsnummer": "41017512345",
                                "fornavn": "Testperson",
                                "etternavn": "Én",
                                "orgnr": "999999991",
                                "orgnavn": "Test Arbeidsgiver AS",
                                "stillingId": "c1d2e3f4-0000-0000-0000-000000000002"
                            }
                        ]
                    """
                )]
            ),
        ],
        path = formidlingListeEgnePath,
        methods = [HttpMethod.GET]
    )
    private fun hentEgneFormidlingerHandler(): (Context) -> Unit = { ctx ->
        val innloggetBruker = ctx.authenticatedUser()
        innloggetBruker.verifiserAutorisasjon(Rolle.JOBBSØKER_RETTET)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.extractNavIdent()
        val tilknyttedeEnheter = modiaKlient.hentMineEnheter(innloggetBruker.innkommendeToken())

        AuditLog.loggVisningAvJobbsøkereTilhørendesRekrutteringstreff(navIdent, treffId)
        ctx.status(200).json(
            formidlingService.hentEgneFormidlingerForTreff(treffId, navIdent, tilknyttedeEnheter)
        )
    }

    private fun Formidling.toOutboundDto() = FormidlingOutboundDto(
        id = id,
        stillingId = stillingId,
        opprettetTidspunkt = opprettetTidspunkt,
    )
}