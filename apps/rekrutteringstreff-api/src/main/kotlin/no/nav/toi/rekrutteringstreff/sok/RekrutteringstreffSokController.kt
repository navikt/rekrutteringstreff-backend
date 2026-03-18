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
            OpenApiParam(name = "visningsstatuser", type = Visningsstatus::class, required = false, description = "Kommaseparert liste av visningsstatuser"),
            OpenApiParam(name = "kontorer", type = String::class, required = false, description = "Kommaseparert liste av enhetId-er"),
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
                            "visningsstatus": "PUBLISERT",
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
                            "visningsstatus": "UTKAST",
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
                    "totaltAntall": 42,
                    "side": 0,
                    "antallPerSide": 25,
                    "statusaggregering": [
                        {"verdi": "PUBLISERT", "antall": 20},
                        {"verdi": "UTKAST", "antall": 12},
                        {"verdi": "SOKNADSFRIST_PASSERT", "antall": 5},
                        {"verdi": "FULLFORT", "antall": 3},
                        {"verdi": "AVLYST", "antall": 2}
                    ]
                }"""
            )]
        ),
        OpenApiResponse(status = "400", description = "Ugyldig visning eller visningsstatus"),
        OpenApiResponse(status = "401", description = "Manglende eller ugyldig token")],
        path = sokPath,
        methods = [HttpMethod.GET]
    )
    private fun sokHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)

        val visning = ctx.queryParamAsEnum<Visning>("visning") ?: Visning.ALLE

        val visningsstatuser = ctx.csvQueryParamAsEnum<Visningsstatus>("visningsstatuser")
        val kontorer = ctx.csvQueryParam("kontorer")

        val side = ctx.queryParamAsInt("side") ?: 0
        val antallPerSide = ctx.queryParamAsInt("antallPerSide") ?: 25
        if (side < 0) {
            throw BadRequestResponse("side må være 0 eller høyere")
        }
        if (antallPerSide <= 0) {
            throw BadRequestResponse("antallPerSide må være større enn 0")
        }

        val request = RekrutteringstreffSokRequest(
            visningsstatuser = visningsstatuser,
            kontorer = if (visning == Visning.MITT_KONTOR) null else kontorer,
            visning = visning,
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

private inline fun <reified T : Enum<T>> String.parseEnum(): T =
    try {
        enumValueOf<T>(this)
    } catch (_: IllegalArgumentException) {
        throw BadRequestResponse("Ugyldig ${T::class.simpleName}: $this")
    }

private inline fun <reified T : Enum<T>> Context.queryParamAsEnum(name: String): T? =
    queryParam(name)?.parseEnum<T>()

private inline fun <reified T : Enum<T>> Context.csvQueryParamAsEnum(name: String): List<T>? =
    queryParam(name)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.map { it.parseEnum<T>() }

private fun Context.csvQueryParam(name: String): List<String>? =
    queryParam(name)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

private fun Context.queryParamAsInt(name: String): Int? =
    queryParam(name)?.let { value ->
        value.toIntOrNull() ?: throw BadRequestResponse("Ugyldig $name: $value")
    }
