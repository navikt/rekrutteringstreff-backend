package no.nav.toi.jobbsoker

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.AuditLog
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.jobbsoker.dto.JobbsøkerDto
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerDataOutboundDto
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseOutboundDto
import no.nav.toi.jobbsoker.dto.JobbsøkerOutboundDto
import no.nav.toi.jobbsoker.dto.JobbsøkereOutboundDto
import no.nav.toi.jobbsoker.dto.PersonTreffIderDto
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


class JobbsøkerController(
    private val jobbsøkerService: JobbsøkerService,
    private val eierService: EierService,
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
                "fornavn": "Ola",
                "etternavn": "Nordmann",
                "navkontor": "NAV Oslo",
                "veilederNavn": "Kari Nordmann",
                "veilederNavIdent": "NAV123"
              },
              {
                "fødselsnummer": "10987654321",
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
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)
        val dtoer = ctx.bodyAsClass<Array<JobbsøkerDto>>()
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        jobbsøkerService.leggTilJobbsøkere(dtoer.map { it.domene() }, treff, ctx.extractNavIdent())
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
                from = JobbsøkereOutboundDto::class,
                example = """{
                    "jobbsøkere": [
                        {   
                            "personTreffId": "any-uuid",
                            "fødselsnummer": "12345678901",
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
                            "fornavn": "Kari",
                            "etternavn": "Nordmann",
                            "navkontor": null,
                            "veilederNavn": null,
                            "veilederNavIdent": null,
                            "hendelser": []
                        }
                    ],
                    "antallSynlige": 2,
                    "antallSkjulte": 2,
                    "antallSlettede": 1
                }"""
            )]
        )],
        path = jobbsøkerPath,
        methods = [HttpMethod.GET]
    )
    private fun hentJobbsøkereHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        if (eierService.erEierEllerUtvikler(treffId = treff, navIdent = navIdent, context = ctx)) {
            val jobbsøkereMedTellinger = jobbsøkerService.hentJobbsøkereMedTellinger(treff)
            AuditLog.loggVisningAvJobbsøkereTilhørendesRekrutteringstreff(navIdent, treff)
            ctx.status(200).json(JobbsøkereOutboundDto(
                jobbsøkere = jobbsøkereMedTellinger.jobbsøkere.toOutboundDto(),
                antallSynlige = jobbsøkereMedTellinger.antallSynlige,
                antallSkjulte = jobbsøkereMedTellinger.antallSkjulte,
                antallSlettede = jobbsøkereMedTellinger.antallSlettede
            ))
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
        val navIdent = ctx.authenticatedUser().extractNavIdent()

        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            log.info("Sletter jobbsøker $jobbsøkerId for treff $treffId")

            when (jobbsøkerService.markerSlettet(jobbsøkerId, treffId, navIdent)) {
                MarkerSlettetResultat.OK -> ctx.status(200)
                MarkerSlettetResultat.IKKE_FUNNET -> {
                    log.info("Fant ikke jobbsøker med id $jobbsøkerId for treff $treffId")
                    ctx.status(404)
                }
                MarkerSlettetResultat.IKKE_TILLATT -> ctx.status(422)
            }
        } else {
            throw ForbiddenResponse("Personen er ikke eier av rekrutteringstreffet og kan ikke slette jobbsøkere")
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
            description = "Liste over jobbsøkerhendelser. Feltet hendelseData er polymorft basert på hendelsestype (oneOf i schema). For de fleste hendelsestyper er hendelseData null.",
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
                    "fornavn": "Ola",
                    "etternavn": "Nordmann",
                    "personTreffId": "any-uuid",
                    "hendelseData": null
                },
                {
                    "id": "any-uuid-2",
                    "tidspunkt": "2025-04-14T11:00:00Z",
                    "hendelsestype": "MOTTATT_SVAR_FRA_MINSIDE",
                    "opprettetAvAktørType": "SYSTEM",
                    "aktørIdentifikasjon": null,
                    "fødselsnummer": "12345678901",
                    "fornavn": "Ola",
                    "etternavn": "Nordmann",
                    "personTreffId": "any-uuid",
                    "hendelseData": {
                        "varselId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                        "avsenderReferanseId": "c1e80bbc-fe0e-4cfb-b291-23317fc48729",
                        "fnr": "12345678901",
                        "eksternStatus": "SENDT",
                        "minsideStatus": "AKTIV",
                        "opprettet": "2025-04-14T10:59:00+02:00",
                        "avsenderNavident": "Z123456",
                        "eksternFeilmelding": null,
                        "eksternKanal": "SMS",
                        "mal": "KANDIDAT_INVITERT_TREFF",
                        "flettedata": null
                    }
                },
                {
                    "id": "any-uuid-3",
                    "tidspunkt": "2025-04-14T12:00:00Z",
                    "hendelsestype": "TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON",
                    "opprettetAvAktørType": "ARRANGØR",
                    "aktørIdentifikasjon": "Z123456",
                    "fødselsnummer": "12345678901",
                    "fornavn": "Ola",
                    "etternavn": "Nordmann",
                    "personTreffId": "any-uuid",
                    "hendelseData": {
                        "navn": null,
                        "sted": {
                            "gammelVerdi": "Storgata 1, 0182 Oslo",
                            "nyVerdi": "Kirkegata 15, 0153 Oslo",
                            "skalVarsle": true
                        },
                        "tidspunkt": {
                            "gammelVerdi": "2025-04-20T10:00:00+02:00",
                            "nyVerdi": "2025-04-21T10:00:00+02:00",
                            "skalVarsle": true
                        },
                        "svarfrist": null,
                        "introduksjon": null
                    }
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

        if (eierService.erEierEllerUtvikler(treffId = treff, navIdent = navIdent, context = ctx)) {
            log.info("Henter jobbsøkerhendelser for treff $treff")
            val hendelser = jobbsøkerService.hentJobbsøkerHendelser(treff)
            ctx.status(200).json(hendelser.map { h ->
                JobbsøkerHendelseMedJobbsøkerDataOutboundDto(
                    id = h.id.toString(),
                    tidspunkt = h.tidspunkt,
                    hendelsestype = h.hendelsestype.toString(),
                    opprettetAvAktørType = h.opprettetAvAktørType.toString(),
                    aktørIdentifikasjon = h.aktørIdentifikasjon,
                    fødselsnummer = h.fødselsnummer.asString,
                    fornavn = h.fornavn.asString,
                    etternavn = h.etternavn.asString,
                    personTreffId = h.personTreffId.somString,
                    hendelseData = h.hendelseData
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

        if (eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = ctx)) {
            jobbsøkerService.inviter(personTreffIder, treffId, navIdent)
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
                        aktørIdentifikasjon = h.aktørIdentifikasjon,
                        hendelseData = h.hendelseData
                    )
                }
            )
        }
}
