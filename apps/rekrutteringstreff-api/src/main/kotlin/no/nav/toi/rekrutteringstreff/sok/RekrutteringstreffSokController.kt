package no.nav.toi.rekrutteringstreff.sok

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser

class RekrutteringstreffSokController(
    private val sokService: RekrutteringstreffSokService,
    javalin: Javalin,
) {
    companion object {
        private const val sokPath = "/api/rekrutteringstreff/sok"
    }

    init {
        javalin.get(sokPath, sokHandler())
    }

    @OpenApi(
        summary = "Søk etter rekrutteringstreff",
        operationId = "sokRekrutteringstreff",
        security = [OpenApiSecurity(name = "BearerAuth")],
        queryParams = [
            OpenApiParam(name = "visning", type = Visning::class, required = false, description = "Filter for hvilke treff som skal vises", example = "alle"),
            OpenApiParam(name = "statuser", type = String::class, required = false, description = "Kommaseparert liste av statuser, for eksempel publisert,utkast", example = "PUBLISERT,UTKAST"),
            OpenApiParam(name = "publisertStatuser", type = String::class, required = false, description = "Kommaseparert liste av publisert statuser", example = "ÅPEN_FOR_SØKERE,SØKNADSFRIST_PASSERT"),
            OpenApiParam(name = "kontorer", type = String::class, required = false, description = "Kommaseparert liste av enhetId-er, for eksempel 0315,1201", example = "0315,1201"),
            OpenApiParam(name = "sortering", type = Sortering::class, required = false, description = "Sorteringsrekkefølge for trefflisten", example = "sist_oppdaterte"),
            OpenApiParam(name = "side", type = Int::class, required = false, description = "Sidetall, starter på 1", example = "1"),
            OpenApiParam(name = "antallPerSide", type = Int::class, required = false, description = "Antall treff per side, må være mellom 1 og 100", example = "20"),
        ],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = RekrutteringstreffSokRespons::class,
                example = """{
                    "treff": [
                        {
                            "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                            "tittel": "Rekrutteringstreff – bygg og anlegg",
                            "beskrivelse": "Treff for arbeidsgivere og jobbsøkere innen bygg og anlegg",
                            "status": "PUBLISERT",
                            "fraTid": "2026-04-15T09:00:00Z",
                            "tilTid": "2026-04-15T12:00:00Z",
                            "svarfrist": "2026-04-10T23:59:59Z",
                            "gateadresse": "Storgata 1",
                            "postnummer": "0182",
                            "poststed": "Oslo",
                            "opprettetAv": "A123456",
                            "opprettetAvTidspunkt": "2026-03-01T10:00:00Z",
                            "sistEndret": "2026-03-10T14:30:00Z",
                            "eiere": ["A123456"],
                            "kontorer": ["0315"],
                            "antallArbeidsgivere": 3,
                            "antallJobbsokere": 12
                        },
                        {
                            "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                            "tittel": "Jobbmesse for helsesektoren",
                            "beskrivelse": null,
                            "status": "UTKAST",
                            "fraTid": null,
                            "tilTid": null,
                            "svarfrist": null,
                            "gateadresse": null,
                            "postnummer": null,
                            "poststed": null,
                            "opprettetAv": "B654321",
                            "opprettetAvTidspunkt": "2026-03-05T08:00:00Z",
                            "sistEndret": "2026-03-05T08:00:00Z",
                            "eiere": ["B654321"],
                            "kontorer": ["1201"],
                            "antallArbeidsgivere": 0,
                            "antallJobbsokere": 0
                        }
                    ],
                    "antallTotalt": 42,
                    "side": 1,
                    "antallPerSide": 20,
                    "statusaggregering": [
                        {"verdi": "PUBLISERT", "antall": 12},
                        {"verdi": "UTKAST", "antall": 12},
                        {"verdi": "FULLFØRT", "antall": 3},
                        {"verdi": "AVLYST", "antall": 2}
                    ]
                }"""
            )]
        ),
        OpenApiResponse(status = "400", description = "Ugyldig parameter (visning, status, sortering, side, antallPerSide m.fl.)"),
        OpenApiResponse(status = "401", description = "Manglende eller ugyldig token")],
        path = sokPath,
        methods = [HttpMethod.GET]
    )
    private fun sokHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)

        val visning = ctx.queryParam("visning")?.let {
            try { Visning.fraJsonVerdi(it) } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Ugyldig visning: $it")
            }
        } ?: Visning.ALLE
        val sortering = ctx.queryParam("sortering")?.let {
            try { Sortering.fraJsonVerdi(it) } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Ugyldig sortering: $it")
            }
        } ?: Sortering.SIST_OPPDATERTE

        val statuser = ctx.queryParam("statuser")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.map {
            try { SokStatus.fraJsonVerdi(it) } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Ugyldig status: $it")
            }
        }
        val publisertStatuser =  ctx.queryParam("publisertStatuser")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.map {
            try {
                PublisertStatus.fraJsonVerdi(it) } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Ugyldig status: $it")
            }
        }
        val publisertFristUtgatt = ctx.queryParam("publisertFristUtgatt")?.let {
            it.toBooleanStrictOrNull() ?: throw IllegalArgumentException("Ugyldig publisertFristUtgatt: $it")
        }
        val kontorer = ctx.csvQueryParam("kontorer")

        val side = ctx.queryParamAsInt("side") ?: 1
        val antallPerSide = ctx.queryParamAsInt("antallPerSide") ?: 20
        if (side < 1) {
            throw IllegalArgumentException("side må være 1 eller høyere")
        }
        if (antallPerSide !in 1..100) {
            throw IllegalArgumentException("antallPerSide må være mellom 1 og 100")
        }

        val request = RekrutteringstreffSokRequest(
            statuser = statuser,
            publisertStatuser = publisertStatuser,
            publisertFristUtgatt = publisertFristUtgatt,
            kontorer = if (visning == Visning.MITT_KONTOR) null else kontorer,
            visning = visning,
            sortering = sortering,
            side = side,
            antallPerSide = antallPerSide,
        )

        val navIdent = ctx.extractNavIdent()
        val kontorId = if (visning == Visning.MITT_KONTOR) {
            ctx.authenticatedUser().extractKontorId().also {
                if (it.isNullOrEmpty()) {
                    throw IllegalArgumentException("Veileders kontor er ikke tilgjengelig")
                }
            }
        } else {
            null
        }

        ctx.status(200).json(sokService.sok(request, navIdent, kontorId))
    }
}

private fun Context.csvQueryParam(name: String): List<String>? =
    queryParam(name)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

private fun Context.queryParamAsInt(name: String): Int? =
    queryParam(name)?.let { value ->
        value.toIntOrNull() ?: throw IllegalArgumentException("Ugyldig $name: $value")
    }
