package no.nav.toi.rekrutteringstreff.sok

import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
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
            OpenApiParam(name = "visning", type = Visning::class, required = false),
            OpenApiParam(name = "statuser", type = Visningsstatus::class, required = false, description = "Kommaseparert liste av visningsstatuser"),
            OpenApiParam(name = "kontorer", type = String::class, required = false, description = "Kommaseparert liste av enhetId-er"),
            OpenApiParam(name = "sortering", type = String::class, required = false, description = "Sorteringsrekkefølge: sist_oppdaterte, nyeste, eldste"),
            OpenApiParam(name = "side", type = Int::class, required = false),
            OpenApiParam(name = "antallPerSide", type = Int::class, required = false),
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
                            "visningsstatus": "publisert",
                            "fraTid": "2026-04-15T09:00:00Z",
                            "tilTid": "2026-04-15T12:00:00Z",
                            "svarfrist": "2026-04-10T23:59:59Z",
                            "gateadresse": "Storgata 1",
                            "postnummer": "0182",
                            "poststed": "Oslo",
                            "kommune": "Oslo",
                            "fylke": "Oslo",
                            "opprettetAvTidspunkt": "2026-03-01T10:00:00Z",
                            "sistEndret": "2026-03-10T14:30:00Z",
                            "eiere": ["A123456"],
                            "kontorer": ["0315"]
                        },
                        {
                            "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                            "tittel": "Jobbmesse for helsesektoren",
                            "beskrivelse": null,
                            "visningsstatus": "utkast",
                            "fraTid": null,
                            "tilTid": null,
                            "svarfrist": null,
                            "gateadresse": null,
                            "postnummer": null,
                            "poststed": null,
                            "kommune": null,
                            "fylke": null,
                            "opprettetAvTidspunkt": "2026-03-05T08:00:00Z",
                            "sistEndret": "2026-03-05T08:00:00Z",
                            "eiere": ["B654321"],
                            "kontorer": ["1201"]
                        }
                    ],
                    "antallTotalt": 42,
                    "side": 1,
                    "antallPerSide": 20,
                    "statusaggregering": [
                        {"verdi": "publisert", "antall": 20},
                        {"verdi": "utkast", "antall": 12},
                        {"verdi": "soknadsfrist_passert", "antall": 5},
                        {"verdi": "fullfort", "antall": 3},
                        {"verdi": "avlyst", "antall": 2}
                    ]
                }"""
            )]
        ),
        OpenApiResponse(status = "400", description = "Ugyldig visning, status eller sortering"),
        OpenApiResponse(status = "401", description = "Manglende eller ugyldig token")],
        path = sokPath,
        methods = [HttpMethod.GET]
    )
    private fun sokHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)

        val visning = ctx.queryParam("visning")?.let {
            try { Visning.fraJsonVerdi(it) } catch (_: IllegalArgumentException) {
                throw BadRequestResponse("Ugyldig visning: $it")
            }
        } ?: Visning.ALLE
        val sortering = ctx.queryParam("sortering")?.let {
            try { Sortering.fraJsonVerdi(it) } catch (_: IllegalArgumentException) {
                throw BadRequestResponse("Ugyldig sortering: $it")
            }
        } ?: Sortering.SIST_OPPDATERTE

        val statuser = ctx.queryParam("statuser")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.map {
            try { Visningsstatus.fraJsonVerdi(it) } catch (_: IllegalArgumentException) {
                throw BadRequestResponse("Ugyldig visningsstatus: $it")
            }
        }
        val kontorer = ctx.csvQueryParam("kontorer")

        val side = ctx.queryParamAsInt("side") ?: 1
        val antallPerSide = ctx.queryParamAsInt("antallPerSide") ?: 20
        if (side < 1) {
            throw BadRequestResponse("side må være 1 eller høyere")
        }
        if (antallPerSide <= 0) {
            throw BadRequestResponse("antallPerSide må være større enn 0")
        }

        val request = RekrutteringstreffSokRequest(
            statuser = statuser,
            kontorer = if (visning == Visning.MITT_KONTOR) null else kontorer,
            visning = visning,
            sortering = sortering,
            side = side,
            antallPerSide = antallPerSide,
        )

        val navIdent = ctx.extractNavIdent()
        val kontorId = ctx.authenticatedUser().extractKontorId()
        if (visning == Visning.MITT_KONTOR && kontorId.isNullOrEmpty()) {
            throw BadRequestResponse("Veileders kontor er ikke tilgjengelig")
        }

        ctx.status(200).json(sokService.sok(request, navIdent, kontorId))
    }
}

private fun Context.csvQueryParam(name: String): List<String>? =
    queryParam(name)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

private fun Context.queryParamAsInt(name: String): Int? =
    queryParam(name)?.let { value ->
        value.toIntOrNull() ?: throw BadRequestResponse("Ugyldig $name: $value")
    }
