package no.nav.toi.arbeidsgiver

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.time.ZonedDateTime
import java.util.*

private const val pathParamTreffId = "id"
private const val arbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver"
private const val hendelserArbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver/hendelser"

private data class LeggTilArbeidsgiverDto(
    val organisasjonsnummer: String,
    val navn: String
) {
    fun somLeggTilArbeidsgiver() = LeggTilArbeidsgiver(Orgnr(organisasjonsnummer), Orgnavn(navn))
}

data class ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktøridentifikasjon: String?,
    val orgnr: String,
    val orgnavn: String
)

data class ArbeidsgiverHendelseOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktøridentifikasjon: String?,
)


data class ArbeidsgiverOutboundDto(
    val organisasjonsnummer: String,
    val navn: String,
    val hendelser: List<ArbeidsgiverHendelseOutboundDto>
)

@OpenApi(
    summary = "Legg til ny arbeidsgiver til et rekrutteringstreff",
    operationId = "leggTilArbeidsgiver",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(
        name = pathParamTreffId,
        type = UUID::class,
        required = true,
        description = "Rekrutteringstreffets unike identifikator (UUID)"
    )],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = LeggTilArbeidsgiverDto::class,
            example = """{"organisasjonsnummer": "123456789", "navn": "Example Company"}"""
        )]
    ),
    responses = [OpenApiResponse(status = "201")],
    path = arbeidsgiverPath,
    methods = [HttpMethod.POST]
)
private fun leggTilArbeidsgiverHandler(repo: ArbeidsgiverRepository): (Context) -> Unit = { ctx ->
    val dto: LeggTilArbeidsgiverDto = ctx.bodyAsClass()
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    repo.leggTil(dto.somLeggTilArbeidsgiver(), treff, ctx.extractNavIdent())
    ctx.status(201)
}

@OpenApi(
    summary = "Hent alle arbeidsgivere for et rekrutteringstreff",
    operationId = "hentArbeidsgivere",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(
        name = pathParamTreffId,
        type = UUID::class,
        required = true,
        description = "Rekrutteringstreffets unike identifikator (UUID)"
    )],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = Array<ArbeidsgiverOutboundDto>::class,
            example = """[
                {
                    "organisasjonsnummer": "123456789",
                    "navn": "Example Company",
                    "hendelser": [
                        {
                            "id": "any-uuid",
                            "tidspunkt": "2025-04-14T10:38:41Z",
                            "hendelsestype": "LEGG_TIL",
                            "opprettetAvAktørType": "ARRANGØR",
                            "aktøridentifikasjon": "testperson",
                        }
                    ]
                },
                {
                    "organisasjonsnummer": "987654321",
                    "navn": "Another Company",
                    "hendelser": []
                }
            ]"""
        )]
    )],
    path = arbeidsgiverPath,
    methods = [HttpMethod.GET]
)
private fun hentArbeidsgivereHandler(repo: ArbeidsgiverRepository): (Context) -> Unit = { ctx ->
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    val arbeidsgivere = repo.hentArbeidsgivere(treff)
    ctx.status(200).json(arbeidsgivere.toOutboundDto())
}

private fun List<Arbeidsgiver>.toOutboundDto(): List<ArbeidsgiverOutboundDto> =
    map { arbeidsgiver ->
        ArbeidsgiverOutboundDto(
            organisasjonsnummer = arbeidsgiver.orgnr.asString,
            navn = arbeidsgiver.orgnavn.asString,
            hendelser = arbeidsgiver.hendelser.map { h ->
                ArbeidsgiverHendelseOutboundDto(
                    id = h.id.toString(),
                    tidspunkt = h.tidspunkt,
                    hendelsestype = h.hendelsestype.toString(),
                    opprettetAvAktørType = h.opprettetAvAktørType.toString(),
                    aktøridentifikasjon = h.aktøridentifikasjon,
                )
            }
        )
    }

@OpenApi(
    summary = "Hent alle arbeidsgiverhendelser med tilhørende data for et rekrutteringstreff, sortert med nyeste først",
    operationId = "hentArbeidsgiverHendelserMedData",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(
        name = pathParamTreffId,
        type = UUID::class,
        required = true,
        description = "Rekrutteringstreffets unike identifikator (UUID)"
    )],
    responses = [OpenApiResponse(
        status = "200",
        content = [OpenApiContent(
            from = Array<ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto>::class,
            example = """[
                {
                    "id": "any-uuid",
                    "tidspunkt": "2025-04-14T10:38:41Z",
                    "hendelsestype": "LEGG_TIL",
                    "opprettetAvAktørType": "ARRANGØR",
                    "aktøridentifikasjon": "testperson",
                    "orgnr": "123456789",
                    "orgnavn": "Example Company"
                }
            ]"""
        )]
    )],
    path = hendelserArbeidsgiverPath,
    methods = [HttpMethod.GET]
)
private fun hentArbeidsgiverHendelserHandler(repo: ArbeidsgiverRepository): (Context) -> Unit = { ctx ->
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    val hendelser = repo.hentArbeidsgiverHendelser(treff)
    ctx.status(200).json(hendelser.map { h ->
        ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto(
            id = h.id.toString(),
            tidspunkt = h.tidspunkt,
            hendelsestype = h.hendelsestype.toString(),
            opprettetAvAktørType = h.opprettetAvAktørType.toString(),
            aktøridentifikasjon = h.aktøridentifikasjon,
            orgnr = h.orgnr.asString,
            orgnavn = h.orgnavn.asString
        )
    })
}

fun Javalin.handleArbeidsgiver(repo: ArbeidsgiverRepository) {
    post(arbeidsgiverPath, leggTilArbeidsgiverHandler(repo))
    get(arbeidsgiverPath, hentArbeidsgivereHandler(repo))
    get(hendelserArbeidsgiverPath, hentArbeidsgiverHendelserHandler(repo))
}
