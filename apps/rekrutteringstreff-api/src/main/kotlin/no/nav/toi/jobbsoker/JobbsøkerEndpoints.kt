package no.nav.toi.jobbsoker

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.time.ZonedDateTime
import java.util.*

private const val pathParamTreffId = "id"
private const val jobbsøkerPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker"
private const val hendelserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker/hendelser"
private const val inviterPath = "$jobbsøkerPath/inviter"
private const val svarJaPath = "$jobbsøkerPath/svar-ja"


data class JobbsøkerDto(
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?
) {
    fun domene() = LeggTilJobbsøker(
        Fødselsnummer(fødselsnummer),
        kandidatnummer?.let(::Kandidatnummer),
        Fornavn(fornavn),
        Etternavn(etternavn),
        navkontor?.let(::Navkontor),
        veilederNavn?.let(::VeilederNavn),
        veilederNavIdent?.let(::VeilederNavIdent)
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

data class InviterJobbsøkereDto(
    val fødselsnumre: List<String>
)

data class SvarJaDto(
    val fødselsnummer: String
)

@OpenApi(
    summary = "Legg til flere jobbsøkere",
    operationId = "leggTilJobbsøkere",
    security = [OpenApiSecurity("BearerAuth")],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
    requestBody = OpenApiRequestBody(
        // ⬇️  merk isArray = true
        content = [OpenApiContent(
            from = Array<JobbsøkerDto>::class,
            example  = """[
              {
                "fødselsnummer": "12345678901",
                "kandidatnummer": "K123456",
                "fornavn": "Ola",
                "etternavn": "Nordmann",
                "navkontor": "NAV Oslo",
                "veilederNavn": "Kari Nordmann",
                "veilederNavIdent": "NAV123"
              },
              {
                "fødselsnummer": "10987654321",
                "kandidatnummer": null,
                "fornavn": "Kari",
                "etternavn": "Nordmann",
                "navkontor": null,
                "veilederNavn": null,
                "veilederNavIdent": null
              }
            ]"""
        )]
    ),
    responses = [OpenApiResponse("201")],
    path = jobbsøkerPath,
    methods = [HttpMethod.POST]
)
private fun leggTilJobbsøkereHandler(repo: JobbsøkerRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val dtoer = ctx.bodyAsClass<Array<JobbsøkerDto>>()   // leser array
    val treff  = TreffId(ctx.pathParam(pathParamTreffId))
    repo.leggTil(dtoer.map { it.domene() }, treff, ctx.extractNavIdent())
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
                            "hendelsestype": "OPPRETT",
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
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
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
                    "hendelsestype": "OPPRETT",
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
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
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

@OpenApi(
    summary = "Inviterer en eller flere jobbsøkere til rekrutteringstreffet.",
    operationId = "inviterJobbsøkere",
    security = [OpenApiSecurity("BearerAuth")],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = InviterJobbsøkereDto::class,
            example = """{ "fødselsnumre": ["12345678901", "10987654321"] }"""
        )]
    ),
    responses = [OpenApiResponse("200", description = "Invitasjonshendelser er lagt til.")],
    path = inviterPath,
    methods = [HttpMethod.POST]
)
private fun inviterJobbsøkereHandler(repo: JobbsøkerRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val dto = ctx.bodyAsClass<InviterJobbsøkereDto>()
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val fødselsnumre = dto.fødselsnumre.map(::Fødselsnummer)
    val navIdent = ctx.extractNavIdent()

    repo.inviter(fødselsnumre, treffId, navIdent)
    ctx.status(200)
}

@OpenApi(
    summary = "Registrerer at en jobbsøker har takket ja til invitasjon.",
    operationId = "svarJaTilInvitasjon",
    security = [OpenApiSecurity("BearerAuth")],
    pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
    requestBody = OpenApiRequestBody(
        content = [OpenApiContent(
            from = SvarJaDto::class,
            example = """{ "fødselsnummer": "12345678901" }"""
        )]
    ),
    responses = [OpenApiResponse("200", description = "Hendelse for 'svart ja' er lagt til.")],
    path = svarJaPath,
    methods = [HttpMethod.POST]
)
private fun svarJaHandler(repo: JobbsøkerRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon()
    val dto = ctx.bodyAsClass<SvarJaDto>()
    val treffId = TreffId(ctx.pathParam(pathParamTreffId))
    val fødselsnummer = Fødselsnummer(dto.fødselsnummer)

    repo.svarJaTilInvitasjon(fødselsnummer, treffId, fødselsnummer.asString)
    ctx.status(200)
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
    post(jobbsøkerPath, leggTilJobbsøkereHandler(repo))
    get(jobbsøkerPath, hentJobbsøkereHandler(repo))
    get(hendelserPath, hentJobbsøkerHendelserHandler(repo))
    post(inviterPath, inviterJobbsøkereHandler(repo))
    post(svarJaPath, svarJaHandler(repo))
}