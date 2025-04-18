package no.nav.toi.jobbsoker

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
private const val jobbsøkerPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker"
private const val hendelserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker/hendelser"

private data class LeggTilJobbsøkerDto(
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?
) {
    fun somLeggTilJobbsøker() = LeggTilJobbsøker(
        Fødselsnummer(fødselsnummer),
        kandidatnummer?.let { Kandidatnummer(it) },
        Fornavn(fornavn),
        Etternavn(etternavn),
        navkontor?.let { Navkontor(it) },
        veilederNavn?.let { VeilederNavn(it) },
        veilederNavIdent?.let { VeilederNavIdent(it) }
    )
}

data class JobbsøkerHendelseOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?
)

data class JobbsøkerHendelseMedJobbsøkerDataOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?,
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String
)

data class JobbsøkerOutboundDto(
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val hendelser: List<JobbsøkerHendelseOutboundDto>
)

@OpenApi(
    summary = "Legg til ny jobbsøker til et rekrutteringstreff",
    operationId = "leggTilJobbsøker",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(
        name = pathParamTreffId,
        type = UUID::class,
        required = true,
        description = "Rekrutteringstreffets unike identifikator (UUID)"
    )],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = LeggTilJobbsøkerDto::class,
            example = """{
                "fødselsnummer": "12345678901",
                "kandidatnummer": "K123456",
                "fornavn": "Ola",
                "etternavn": "Nordmann",
                "navkontor": "NAV Oslo",
                "veilederNavn": "Kari Nordmann",
                "veilederNavIdent": "NAV123"
            }"""
        )]
    ),
    responses = [OpenApiResponse(status = "201")],
    path = jobbsøkerPath,
    methods = [HttpMethod.POST]
)
private fun leggTilJobbsøkerHandler(repo: JobbsøkerRepository): (Context) -> Unit = { ctx ->
    val dto: LeggTilJobbsøkerDto = ctx.bodyAsClass()
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    repo.leggTil(dto.somLeggTilJobbsøker(), treff, ctx.extractNavIdent())
    ctx.status(201)
}

@OpenApi(
    summary = "Hent alle jobbsøkere for et rekrutteringstreff",
    operationId = "hentJobbsøkere",
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
            from = Array<JobbsøkerOutboundDto>::class,
            example = """[
                {
                    "fødselsnummer": "12345678901",
                    "kandidatnummer": "K123456",
                    "fornavn": "Ola",
                    "etternavn": "Nordmann",
                    "navkontor": "Oslo",
                    "veilederNavn": "Kari Nordmann",
                    "veilederNavIdent": "NAV123",
                    "hendelser": [
                        {
                            "id": "any-uuid",
                            "tidspunkt": "2025-04-14T10:38:41Z",
                            "hendelsestype": "LAGT_TIL",
                            "opprettetAvAktørType": "ARRANGØR",
                            "aktørIdentifikasjon": "testperson"
                        }
                    ]
                },
                {
                    "fødselsnummer": "10987654321",
                    "kandidatnummer": "K543210",
                    "fornavn": "Kari",
                    "etternavn": "Nordmann",
                    "navkontor": null,
                    "veilederNavn": null,
                    "veilederNavIdent": null,
                    "hendelser": []
                }
            ]"""
        )]
    )],
    path = jobbsøkerPath,
    methods = [HttpMethod.GET]
)
private fun hentJobbsøkereHandler(repo: JobbsøkerRepository): (Context) -> Unit = { ctx ->
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    val jobbsøkere = repo.hentJobbsøkere(treff)
    ctx.status(200).json(jobbsøkere.toOutboundDto())
}

@OpenApi(
    summary = "Hent alle jobbsøkerhendelser med jobbsøkerdata for et rekrutteringstreff, sortert med nyeste først",
    operationId = "hentJobbsøkerHendelserMedData",
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
            from = Array<JobbsøkerHendelseMedJobbsøkerDataOutboundDto>::class,
            example = """[
                {
                    "id": "any-uuid",
                    "tidspunkt": "2025-04-14T10:38:41Z",
                    "hendelsestype": "LAGT_TIL",
                    "opprettetAvAktørType": "ARRANGØR",
                    "aktørIdentifikasjon": "testperson",
                    "fødselsnummer": "12345678901",
                    "kandidatnummer": "K123456",
                    "fornavn": "Ola",
                    "etternavn": "Nordmann"
                }
            ]"""
        )]
    )],
    path = hendelserPath,
    methods = [HttpMethod.GET]
)
private fun hentJobbsøkerHendelserHandler(repo: JobbsøkerRepository): (Context) -> Unit = { ctx ->
    val treff = TreffId(ctx.pathParam(pathParamTreffId))
    val hendelser = repo.hentJobbsøkerHendelser(treff)
    ctx.status(200).json(hendelser.map { h ->
        JobbsøkerHendelseMedJobbsøkerDataOutboundDto(
            id = h.id.toString(),
            tidspunkt = h.tidspunkt,
            hendelsestype = h.hendelsestype.toString(),
            opprettetAvAktørType = h.opprettetAvAktørType.toString(),
            aktørIdentifikasjon = h.aktørIdentifikasjon,
            fødselsnummer = h.fødselsnummer.asString,
            kandidatnummer = h.kandidatnummer?.asString,
            fornavn = h.fornavn.asString,
            etternavn = h.etternavn.asString
        )
    })
}

private fun List<Jobbsøker>.toOutboundDto(): List<JobbsøkerOutboundDto> =
    map {
        JobbsøkerOutboundDto(
            fødselsnummer = it.fødselsnummer.asString,
            kandidatnummer = it.kandidatnummer?.asString,
            fornavn = it.fornavn.asString,
            etternavn = it.etternavn.asString,
            navkontor = it.navkontor?.asString,
            veilederNavn = it.veilederNavn?.asString,
            veilederNavIdent = it.veilederNavIdent?.asString,
            hendelser = it.hendelser.map { h ->
                JobbsøkerHendelseOutboundDto(
                    id = h.id.toString(),
                    tidspunkt = h.tidspunkt,
                    hendelsestype = h.hendelsestype.toString(),
                    opprettetAvAktørType = h.opprettetAvAktørType.toString(),
                    aktørIdentifikasjon = h.aktørIdentifikasjon
                )
            }
        )
    }

fun Javalin.handleJobbsøker(repo: JobbsøkerRepository) {
    post(jobbsøkerPath, leggTilJobbsøkerHandler(repo))
    get(jobbsøkerPath, hentJobbsøkereHandler(repo))
    get(hendelserPath, hentJobbsøkerHendelserHandler(repo))
}
