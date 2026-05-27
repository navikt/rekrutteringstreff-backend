package no.nav.toi.formidling

import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.ArbeidsgiverService
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.executeInTransaction
import no.nav.toi.formidling.dto.OpprettFormidlingDto
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

class FormidlingService(
    private val dataSource: DataSource,
    private val formidlingRepository: FormidlingRepository,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val jobbsøkerService: JobbsøkerService,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val stillingKlient: StillingKlient,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun opprettFormidling(
        treffId: TreffId,
        opprettFormidling: OpprettFormidlingDto,
        navIdent: String,
        userToken: String
    ): List<Formidling> {
        val (arbeidsgiver, jobbsøkere) = validerOgHentArbeidsgivereOgJobbsøkere(treffId, opprettFormidling)
        val stillingIdOgKandidatlisteId = opprettStillingOgKandidatliste(treffId, opprettFormidling, userToken)
        val formidlinger = lagreFormidlinger(treffId, jobbsøkere, arbeidsgiver, stillingIdOgKandidatlisteId.stillingId)
        leggKandidaterPåListenOgSendTilStatistikk(stillingIdOgKandidatlisteId, jobbsøkere)
        endreJobbsøkerStatusOgLeggTilHendelser(formidlinger, navIdent)
        return formidlinger
    }

    private fun validerOgHentArbeidsgivereOgJobbsøkere(treffId: TreffId, opprettFormidling: OpprettFormidlingDto): Pair<Arbeidsgiver, List<Jobbsøker>> {
        rekrutteringstreffRepository.hent(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId finnes ikke")

        val arbeidsgiver = arbeidsgiverService.hentArbeidsgiver(treffId, Orgnr(opprettFormidling.orgnr))
            ?: throw ArbeidsgiverIkkeFunnetException("Arbeidsgiver med orgnr ${opprettFormidling.orgnr} finnes ikke på treffet")

        val jobbsøkere = opprettFormidling.fødselsnumre.map { fnr ->
            jobbsøkerService.hentJobbsøker(treffId, Fødselsnummer(fnr))
                ?: throw JobbsøkerIkkeFunnetPåTreffException("Jobbsøker med fødselsnummer $fnr finnes ikke på treffet")
        }
        return Pair(arbeidsgiver, jobbsøkere)
    }

    private fun opprettStillingOgKandidatliste(
        treffId: TreffId,
        opprettFormidling: OpprettFormidlingDto,
        userToken: String
    ): OpprettFormidlingStillingRespons {
        val request = OpprettRekrutteringstreffFormidling(
            eierNavKontorEnhetId = opprettFormidling.eierNavKontorEnhetId,
            rekrutteringstreffId = treffId.somUuid,
            stilling = opprettFormidling.stilling,
        )
        return stillingKlient.opprettFormidlingStillingOgKandidatliste(request, userToken)
    }

    private fun leggKandidaterPåListenOgSendTilStatistikk(
        stillingId: OpprettFormidlingStillingRespons,
        jobbsøkere: List<Jobbsøker>
    ) {
//       TODO("Not yet implemented")
    }

    private fun lagreFormidlinger(
        rekrutteringstreffId: TreffId,
        jobbsøkere: List<Jobbsøker>,
        arbeidsgiver: Arbeidsgiver,
        stillingId: UUID,
    ): List<Formidling> {
        val formidlingIder = dataSource.executeInTransaction { connection ->
            jobbsøkere.map { jobbsøker ->
                formidlingRepository.opprett(
                    connection,
                    rekrutteringstreffId,
                    jobbsøker.personTreffId,
                    arbeidsgiver.arbeidsgiverTreffId,
                    stillingId
                )
            }
        }
        logger.info("Opprettet ${formidlingIder.size} formidlinger for treff ${rekrutteringstreffId} med arbeidsgiver ${arbeidsgiver.arbeidsgiverTreffId}")
        return formidlingIder.mapNotNull { formidlingRepository.hent(it) }
    }

    private fun endreJobbsøkerStatusOgLeggTilHendelser(formidlinger: List<Formidling>, navIdent: String) {
        formidlinger.forEach { formidling ->
            jobbsøkerService.registrerFåttJobb(formidling.jobbsøkerPersonTreffId, navIdent)
        }
    }

    fun hent(formidlingId: Long): Formidling? {
        return formidlingRepository.hent(formidlingId)
    }

    fun hent(treffId: TreffId, personTreffId: PersonTreffId, arbeidsgiverTreffId: ArbeidsgiverTreffId): Formidling? {
        return formidlingRepository.hent(treffId, personTreffId, arbeidsgiverTreffId)
    }

    fun slett(formidlingId: Long): Boolean {
        val slettet = dataSource.executeInTransaction { connection ->
            formidlingRepository.markerSlettet(connection, formidlingId)
        }
        if (slettet) {
            logger.info("Markert formidling $formidlingId som slettet")
        }
        return slettet
    }
}

class ArbeidsgiverIkkeFunnetException(message: String) : RuntimeException(message)
class JobbsøkerIkkeFunnetPåTreffException(message: String) : RuntimeException(message)

