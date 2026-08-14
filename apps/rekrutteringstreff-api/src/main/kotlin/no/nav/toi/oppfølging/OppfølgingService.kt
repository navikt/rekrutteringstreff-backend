package no.nav.toi.oppfølging

import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.HendelseWriter
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.executeInTransaction
import no.nav.toi.låsTreff
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.TreffgjennomføringFase
import no.nav.toi.treffgjennomføring.TreffgjennomføringReader
import no.nav.toi.treffgjennomføring.FaseRepository
import no.nav.toi.treffgjennomføring.TreffkontekstRepository
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import java.sql.Connection
import javax.sql.DataSource

class OppfølgingService(
    private val dataSource: DataSource,
    private val repository: OppfølgingRepository,
    private val kontekstRepository: TreffkontekstRepository,
    private val faseRepository: FaseRepository,
    private val reader: TreffgjennomføringReader,
    private val hendelser: HendelseWriter,
) {

    fun lagreVurdering(treffId: TreffId, dto: VurderingDto, navIdent: String): TreffgjennomføringDto =
        dataSource.executeInTransaction { connection ->
            val kontekst = kontekstRepository.hent(connection, treffId)
                ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
            connection.låsTreff(kontekst.treffDbId)

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
            faseRepository.meldFramdrift(connection, kontekst.treffDbId, TreffgjennomføringFase.VURDERING)

            reader.les(connection, kontekst)
        }

    fun slettForJobbsøker(connection: Connection, jobbsøkerId: Long) =
        repository.slettForJobbsøker(connection, jobbsøkerId)

    fun tellForJobbsøker(connection: Connection, jobbsøkerId: Long): Int =
        repository.tellForJobbsøker(connection, jobbsøkerId)

    private fun skrivHendelser(connection: Connection, før: Vurdering?, etter: Vurdering, navIdent: String) {
        fun par(
            jobbsøkertype: JobbsøkerHendelsestype,
            arbeidsgivertype: ArbeidsgiverHendelsestype,
            ekstra: Map<String, Any?> = emptyMap(),
        ) = hendelser.forJobbsøkerOgArbeidsgiver(
            connection, etter.personTreffId, etter.arbeidsgiverTreffId,
            jobbsøkertype, arbeidsgivertype, navIdent, ekstra,
        )

        // Uten forrigeVurdering kan ikke tidslinja fortelle at noen gikk fra «Aktuell» til «Ikke aktuell».
        if (før?.vurdering != etter.vurdering) {
            par(
                JobbsøkerHendelsestype.VURDERT, ArbeidsgiverHendelsestype.VURDERT,
                mapOf("vurdering" to etter.vurdering?.name, "forrigeVurdering" to før?.vurdering?.name),
            )
        }

        val notaterFør = før?.notater.orEmpty().toSet()
        val notaterEtter = etter.notater.toSet()
        (notaterEtter - notaterFør).forEach {
            par(
                JobbsøkerHendelsestype.NOTAT_LAGT_TIL, ArbeidsgiverHendelsestype.NOTAT_LAGT_TIL,
                mapOf("notat" to it.name),
            )
        }
        (notaterFør - notaterEtter).forEach {
            par(
                JobbsøkerHendelsestype.NOTAT_FJERNET, ArbeidsgiverHendelsestype.NOTAT_FJERNET,
                mapOf("notat" to it.name),
            )
        }

        if ((før?.andregangsintervju ?: false) != etter.andregangsintervju) {
            if (etter.andregangsintervju) {
                par(
                    JobbsøkerHendelsestype.ANDREGANGSINTERVJU_AVTALT,
                    ArbeidsgiverHendelsestype.ANDREGANGSINTERVJU_AVTALT,
                    mapOf("dato" to etter.andregangsintervjuDato?.toString()),
                )
            } else {
                par(
                    JobbsøkerHendelsestype.ANGRE_ANDREGANGSINTERVJU_AVTALT,
                    ArbeidsgiverHendelsestype.ANGRE_ANDREGANGSINTERVJU_AVTALT,
                )
            }
        }

        if ((før?.jobbtilbud ?: false) != etter.jobbtilbud) {
            if (etter.jobbtilbud) {
                par(JobbsøkerHendelsestype.JOBBTILBUD_GITT, ArbeidsgiverHendelsestype.JOBBTILBUD_GITT)
            } else {
                par(JobbsøkerHendelsestype.ANGRE_JOBBTILBUD_GITT, ArbeidsgiverHendelsestype.ANGRE_JOBBTILBUD_GITT)
            }
        }
    }
}
