package no.nav.toi.statistikk

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.openapi.*
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.Rolle
import no.nav.toi.RuteRegistrerer
import no.nav.toi.authenticatedUser
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeParseException

class StatistikkController(
    private val statistikkService: StatistikkService,
) : RuteRegistrerer {
    companion object {
        private const val fåttJobbPath = "/api/rekrutteringstreff/statistikk/fatt-jobben"
        private const val queryParamNavKontor = "navKontor"
        private const val queryParamFraOgMed = "fraOgMed"
        private const val queryParamTilOgMed = "tilOgMed"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun registrer(routes: JavalinDefaultRoutingApi) {
        routes.get(fåttJobbPath, hentFåttJobbStatistikkHandler())
    }

    @OpenApi(
        summary = "Hent antall som har fått jobb via rekrutteringstreff for et Nav-kontor i en periode",
        operationId = "hentFåttJobbStatistikk",
        tags = ["statistikk"],
        security = [OpenApiSecurity(name = "BearerAuth")],
        queryParams = [
            OpenApiParam(name = queryParamNavKontor, type = String::class, required = true, description = "Nav-kontorets enhetsnummer", example = "0318"),
            OpenApiParam(name = queryParamFraOgMed, type = String::class, required = true, description = "Fra og med-dato (ISO-8601, f.eks. 2026-06-01)", example = "2026-06-01"),
            OpenApiParam(name = queryParamTilOgMed, type = String::class, required = true, description = "Til og med-dato (ISO-8601, f.eks. 2026-06-30)", example = "2026-06-30"),
        ],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = FåttJobbStatistikk::class, example = """{"totalt": 12, "under30år": 4, "innsatsgruppeIkkeStandard": 3}""")]
        )],
        path = fåttJobbPath,
        methods = [HttpMethod.GET]
    )
    private fun hentFåttJobbStatistikkHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)

        val navKontor = ctx.queryParam(queryParamNavKontor)
            ?.takeIf { it.isNotBlank() }
            ?: throw BadRequestResponse("Mangler påkrevd parameter '$queryParamNavKontor'")
        val fraOgMed = ctx.queryParam(queryParamFraOgMed).parseDato(queryParamFraOgMed)
        val tilOgMed = ctx.queryParam(queryParamTilOgMed).parseDato(queryParamTilOgMed)

        if (tilOgMed.isBefore(fraOgMed)) {
            throw BadRequestResponse("'$queryParamTilOgMed' kan ikke være før '$queryParamFraOgMed'")
        }

        logger.info("Henter fått-jobb-statistikk for kontor $navKontor i perioden $fraOgMed - $tilOgMed")
        ctx.json(statistikkService.hentFåttJobbStatistikk(navKontor, fraOgMed, tilOgMed))
    }

    private fun String?.parseDato(parameterNavn: String): LocalDate {
        val verdi = this?.takeIf { it.isNotBlank() }
            ?: throw BadRequestResponse("Mangler påkrevd parameter '$parameterNavn'")
        return try {
            LocalDate.parse(verdi)
        } catch (e: DateTimeParseException) {
            throw BadRequestResponse("Ugyldig dato for '$parameterNavn': $verdi")
        }
    }
}
