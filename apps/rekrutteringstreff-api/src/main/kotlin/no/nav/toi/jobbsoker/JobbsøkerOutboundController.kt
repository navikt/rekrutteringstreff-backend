package no.nav.toi.jobbsoker

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.InternalServerErrorResponse
import io.javalin.openapi.*
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.eier.EierRepository
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
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
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
        )],
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
        val userToken = ctx.attribute<String>("raw_token")
            ?: throw InternalServerErrorResponse("Raw token ikke funnet i context")

        // TODO er persontreffId det samme som uuiden til rekrutteringstreffet?
        //val eiere = eierRepository.hent()

        jobbsøkerRepository.hentFødselsnummer(personTreffId)?.let { fødselsnummer ->
            kandidatsøkKlient.hentKandidatnummer(fødselsnummer, userToken)?.let { kandidatnummer ->
                ctx.json(KandidatnummerDto(kandidatnummer.asString))
            } ?: ctx.status(HttpStatus.NOT_FOUND)
        } ?: ctx.status(HttpStatus.NOT_FOUND)
    }
}