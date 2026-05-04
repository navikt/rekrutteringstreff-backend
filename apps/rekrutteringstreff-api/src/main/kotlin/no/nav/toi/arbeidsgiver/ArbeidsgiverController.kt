package no.nav.toi.arbeidsgiver

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.bodyAsClass
import io.javalin.http.NotFoundResponse
import io.javalin.openapi.*
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverBehovDto
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverMedBehovDto
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverOutboundDto
import no.nav.toi.arbeidsgiver.dto.BehovMetadataDto
import no.nav.toi.arbeidsgiver.dto.LeggTilArbeidsgiverDto
import no.nav.toi.arbeidsgiver.dto.LeggTilArbeidsgiverMedBehovDto
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierService
import java.util.*

class ArbeidsgiverController(
    private val arbeidsgiverService: ArbeidsgiverService,
    private val eierService: EierService,
    javalin: Javalin
) {

    companion object {
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"
        private const val pathParamTreffId = "id"
        private const val arbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver"
        private const val arbeidsgiverMedBehovPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver-med-behov"
        private const val hendelserArbeidsgiverPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver/hendelser"
        private const val pathParamArbeidsgiverId = "arbeidsgiverId"
        private const val arbeidsgiverItemPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver/{$pathParamArbeidsgiverId}"
        private const val arbeidsgiverBehovPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/arbeidsgiver/{$pathParamArbeidsgiverId}/behov"
        private const val behovMetadataPath = "/api/rekrutteringstreff/arbeidsgiver-behov-metadata"
    }

    private fun requireEier(ctx: Context, treff: TreffId): String {
        val navIdent = ctx.extractNavIdent()
        if (!eierService.erEierEllerUtvikler(treffId = treff, navIdent = navIdent, context = ctx)) {
            throw ForbiddenResponse("Bruker er ikke eier av rekrutteringstreff med id ${treff.somString}")
        }
        return navIdent
    }

    init {
        javalin.post(arbeidsgiverPath, leggTilArbeidsgiverHandler())
        javalin.post(arbeidsgiverMedBehovPath, leggTilArbeidsgiverMedBehovHandler())
        javalin.get(arbeidsgiverPath, hentArbeidsgivereHandler())
        javalin.get(arbeidsgiverMedBehovPath, hentArbeidsgivereMedBehovHandler())
        javalin.get(hendelserArbeidsgiverPath, hentArbeidsgiverHendelserHandler())
        javalin.delete(arbeidsgiverItemPath, slettArbeidsgiverHandler())
        javalin.put(arbeidsgiverBehovPath, oppdaterBehovHandler())
        javalin.get(behovMetadataPath, hentBehovMetadataHandler())
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
        val navIdent = requireEier(ctx, treff)
        arbeidsgiverService.leggTilArbeidsgiver(dto.somLeggTilArbeidsgiver(), treff, navIdent)
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
                    "arbeidsgiverTreffId": "any-uuid",
                    "organisasjonsnummer": "123456789",
                    "navn": "Example Company",
                    "status": "AKTIV"
                },
                {
                    "arbeidsgiverTreffId": "any-uuid",
                    "organisasjonsnummer": "987654321",
                    "navn": "Another Company",
                    "status": "SLETTET"
                }
            ]"""
            )]
        )],
        path = arbeidsgiverPath,
        methods = [HttpMethod.GET]
    )
    private fun hentArbeidsgivereHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET, Rolle.BORGER)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val arbeidsgivere = arbeidsgiverService.hentArbeidsgivere(treff)
        ctx.status(200).json(arbeidsgivere.toOutboundDto())
    }

    private fun List<Arbeidsgiver>.toOutboundDto(): List<ArbeidsgiverOutboundDto> =
        map { arbeidsgiver ->
            ArbeidsgiverOutboundDto(
                arbeidsgiverTreffId = arbeidsgiver.arbeidsgiverTreffId.somString,
                organisasjonsnummer = arbeidsgiver.orgnr.asString,
                navn = arbeidsgiver.orgnavn.asString,
                status = arbeidsgiver.status.name,
                gateadresse = arbeidsgiver.gateadresse,
                postnummer = arbeidsgiver.postnummer,
                poststed = arbeidsgiver.poststed,
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
        requireEier(ctx, treff)
        val hendelser = arbeidsgiverService.hentArbeidsgiverHendelser(treff)
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
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = requireEier(ctx, treffId)
        if (arbeidsgiverService.markerArbeidsgiverSlettet(id, treffId, navIdent)) ctx.status(204) else throw NotFoundResponse()
    }

    @OpenApi(
        summary = "Legg til arbeidsgiver med behov atomisk",
        operationId = "leggTilArbeidsgiverMedBehov",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = LeggTilArbeidsgiverMedBehovDto::class,
            example = """{
              "organisasjonsnummer": "123456789",
              "navn": "Example Company",
              "næringskoder": [{"kode": "47.111", "beskrivelse": "Detaljhandel"}],
              "gateadresse": "Storgata 1",
              "postnummer": "0155",
              "poststed": "Oslo",
              "behov": {
                "samledeKvalifikasjoner": [{"label": "Kokk", "kategori": "YRKESTITTEL", "konseptId": 175819}],
                "arbeidssprak": ["Norsk", "Engelsk"],
                "antall": 3,
                "ansettelsesformer": ["Fast", "Vikariat"],
                "personligeEgenskaper": [{"label": "Kundebehandler", "kategori": "PERSONLIG_EGENSKAP", "konseptId": 700005}]
              }
            }"""
        )]),
        responses = [OpenApiResponse(status = "201", description = "Arbeidsgiver med behov opprettet")],
        path = arbeidsgiverMedBehovPath,
        methods = [HttpMethod.POST]
    )
    private fun leggTilArbeidsgiverMedBehovHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val navIdent = requireEier(ctx, treff)
        val dto: LeggTilArbeidsgiverMedBehovDto = ctx.bodyAsClass()
        arbeidsgiverService.leggTilArbeidsgiverMedBehov(
            arbeidsgiver = dto.somLeggTilArbeidsgiver(),
            behov = dto.behov.somArbeidsgiverBehov(),
            treffId = treff,
            navIdent = navIdent,
        )
        ctx.status(201)
    }

    @OpenApi(
        summary = "Hent alle arbeidsgivere med behov for et rekrutteringstreff",
        operationId = "hentArbeidsgivereMedBehov",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = Array<ArbeidsgiverMedBehovDto>::class,
                example = """[
  {
    "arbeidsgiverTreffId": "any-uuid",
    "organisasjonsnummer": "123456789",
        "navn": "Example Company",
        "behov": {
          "samledeKvalifikasjoner": [{"label": "Kokk", "kategori": "YRKESTITTEL", "konseptId": 175819}],
          "arbeidssprak": ["Norsk"],
          "antall": 3,
          "ansettelsesformer": ["Fast"],
          "personligeEgenskaper": []
        }
      }
    ]"""
            )]
        )],
        path = arbeidsgiverMedBehovPath,
        methods = [HttpMethod.GET]
    )
    private fun hentArbeidsgivereMedBehovHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        requireEier(ctx, treff)
        val resultat = arbeidsgiverService.hentArbeidsgivereMedBehov(treff)
            .map { ArbeidsgiverMedBehovDto.fra(it) }
        ctx.status(200).json(resultat)
    }

    @OpenApi(
        summary = "Upsert behov for en eksisterende arbeidsgiver i treffet",
        operationId = "oppdaterArbeidsgiverBehov",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [
            OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true),
            OpenApiParam(name = pathParamArbeidsgiverId, type = UUID::class, required = true)
        ],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(
            from = ArbeidsgiverBehovDto::class,
            example = """{
          "samledeKvalifikasjoner": [{"label": "Kokk", "kategori": "YRKESTITTEL", "konseptId": 175819}],
          "arbeidssprak": ["Norsk", "Engelsk"],
          "antall": 5,
          "ansettelsesformer": ["Fast"],
          "personligeEgenskaper": []
        }"""
        )]),
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = ArbeidsgiverMedBehovDto::class,
                example = """{
              "arbeidsgiverTreffId": "any-uuid",
              "organisasjonsnummer": "123456789",
              "navn": "Example Company",
              "behov": {
                "samledeKvalifikasjoner": [{"label": "Kokk", "kategori": "YRKESTITTEL", "konseptId": 175819}],
                "arbeidssprak": ["Norsk", "Engelsk"],
                "antall": 5,
                "ansettelsesformer": ["Fast"],
                "personligeEgenskaper": []
              }
            }"""
            )]
        )],
        path = arbeidsgiverBehovPath,
        methods = [HttpMethod.PUT]
    )
    private fun oppdaterBehovHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
        val treff = TreffId(ctx.pathParam(pathParamTreffId))
        val arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.fromString(ctx.pathParam(pathParamArbeidsgiverId)))
        val navIdent = requireEier(ctx, treff)
        val dto: ArbeidsgiverBehovDto = ctx.bodyAsClass()
        val oppdatert = arbeidsgiverService.oppdaterBehov(
            arbeidsgiverTreffId = arbeidsgiverTreffId,
            treffId = treff,
            behov = dto.somArbeidsgiverBehov(),
            navIdent = navIdent,
        ) ?: throw NotFoundResponse("Arbeidsgiver ${arbeidsgiverTreffId.somString} finnes ikke i treff ${treff.somString}")
        ctx.status(200).json(ArbeidsgiverMedBehovDto.fra(oppdatert))
    }

    @OpenApi(
        summary = "Hent metadata for behov-feltene (lukkede lister)",
        operationId = "hentArbeidsgiverBehovMetadata",
        security = [OpenApiSecurity(name = "BearerAuth")],
        responses = [OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = BehovMetadataDto::class,
                example = """{
              "ansettelsesformer": ["Fast", "Vikariat", "Engasjement", "Prosjekt", "Åremål", "Sesong", "Feriejobb", "Trainee", "Lærling", "Selvstendig næringsdrivende", "Annet"],
              "arbeidssprak": ["Norsk", "Engelsk", "Svensk", "Dansk", "Tysk", "Fransk", "Spansk", "Annet"]
            }"""
            )]
        )],
        path = behovMetadataPath,
        methods = [HttpMethod.GET]
    )
    private fun hentBehovMetadataHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)
        ctx.status(200).json(
            BehovMetadataDto(
                ansettelsesformer = Ansettelsesform.entries.map { it.apiNavn },
                arbeidssprak = Arbeidssprak.entries.map { it.apiNavn },
            )
        )
    }
}
