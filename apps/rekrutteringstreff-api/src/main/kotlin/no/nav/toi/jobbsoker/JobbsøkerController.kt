package no.nav.toi.jobbsoker

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.jobbsoker.dto.JobbsøkerDto
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerDataOutboundDto
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseOutboundDto
import no.nav.toi.jobbsoker.dto.JobbsøkerOutboundDto
import no.nav.toi.jobbsoker.dto.PersonTreffIderDto
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.ZonedDateTime
import java.util.*

private const val pathParamTreffId = "id"

private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

const val jobbsøkerPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker"
private const val hendelserPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker/hendelser"
private const val inviterPath = "$jobbsøkerPath/inviter"

class JobbsøkerController(
    private val jobbsøkerRepository: JobbsøkerRepository,
    javalin: Javalin
) {
    init {
        javalin.post(jobbsøkerPath, leggTilJobbsøkereHandler())
        javalin.get(jobbsøkerPath, hentJobbsøkereHandler())
        javalin.get(hendelserPath, hentJobbsøkerHendelserHandler())
        javalin.post(inviterPath, inviterJobbsøkereHandler())
    }

    @OpenApi(
        summary = "Legg til flere jobbsøkere",
        operationId = "leggTilJobbsøkere",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(
            // ⬇️  merk isArray = true
            content = [OpenApiContent(
                from = Array<JobbsøkerDto>::class,
                example = """[
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
    private fun leggTilJobbsøkereHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val dtoer = ctx.bodyAsClass<Array<JobbsøkerDto>>()
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        jobbsøkerRepository.leggTil(dtoer.map { it.domene() }, treff, ctx.extractNavIdent())
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
                    "personTreffId": "any-uuid",
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
                            "hendelsestype": "OPPRETTET",
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
    private fun hentJobbsøkereHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val jobbsøkere = jobbsøkerRepository.hentJobbsøkere(treff)
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
                    "hendelsestype": "OPPRETTET",
                    "opprettetAvAktørType": "ARRANGØR",
                    "aktørIdentifikasjon": "testperson",
                    "fødselsnummer": "12345678901",
                    "kandidatnummer": "K123456",
                    "fornavn": "Ola",
                    "etternavn": "Nordmann",
                    "personTreffId": "any-uuid"
                }
            ]"""
            )]
        )],
        path = hendelserPath,
        methods = [HttpMethod.GET]
    )
    private fun hentJobbsøkerHendelserHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val hendelser = jobbsøkerRepository.hentJobbsøkerHendelser(treff)
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
                etternavn = h.etternavn.asString,
                personTreffId = h.personTreffId.somString
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
                from = PersonTreffIderDto::class,
                example = """{ "personTreffIder": ["2d4dcf50-2418-4085-9c5f-1390bc49a97f", "0aff1e80-cc11-4cdc-a495-ada1f0a8b3dd"] }"""
            )]
        ),
        responses = [OpenApiResponse("200", description = "Invitasjonshendelser er lagt til.")],
        path = inviterPath,
        methods = [HttpMethod.POST]
    )
    private fun inviterJobbsøkereHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val dto = ctx.bodyAsClass<PersonTreffIderDto>()
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val personTreffIder = dto.personTreffIder
        val navIdent = ctx.extractNavIdent()

        jobbsøkerRepository.inviter(personTreffIder, treffId, navIdent)
        ctx.status(200)
    }

    private fun List<Jobbsøker>.toOutboundDto(): List<JobbsøkerOutboundDto> =
        map {
            JobbsøkerOutboundDto(
                personTreffId = it.personTreffId.toString(),
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
}