package no.nav.toi.jobbsoker;

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.jobbsoker.dto.JobbsøkerMedStatuserOutboundDto
import no.nav.toi.jobbsoker.dto.StatuserOutboundDto
import no.nav.toi.jobbsoker.dto.toOutboundDto
import no.nav.toi.rekrutteringstreff.TreffId
import java.util.UUID
import kotlin.collections.findLast

class JobbsøkerInnloggetBorgerController(
    private val jobbsøkerService: JobbsøkerService,
    javalin: Javalin
) {
    companion object {
        private const val pathParamTreffId = "id"
        private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

        private const val borgerJobbsøkerPath = "$endepunktRekrutteringstreff/{$pathParamTreffId}/jobbsoker/borger"
        private const val svarJaPath = "$borgerJobbsøkerPath/svar-ja"
        private const val svarNeiPath = "$borgerJobbsøkerPath/svar-nei"
    }

    init {
        javalin.post(svarJaPath, svarJaHandler())
        javalin.post(svarNeiPath, svarNeiHandler())
        javalin.get(borgerJobbsøkerPath, hentBorgerJobbsøkerHandler())
    }

    @OpenApi(
        summary = "Registrerer at en jobbsøker har takket ja til invitasjon.",
        operationId = "svarJaTilInvitasjon",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [OpenApiResponse("200", description = "Hendelse for 'svart ja' er lagt til.")],
        path = svarJaPath,
        methods = [HttpMethod.POST]
    )
    private fun svarJaHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.BORGER)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))

        ctx.authenticatedUser().extractPid().let { pid ->
            if (pid.isEmpty()) {
                throw IllegalArgumentException("PID må oppgis for å hente jobbsøker")
            }
            jobbsøkerService.svarJaTilInvitasjon(Fødselsnummer(pid), treffId, pid)
            ctx.status(200)
        }

    }

    @OpenApi(
        summary = "Registrerer at en jobbsøker har takket nei til invitasjon.",
        operationId = "svarNeiTilInvitasjon",
        security = [OpenApiSecurity("BearerAuth")],
        pathParams = [OpenApiParam(name = pathParamTreffId, type = UUID::class, required = true)],
        responses = [OpenApiResponse("200", description = "Hendelse for 'svart nei' er lagt til.")],
        path = svarNeiPath,
        methods = [HttpMethod.POST]
    )
    private fun svarNeiHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.BORGER)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))
        ctx.authenticatedUser().extractPid().let { pid ->
            if (pid.isEmpty()) {
                throw IllegalArgumentException("PID må oppgis for å hente jobbsøker")
            }
            jobbsøkerService.svarNeiTilInvitasjon(Fødselsnummer(pid), treffId, pid)
            ctx.status(200)
        }
    }

    @OpenApi(
        summary = "Hent en jobbsøker basert på fødselsnummer",
        operationId = "hentJobbsøker",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [
            OpenApiParam(name = pathParamTreffId, type = UUID::class, description = "ID for rekrutteringstreffet", required = true)
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Jobbsøker funnet", content = [OpenApiContent(from = JobbsøkerMedStatuserOutboundDto::class, example = """
            {
              "treffId": "c1b2c3d4-e5f6-7890-1234-567890abcdef",
              "fødselsnummer": "12345678901",
              "fornavn": "Ola",
              "etternavn": "Nordmann",
              "navkontor": "NAV Grünerløkka",
              "veilederNavn": "Vera Veileder",
              "veilederNavIdent": "V123456",
              "statuser": {
                "erPåmeldt": true,
                "erInvitert": true,
                "harSvart": true
              },
              "hendelser": [
                {
                  "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                  "tidspunkt": "2023-05-15T10:30:00+02:00",
                  "hendelsestype": "OPPRETTET",
                  "opprettetAvAktørType": "ARRANGØR",
                  "aktørIdentifikasjon": "Z999999"
                },
                {
                  "id": "d4e5f6a7-b8c9-0123-4567-890abcdef123",
                  "tidspunkt": "2023-05-16T10:00:00+02:00",
                  "hendelsestype": "INVITERT",
                  "opprettetAvAktørType": "ARRANGØR",
                  "aktørIdentifikasjon": "Z999999"
                },
                {
                  "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                  "tidspunkt": "2023-05-16T11:00:00+02:00",
                  "hendelsestype": "SVART_JA_TIL_INVITASJON",
                  "opprettetAvAktørType": "JOBBSØKER",
                  "aktørIdentifikasjon": "12345678901"
                }
              ]
            }
        """)]),
            OpenApiResponse(status = "404", description = "Jobbsøker ikke funnet")
        ],
        path = borgerJobbsøkerPath,
        methods = [HttpMethod.GET]
    )
    private fun hentBorgerJobbsøkerHandler(): (Context) -> Unit = { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.BORGER)
        val treffId = TreffId(ctx.pathParam(pathParamTreffId))

        ctx.authenticatedUser().extractPid().let { pid ->
            if (pid.isEmpty()) {
                throw IllegalArgumentException("PID må oppgis for å hente jobbsøker")
            }
            val jobbsøker = jobbsøkerService.hentJobbsøker(treffId, Fødselsnummer(pid))
            if (jobbsøker == null) {
                ctx.status(404)
            } else {
                ctx.json(jobbsøker.toOutboundDtoMedStatuser())
            }
        }
    }

    private fun Jobbsøker.toOutboundDtoMedStatuser(): JobbsøkerMedStatuserOutboundDto {
        val sisteSvar = hendelser.findLast {
            it.hendelsestype == JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON || it.hendelsestype == JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON
        }

        return JobbsøkerMedStatuserOutboundDto(
            personTreffId = personTreffId.toString(),
            treffId = treffId.somString,
            fødselsnummer = fødselsnummer.asString,
            fornavn = fornavn.asString,
            etternavn = etternavn.asString,
            navkontor = navkontor?.asString,
            veilederNavn = veilederNavn?.asString,
            veilederNavIdent = veilederNavIdent?.asString,
            statuser = StatuserOutboundDto(
                erPåmeldt = sisteSvar?.hendelsestype == JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
                erInvitert = hendelser.any { it.hendelsestype == JobbsøkerHendelsestype.INVITERT },
                harSvart = sisteSvar != null
            ),
            hendelser = hendelser.map { it.toOutboundDto() }
        )
    }
}