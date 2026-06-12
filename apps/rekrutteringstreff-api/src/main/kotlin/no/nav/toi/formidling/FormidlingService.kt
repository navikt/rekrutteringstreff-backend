package no.nav.toi.formidling

import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.ArbeidsgiverService
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.executeInTransaction
import no.nav.toi.formidling.dto.FormidlingDto
import no.nav.toi.formidling.dto.OpprettFormidlingDto
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

class FormidlingService(
    private val dataSource: DataSource,
    private val formidlingRepository: FormidlingRepository,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val jobbsøkerService: JobbsøkerService,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val stillingKlient: StillingKlient,
    private val kandidatKlient: KandidatKlient,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun hentAlleFormidlingerForTreff(treffId: TreffId): List<FormidlingDto> =
        formidlingRepository.hentAlleForTreff(treffId)

    fun hentEgneFormidlingerForTreff(
        treffId: TreffId,
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): List<FormidlingDto> =
        formidlingRepository.hentEgneForTreff(treffId, veilederNavIdent, tilknyttedeEnheter)

    fun opprettFormidling(
        treffId: TreffId,
        opprettFormidling: OpprettFormidlingDto,
        navIdent: String,
        userToken: String
    ): List<Formidling> {
        logger.info("Prøver å oppprette ${opprettFormidling.fødselsnumre.size} formidlinger for rekrutteringstreff $treffId og orgnr ${opprettFormidling.orgnr}")

        val (arbeidsgiver, jobbsøkere) = validerOgHentArbeidsgivereOgJobbsøkere(treffId, opprettFormidling)
        val (formidlingerUtenUtfall, jobbsøkereUtenFormidling) =
            kategoriserEksisterendeFormidlinger(treffId, arbeidsgiver, jobbsøkere)

        if (formidlingerUtenUtfall.isEmpty() && jobbsøkereUtenFormidling.isEmpty()) {
            logger.warn("Alle formidlinger var allerede opprettet for treff $treffId og orgnr ${arbeidsgiver.orgnr}. Hopper over kall.")
            return emptyList()
        }

        val nyeFormidlinger = if (jobbsøkereUtenFormidling.isNotEmpty()) {
            val stillingOgKandidatliste = opprettStillingOgKandidatliste(treffId, opprettFormidling, userToken)
            lagreFormidlinger(
                treffId,
                jobbsøkereUtenFormidling,
                arbeidsgiver,
                stillingOgKandidatliste.stillingsId,
                stillingOgKandidatliste.kandidatlisteId,
            )
        } else {
            emptyList()
        }

        val formidlinger = formidlingerUtenUtfall + nyeFormidlinger

        formidlinger.forEach { formidling ->
            val jobbsøker = jobbsøkere.find { it.personTreffId == formidling.jobbsøkerPersonTreffId } ?: error("Fant ikke jobbsøker i listen")
            if (formidling.kandidatlisteId == null) {
                error("KandidatlisteId mangler for formidling for stilling ${formidling.stillingId}")
            }
            leggKandidatPåListen(formidling.stillingId, formidling.kandidatlisteId, jobbsøker, opprettFormidling.eierNavKontorEnhetId, userToken)
            dataSource.executeInTransaction { connection ->
                endreJobbsøkerStatusOgLeggTilHendelser(connection, formidling.jobbsøkerPersonTreffId, navIdent)
                formidlingRepository.oppdaterUtfallSendtTidspunkt(connection, formidling.formidlingId)
            }
        }
        return formidlinger
    }

    /**
     * Kategoriserer jobbsøkerne mot eksisterende formidlinger (gjort slik at frontend kan prøve på nytt ved feil
     * uten å sende utfallet to ganger):
     * - Eksisterende formidlinger uten utfallSendtTidspunkt gjenbrukes.
     * - Eksisterende formidlinger med utfallSendtTidspunkt ignoreres (allerede sendt).
     * - Jobbsøkere uten formidling skal få opprettet nye formidlinger.
     */
    private fun kategoriserEksisterendeFormidlinger(
        treffId: TreffId,
        arbeidsgiver: Arbeidsgiver,
        jobbsøkere: List<Jobbsøker>,
    ): FormidlingsKandidater {
        val eksisterendeFormidlinger = jobbsøkere.mapNotNull { jobbsøker ->
            formidlingRepository.hent(treffId, jobbsøker.personTreffId, arbeidsgiver.arbeidsgiverTreffId)
        }
        val medUtfall = eksisterendeFormidlinger.filter { it.utfallSendtTidspunkt != null }
        val utenUtfall = eksisterendeFormidlinger.filter { it.utfallSendtTidspunkt == null }

        val personTreffIderMedFormidling = eksisterendeFormidlinger.map { it.jobbsøkerPersonTreffId }.toSet()
        val jobbsøkereUtenFormidling = jobbsøkere.filter { it.personTreffId !in personTreffIderMedFormidling }

        if (medUtfall.isNotEmpty()) {
            logger.warn(
                "Fant ${medUtfall.size} eksisterende formidlinger med utfallSendtTidspunkt for treff $treffId og orgnr ${arbeidsgiver.orgnr}. Disse blir ikke behandlet på nytt.",
            )
        }

        return FormidlingsKandidater(
            formidlingerUtenUtfall = utenUtfall,
            jobbsøkereUtenFormidling = jobbsøkereUtenFormidling,
        )
    }

    private fun validerOgHentArbeidsgivereOgJobbsøkere(treffId: TreffId, opprettFormidling: OpprettFormidlingDto): Pair<Arbeidsgiver, List<Jobbsøker>> {
        rekrutteringstreffRepository.hent(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId finnes ikke")

        val arbeidsgiver = arbeidsgiverService.hentArbeidsgiver(treffId, Orgnr(opprettFormidling.orgnr))
            ?: throw ArbeidsgiverIkkeFunnetException("Arbeidsgiver med orgnr ${opprettFormidling.orgnr} finnes ikke på treffet")

        val jobbsøkere = opprettFormidling.fødselsnumre.map { fnr ->
            jobbsøkerService.hentJobbsøker(treffId, Fødselsnummer(fnr), inkluderUsynlige = true)
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

    private fun leggKandidatPåListen(
        stillingId: UUID,
        kandidatlisteId: UUID,
        jobbsøker: Jobbsøker,
        navKontorEnhetId: String,
        userToken: String
    ) {
        kandidatKlient.leggTilPersonerPåKandidatliste(
            kandidatlisteId = kandidatlisteId,
            stillingId = stillingId,
            jobbsøker = jobbsøker,
            navKontorVeileder = navKontorEnhetId,
            userToken = userToken
        )
    }

    private fun lagreFormidlinger(
        rekrutteringstreffId: TreffId,
        jobbsøkere: List<Jobbsøker>,
        arbeidsgiver: Arbeidsgiver,
        stillingId: UUID,
        kandidatlisteId: UUID?,
    ): List<Formidling> {
        val formidlingIder = dataSource.executeInTransaction { connection ->
            jobbsøkere.map { jobbsøker ->
                formidlingRepository.opprett(
                    connection,
                    rekrutteringstreffId,
                    jobbsøker.personTreffId,
                    arbeidsgiver.arbeidsgiverTreffId,
                    stillingId,
                    kandidatlisteId,
                    null,
                )
            }
        }
        logger.info("Opprettet ${formidlingIder.size} formidlinger for treff ${rekrutteringstreffId} med arbeidsgiver ${arbeidsgiver.arbeidsgiverTreffId}")
        return formidlingIder.mapNotNull { formidlingRepository.hent(it) }
    }

    private fun endreJobbsøkerStatusOgLeggTilHendelser(
        connection: Connection,
        jobbsøkerPersonTreffId: PersonTreffId,
        navIdent: String
    ) {
        jobbsøkerService.registrerFåttJobb(connection, jobbsøkerPersonTreffId, navIdent)
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

private data class FormidlingsKandidater(
    val formidlingerUtenUtfall: List<Formidling>,
    val jobbsøkereUtenFormidling: List<Jobbsøker>,
)

