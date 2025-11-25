package no.nav.toi.jobbsoker

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
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
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


class JobbsøkerController(
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val eierRepository: EierRepository,
    javalin: Javalin
) {
    companion object {
        private const val pathParamTreffId = "id"
        private const val pathParamJobbsøkerId = "jobbsokerid"
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

        private const val jobbsøkerPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker"
        private const val hendelserPath = "$jobbsøkerPath/hendelser"
        private const val slettPath = "$jobbsøkerPath/{$pathParamJobbsøkerId}/slett"
        private const val inviterPath = "$jobbsøkerPath/inviter"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    init {
        javalin.post(jobbsøkerPath, leggTilJobbsøkereHandler())
        javalin.get(jobbsøkerPath, hentJobbsøkereHandler())
        javalin.delete(slettPath, slettJobbsøkerHandler())
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
    ) // TODO legg til jobbsøkerrettet
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
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        val eiere = eierRepository.hent(treff)?.tilNavIdenter() ?: throw IllegalStateException("Rekrutteringstreff med id $treff har ingen eiere")

        if(eiere.contains(navIdent) || ctx.authenticatedUser().erUtvikler()) {
            val jobbsøkere = jobbsøkerRepository.hentJobbsøkere(treff)
            ctx.status(200).json(jobbsøkere.toOutboundDto())
        } else {
            throw ForbiddenResponse("Personen er ikke eier av rekrutteringstreffet og kan ikke hente jobbsøkere")
        }
    }

      @OpenApi(
        summary = "Slett en jobbsøker",
        operationId = "slettJobbsøker",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [
            OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true),
            OpenApiParam(name = pathParamJobbsøkerId, type = UUID::class, required = true),
         ],
        responses = [OpenApiResponse("201")],
        path = slettPath,
        methods = [HttpMethod.DELETE]
    )
    private fun slettJobbsøkerHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treffId = TreffId(UUID.fromString(ctx.pathParam(pathParamTreffId)))
        val jobbsøkerId = PersonTreffId(UUID.fromString(ctx.pathParam(pathParamJobbsøkerId)))
        log.info("Sletter jobbsøker $jobbsøkerId for treff $treffId")

        val fødselsnummer = jobbsøkerRepository.hentFødselsnummer(jobbsøkerId)
        if (fødselsnummer == null) {
            log.info("Fant ikke jobbsøker med id $jobbsøkerId for treff $treffId")
            ctx.status(404)
        } else {
            val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
            if (jobbsøker!!.status != JobbsøkerStatus.LAGT_TIL) {
                // Vi støtter kun sletting av jobbsøkere som er i "LAGT_TIL" status
                ctx.status(422)
            } else {
                jobbsøkerRepository.endreStatus(jobbsøkerId.somUuid, JobbsøkerStatus.SLETTET)
                ctx.status(200)
            }
        }
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
        val navIdent = ctx.authenticatedUser().extractNavIdent()
        val eiere = eierRepository.hent(treff)?.tilNavIdenter() ?: throw IllegalStateException("Rekrutteringstreff med id $treff har ingen eiere")

        if(eiere.contains(navIdent) || ctx.authenticatedUser().erUtvikler()) {
            log.info("Henter jobbsøkerhendelser for treff $treff")
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
        } else {
            throw ForbiddenResponse("Personen er ikke eier av rekrutteringstreffet og kan ikke hente jobbsøkerhendelser")
        }
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

        val eiere = eierRepository.hent(treffId)?.tilNavIdenter() ?: throw IllegalStateException("Rekrutteringstreff med id $treffId har ingen eiere")

        if(eiere.contains(navIdent) || ctx.authenticatedUser().erUtvikler()) {
            jobbsøkerRepository.inviter(personTreffIder, treffId, navIdent)
            ctx.status(200)
        } else {
            throw ForbiddenResponse("Personen er ikke eier av rekrutteringstreffet og kan ikke invitere jobbsøkere")
        }
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
                status = it.status,
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
