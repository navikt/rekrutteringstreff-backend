package no.nav.toi.oppfølging

import io.javalin.http.BadRequestResponse
import no.nav.toi.HendelseWriter
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.FaseRepository
import no.nav.toi.treffgjennomføring.TreffgjennomføringFase
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import java.sql.Connection

class OppfølgingService(
    private val writer: TreffgjennomføringWriter,
    private val repository: OppfølgingRepository,
    private val faseRepository: FaseRepository,
    private val hendelser: HendelseWriter,
) {

    fun lagreVurdering(treffId: TreffId, dto: VurderingDto, navIdent: String): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            val ny = OppfølgingValidering.vurdering(dto)
            val jobbsøkerId = kontekst.jobbsøkerId(ny.personTreffId)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
            val arbeidsgiverId = kontekst.arbeidsgiverId(ny.arbeidsgiverTreffId)
                ?: throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

            val før = repository.hentForTreff(connection, kontekst.treffDbId).firstOrNull {
                it.personTreffId == ny.personTreffId && it.arbeidsgiverTreffId == ny.arbeidsgiverTreffId
            }

            if (ny.harRegistrertNoe()) repository.lagre(connection, jobbsøkerId, arbeidsgiverId, ny)
            else repository.slett(connection, jobbsøkerId, arbeidsgiverId)

            skrivHendelser(connection, før, ny, navIdent)
            faseRepository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.VURDERING)
        }

    private fun skrivHendelser(connection: Connection, før: Vurdering?, etter: Vurdering, navIdent: String) {
        // Registreringene gjelder paret jobbsøker × arbeidsgiver, men handler om personen.
        // Hendelsen skrives derfor bare på jobbsøkeren, med arbeidsgiveren som kontekst i hendelse_data.
        fun hendelse(
            hendelsestype: JobbsøkerHendelsestype,
            ekstra: Map<String, Any?> = emptyMap(),
        ) = hendelser.forJobbsøker(
            connection, etter.personTreffId, hendelsestype, navIdent,
            ekstra + ("arbeidsgiverTreffId" to etter.arbeidsgiverTreffId.somString),
        )

        // Uten forrigeVurdering kan ikke tidslinja fortelle at noen gikk fra «Aktuell» til «Ikke aktuell».
        if (før?.vurdering != etter.vurdering) {
            hendelse(
                JobbsøkerHendelsestype.VURDERT,
                mapOf("vurdering" to etter.vurdering?.name, "forrigeVurdering" to før?.vurdering?.name),
            )
        }

        val notaterFør = før?.notater.orEmpty().toSet()
        val notaterEtter = etter.notater.toSet()
        (notaterEtter - notaterFør).forEach {
            hendelse(JobbsøkerHendelsestype.NOTAT_LAGT_TIL, mapOf("notat" to it.name))
        }
        (notaterFør - notaterEtter).forEach {
            hendelse(JobbsøkerHendelsestype.NOTAT_FJERNET, mapOf("notat" to it.name))
        }

        if ((før?.andregangsintervju ?: false) != etter.andregangsintervju) {
            if (etter.andregangsintervju) {
                hendelse(
                    JobbsøkerHendelsestype.ANDREGANGSINTERVJU_AVTALT,
                    mapOf("dato" to etter.andregangsintervjuDato?.toString()),
                )
            } else {
                hendelse(JobbsøkerHendelsestype.ANGRE_ANDREGANGSINTERVJU_AVTALT)
            }
        }

        if ((før?.jobbtilbud ?: false) != etter.jobbtilbud) {
            if (etter.jobbtilbud) hendelse(JobbsøkerHendelsestype.JOBBTILBUD_GITT)
            else hendelse(JobbsøkerHendelsestype.ANGRE_JOBBTILBUD_GITT)
        }
    }
}
