package no.nav.toi.arbeidsgiver

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.bodyAsClass
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverOutboundDto
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.*

class ArbeidsgiverController(
    private val arbeidsgiverRepository: ArbeidsgiverRepository,
    private val eierRepository: EierRepository,
    javalin: Javalin
) {

    companion object {
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val pathParamTreffId = "id"
        private const val arbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver"
        private const val hendelserArbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver/hendelser"
        private const val pathParamArbeidsgiverId = "arbeidsgiverId"
        private const val arbeidsgiverItemPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver/{$pathParamArbeidsgiverId}"
    }

    init {
        javalin.post(arbeidsgiverPath, leggTilArbeidsgiverHandler())
        javalin.get(arbeidsgiverPath, hentArbeidsgivereHandler())
        javalin.get(hendelserArbeidsgiverPath, hentArbeidsgiverHendelserHandler())
        javalin.delete(arbeidsgiverItemPath, slettArbeidsgiverHandler())
    }

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
                example = """{"organisasjonsnummer": "123456789", "navn": "Example Company", næringskoder: [{"kode": "47.111", "beskrivelse": "Detaljhandel med bredt varesortiment uten salg av drivstoff"}]}"""
            )]
        ),
        responses = [OpenApiResponse(
            status = "201",
            description = "Arbeidsgiver opprettet"
        )],
        path = arbeidsgiverPath,
        methods = [HttpMethod.POST]
    )
    private fun leggTilArbeidsgiverHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val dto: LeggTilArbeidsgiverDto = ctx.bodyAsClass()
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.authenticatedUser().extractNavIdent()
        //sjekk at personen er eier av treff
        val eiere = eierRepository.hent(treff)?.tilNavIdenter()
            ?: throw IllegalStateException("Rekrutteringstreff med id ${treff.somString} har ingen eiere")

        if (eiere.contains(navIdent) || ctx.authenticatedUser().erUtvikler()) {
            arbeidsgiverRepository.leggTil(dto.somLeggTilArbeidsgiver(), treff, ctx.extractNavIdent())
            ctx.status(201)
        } else {
            throw ForbiddenResponse("Bruker er ikke eier av rekrutteringstreff med id ${treff.somString}")
        }

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
                    "arbeidsgiverTreffId": "any-uuid",
                    "organisasjonsnummer": "123456789",
                    "navn": "Example Company",
                },
                {
                    "arbeidsgiverTreffId": "any-uuid",
                    "organisasjonsnummer": "987654321",
                    "navn": "Another Company",
                    "hendelser": []
                }
            ]"""
            )]
        )],
        path = arbeidsgiverPath,
        methods = [HttpMethod.GET]
    ) // TODO tror alle skal få lov til å hente dette, også jobbsøkerrettet
    private fun hentArbeidsgivereHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val arbeidsgivere = arbeidsgiverRepository.hentArbeidsgivere(treff)
        ctx.status(200).json(arbeidsgivere.toOutboundDto())
    }

    private fun List<Arbeidsgiver>.toOutboundDto(): List<ArbeidsgiverOutboundDto> =
        map { arbeidsgiver ->
            ArbeidsgiverOutboundDto(
                arbeidsgiverTreffId = arbeidsgiver.arbeidsgiverTreffId.somString,
                organisasjonsnummer = arbeidsgiver.orgnr.asString,
                navn = arbeidsgiver.orgnavn.asString,
                status = arbeidsgiver.status.name,
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
                    "hendelsestype": "OPPRETTET",
                    "opprettetAvAktørType": "ARRANGØR",
                    "aktøridentifikasjon": "testperson",
                    "orgnr": "123456789",
                    "orgnavn": "Example Company",
                }
            ]"""
            )]
        )],
        path = hendelserArbeidsgiverPath,
        methods = [HttpMethod.GET]
    )
    private fun hentArbeidsgiverHendelserHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = ctx.authenticatedUser().extractNavIdent()
        val eiere = eierRepository.hent(treff)?.tilNavIdenter()
            ?: throw IllegalStateException("Rekrutteringstreff med id ${treff.somString} har ingen eiere")

        if (eiere.contains(navIdent) || ctx.authenticatedUser().erUtvikler()) {
            val hendelser = arbeidsgiverRepository.hentArbeidsgiverHendelser(treff)
            ctx.status(200).json(hendelser.map { h ->
                ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto(
                    id = h.id.toString(),
                    tidspunkt = h.tidspunkt,
                    hendelsestype = h.hendelsestype.toString(),
                    opprettetAvAktørType = h.opprettetAvAktørType.toString(),
                    aktøridentifikasjon = h.aktøridentifikasjon,
                    orgnr = h.orgnr.asString,
                    orgnavn = h.orgnavn.asString,
                )
            })
        } else {
            throw ForbiddenResponse("Bruker er ikke eier av rekrutteringstreff med id ${treff.somString}")
        }
    }

    @OpenApi(
        summary = "Slett en arbeidsgiver",
        operationId = "slettArbeidsgiver",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [
            OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true),
            OpenApiParam(name = pathParamArbeidsgiverId, type = UUID::class, required = true)
        ],
        responses = [OpenApiResponse(status = "204")],
        path = arbeidsgiverItemPath,
        methods = [HttpMethod.DELETE]
    )
    private fun slettArbeidsgiverHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val id = UUID.fromString(ctx.pathParam(pathParamArbeidsgiverId))
        val navIdent = ctx.extractNavIdent()
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))

        val eiere = eierRepository.hent(treffId)?.tilNavIdenter()
            ?: throw IllegalArgumentException("Rekrutteringstreff med id ${treffId.somString} har ingen eiere")

        if (eiere.contains(navIdent) || ctx.authenticatedUser().erUtvikler()) {
            if (arbeidsgiverRepository.slett(id, navIdent)) ctx.status(204) else throw NotFoundResponse()
        } else {
            throw ForbiddenResponse("Bruker er ikke eier av rekrutteringstreff med id ${treffId.somString}")
        }
    }

    private data class LeggTilArbeidsgiverDto(
        val organisasjonsnummer: String,
        val navn: String,
        val næringskoder: List<Næringskode> = emptyList()
    ) {
        fun somLeggTilArbeidsgiver() = LeggTilArbeidsgiver(Orgnr(organisasjonsnummer), Orgnavn(navn), næringskoder.map {
            Næringskode(it.kode, it.beskrivelse)
        })
    }
}
