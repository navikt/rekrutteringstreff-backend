package no.nav.toi.jobbsoker

import io.javalin.router.JavalinDefaultRoutingApi
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.HttpStatus
import io.javalin.openapi.*
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierService
import java.util.*
import no.nav.toi.RuteRegistrerer

data class KandidatnummerDto(val kandidatnummer: String)

/**
 * Denne controlleren henter data om personene utenom rekrutteirngstreff, foreløpig fra kandidatsøket.
 */
class JobbsøkerOutboundController(
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val kandidatsøkKlient: KandidatsøkKlient,
    private val eierService: EierService,
) : RuteRegistrerer {
    companion object {
        private const val pathParamPersonTreffId = "personTreffId"
        private const val pathParamTreffId = "treffId"
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff/{$pathParamTreffId}"
        private const val eksternKandidatnummerPath = "$endepunktRekrutteringstreff/jobbsoker/{$pathParamPersonTreffId}/kandidatnummer"
    }
    override fun registrer(routes: JavalinDefaultRoutingApi) {
        routes.get(eksternKandidatnummerPath, hentKandidatnummerHandler())
    }

    @OpenApi(
        summary = "Hent kandidatnummer for en jobbsøker basert på personTreffId",
        operationId = "hentKandidatnummerForPersonTreffId",
        tags = ["jobbsøker-outbound"],
        pathParams = [OpenApiParam(
            name = pathParamPersonTreffId,
            type = UUID::class,
            required = true,
            description = "Jobbsøkerens unike identifikator for treffet (UUID)"
        ), OpenApiParam(
            name = pathParamTreffId,
            type = UUID::class,
            required = true,
            description = "Rekrutteringstreffets unike identifikator (UUID)"
        )
        ],
        responses = [
            OpenApiResponse("200", content = [OpenApiContent(from = KandidatnummerDto::class)]),
            OpenApiResponse("404", description = "Jobbsøker eller kandidatnummer ikke funnet")
        ],
        path = eksternKandidatnummerPath,
        methods = [HttpMethod.GET]
    )
    private fun hentKandidatnummerHandler(): (Context) -> Unit = { ctx ->
        val innloggetBruker = ctx.authenticatedUser()
        // Om vi legger til veiledertilganger her, må vi huske å sjekke at at veileder skal ha tilgang til den enkelte kandidaten, ikke bare at veileder har tilgang til treffet. Det er enklere å legge til veiledertilganger i frontend og sjekke at veileder har tilgang til treffet, enn å legge til logikk i backend for å sjekke at veileder har tilgang til den enkelte kandidaten.
        // Foreløpig brukes den bare et sted som kun trenger arbeidsgiverrettet tilgang, så vi har bare droppet veiledertilgangen.
        innloggetBruker.verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.UTVIKLER)

        val personTreffId = PersonTreffId(ctx.pathParam(pathParamPersonTreffId))
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val innkommendeToken = innloggetBruker.innkommendeToken()
        val navIdent = innloggetBruker.extractNavIdent()

        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            jobbsøkerRepository.hentFødselsnummer(personTreffId)?.let { fødselsnummer ->
                kandidatsøkKlient.hentKandidatnummer(fødselsnummer, innkommendeToken)?.let { kandidatnummer ->
                    ctx.json(KandidatnummerDto(kandidatnummer.asString))
                } ?: ctx.status(HttpStatus.NOT_FOUND)
            } ?: ctx.status(HttpStatus.NOT_FOUND)
        } else {
            throw ForbiddenResponse("Personen eier ikke treffet og kan ikke hente kandidatnummer")
        }
    }
}