package no.nav.toi.jobbsoker

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.HttpStatus
import io.javalin.http.InternalServerErrorResponse
import io.javalin.openapi.*
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import java.lang.IllegalStateException
import java.util.*

data class KandidatnummerDto(val kandidatnummer: String)

class JobbsøkerOutboundController(
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val kandidatsøkKlient: KandidatsøkKlient,
    private val eierRepository: EierRepository,
    javalin: Javalin
) {
    companion object {
        private const val pathParamPersonTreffId = "personTreffId"
        private const val pathParamTreffId = "treffId"
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff/{$pathParamTreffId}"
        private const val eksternKandidatnummerPath = "$endepunktRekrutteringstreff/jobbsoker/{$pathParamPersonTreffId}/kandidatnummer"
    }
    init {
        javalin.get(eksternKandidatnummerPath, hentKandidatnummerHandler())
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
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.UTVIKLER)

        val personTreffId = PersonTreffId(ctx.pathParam(pathParamPersonTreffId))
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val userToken = ctx.attribute<String>("raw_token")
            ?: throw InternalServerErrorResponse("Raw token ikke funnet i context")
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        val eiere = eierRepository.hent(treffId)?.tilNavIdenter() ?: throw IllegalStateException("Kan ikke hente kandidatnummer siden person ikke er eier av treffet")

        if (eiere.contains(navIdent) || ctx.authenticatedUser().erUtvikler()) {
            jobbsøkerRepository.hentFødselsnummer(personTreffId)?.let { fødselsnummer ->
                kandidatsøkKlient.hentKandidatnummer(fødselsnummer, userToken)?.let { kandidatnummer ->
                    ctx.json(KandidatnummerDto(kandidatnummer.asString))
                } ?: ctx.status(HttpStatus.NOT_FOUND)
            } ?: ctx.status(HttpStatus.NOT_FOUND)
        } else {
            throw ForbiddenResponse("Personen eier ikke treffet og kan ikke hente kandidatnummer")
        }
    }
}