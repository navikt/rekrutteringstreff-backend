package no.nav.toi.minside.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import no.nav.toi.minside.arbeidsgiver.ArbeidsgiverOutboundDto
import no.nav.toi.minside.authenticatedUser
import no.nav.toi.minside.innlegg.InnleggOutboundDto
import no.nav.toi.minside.svar.BorgerKlient.Companion.log
import java.time.ZonedDateTime
import java.util.UUID

const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
private const val pathParamTreffId = "id"
private const val hentRekrutteringsTreff = "$endepunktRekrutteringstreff/{$pathParamTreffId}"

@OpenApi(
    summary = "Hent ett rekrutteringstreff",
    operationId = "hentRekrutteringstreff",
    security = [OpenApiSecurity("BearerAuth")],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = RekrutteringstreffOutboundDto::class,
            example = """{
               "id":"d6a587cd-8797-4b9a-a68b-575373f16d65",
               "tittel":"Sommerjobbtreff",
               "beskrivelse":null,
               "fraTid":null,
               "tilTid":null,
               "svarfrist":null,
               "gateadresse":null,
               "postnummer":null,
               "poststed":null,
               "status":null
            }"""
        )]
    )],
    path = hentRekrutteringsTreff,
    methods = [HttpMethod.GET]
)
private fun hentRekrutteringstreffHandler(treffKlient: RekrutteringstreffKlient): (Context) -> Unit = { ctx ->
    val id = ctx.pathParam(pathParamTreffId)
    val treff = treffKlient.hent(id, ctx.authenticatedUser().jwt)?.tilDTOForBruker()
    if (treff == null) {
        log.info("Fant ikke treff med id: $id")
        ctx.status(404)
    } else {
        val arbeidsgivere = treffKlient.hentArbeidsgivere(id, ctx.authenticatedUser().jwt)?.map { it.tilDTOForBruker() } ?: emptyList()
        val innlegg = treffKlient.hentInnlegg(id, ctx.authenticatedUser().jwt)?.map { it.tilDTOForBruker() } ?: emptyList()
        log.info("Hentet rekrutteringstrefff med id: $id")
        ctx.status(200).json(treff.copy(arbeidsgivere = arbeidsgivere, innlegg = innlegg).json())
    }
}

fun Javalin.rekrutteringstreffendepunkt(treffKlient: RekrutteringstreffKlient) = get(hentRekrutteringsTreff, hentRekrutteringstreffHandler(treffKlient))

data class RekrutteringstreffOutboundDto(
    private val id: UUID,
    private val tittel: String,
    private val beskrivelse: String?,
    private val fraTid: ZonedDateTime?,
    private val tilTid: ZonedDateTime?,
    private val svarfrist: ZonedDateTime?,
    private val gateadresse: String?,
    private val postnummer: String?,
    private val poststed: String?,
    private val status: String?,
    private val innlegg: List<InnleggOutboundDto> = emptyList(),
    private val arbeidsgivere: List<ArbeidsgiverOutboundDto> = emptyList(),
) {
    fun json() = """
        {
            "id": "$id",
            "tittel": "$tittel",
            "beskrivelse": ${beskrivelse?.let { "\"$it\"" } },
            "fraTid": ${fraTid?.let { "\"$it\"" } },
            "tilTid": ${tilTid?.let { "\"$it\"" } },
            "svarfrist": ${svarfrist?.let { "\"$it\"" } },
            "gateadresse": ${gateadresse?.let { "\"$it\"" } },
            "postnummer": ${postnummer?.let { "\"$it\"" } },
            "poststed": ${poststed?.let { "\"$it\"" } },
            "status": ${status?.let { "\"$it\"" } },
            "innlegg": [ ${innlegg.map { it.json() }.joinToString(",\n")} ],
            "arbeidsgivere": [ ${arbeidsgivere.map { it.json() }.joinToString(",\n")} ]
        }
    """.trimIndent()
}
