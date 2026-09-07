package no.nav.toi.oppfølging

import io.javalin.http.BadRequestResponse
import no.nav.toi.HendelseWriter
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.StegRepository
import no.nav.toi.treffgjennomføring.TreffgjennomføringSteg
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import java.sql.Connection
import no.nav.toi.Miljø

class OppfølgingService(
    private val writer: TreffgjennomføringWriter,
    private val repository: OppfølgingRepository,
    private val oppmøteRepository: OppmøteRepository,
    private val stegRepository: StegRepository,
    private val hendelser: HendelseWriter,
    private val miljø: Miljø = Miljø.LOKALT,
) {

    fun lagreVurdering(treffId: TreffId, dto: VurderingDto, navIdent: String): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            kontekst.krevWorkOpEllerLokalUtvikling(miljø)
            val ny = OppfølgingValidering.vurdering(dto)
            val jobbsøkerId = kontekst.jobbsøkerId(ny.personTreffId)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
            val arbeidsgiverId = kontekst.arbeidsgiverId(ny.arbeidsgiverTreffId)
                ?: throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

            val før = repository.hentForTreff(connection, kontekst.treffDbId).firstOrNull {
                it.personTreffId == ny.personTreffId && it.arbeidsgiverTreffId == ny.arbeidsgiverTreffId
            }

            if (ny.harRegistrertNoe()) {
                if (ny.personTreffId !in oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)) {
                    throw BadRequestResponse("Bare fremmøtte jobbsøkere kan få registrert vurdering")
                }
                repository.lagre(connection, jobbsøkerId, arbeidsgiverId, ny)
            } else {
                repository.slett(connection, jobbsøkerId, arbeidsgiverId)
            }

            skrivHendelser(connection, før, ny, navIdent)
            stegRepository.settGjeldendeSteg(connection, kontekst.treffDbId, rad.gjeldendeSteg, TreffgjennomføringSteg.VURDERING)
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
        if (før?.vurderingsstatus != etter.vurderingsstatus) {
            hendelse(
                JobbsøkerHendelsestype.VURDERT,
                mapOf("vurdering" to etter.vurderingsstatus?.name, "forrigeVurdering" to før?.vurderingsstatus?.name),
            )
        }

        val notaterFør = før?.vurderingsnotat.orEmpty().toSet()
        val notaterEtter = etter.vurderingsnotat.toSet()
        (notaterEtter - notaterFør).forEach {
            hendelse(JobbsøkerHendelsestype.NOTAT_LAGT_TIL, mapOf("notat" to it.name))
        }
        (notaterFør - notaterEtter).forEach {
            hendelse(JobbsøkerHendelsestype.NOTAT_FJERNET, mapOf("notat" to it.name))
        }

        if ((før?.avtaltIntervju ?: false) != etter.avtaltIntervju) {
            if (etter.avtaltIntervju) {
                hendelse(
                    JobbsøkerHendelsestype.AVTALT_INTERVJU,
                    mapOf("dato" to etter.avtaltIntervjuDato?.toString()),
                )
            } else {
                hendelse(JobbsøkerHendelsestype.AVTALT_INTERVJU_ANGRET)
            }
        }

        if ((før?.jobbtilbud ?: false) != etter.jobbtilbud) {
            if (etter.jobbtilbud) hendelse(JobbsøkerHendelsestype.JOBBTILBUD_GITT)
            else hendelse(JobbsøkerHendelsestype.ANGRE_JOBBTILBUD_GITT)
        }
    }
}
