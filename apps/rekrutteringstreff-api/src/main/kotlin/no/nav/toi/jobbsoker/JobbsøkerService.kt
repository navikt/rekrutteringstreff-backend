package no.nav.toi.jobbsoker

import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.SecureLog
import no.nav.toi.exception.JobbsøkerIkkeFunnetException
import no.nav.toi.exception.JobbsøkerIkkeSynligException
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerData
import no.nav.toi.jobbsoker.sok.*
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource

data class LeggTilJobbsøkereResultat(
    val antallLagtTil: Int,
)

class JobbsøkerService(
    private val dataSource: DataSource,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val jobbsøkerSokRepository: JobbsøkerSokRepository = JobbsøkerSokRepository(dataSource),
    private val jobbsøkerFormidlingSokRepository: JobbsøkerFormidlingSokRepository = JobbsøkerFormidlingSokRepository(dataSource),
    private val kandidatsøkKlient: KandidatsøkKlient? = null,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val secureLogger: Logger = SecureLog(logger)

    fun leggTilJobbsøkere(
        jobbsøkere: List<LeggTilJobbsøker>,
        treffId: TreffId,
        navIdent: String,
        lagtTilAvNavn: String? = null,
        innkommendeToken: String? = null,
    ): LeggTilJobbsøkereResultat {
        val eksisterendeJobbsøkere = hentJobbsøkere(treffId)
        val personTreffIdForGjenoppretting = hentSlettedeJobbsøkereUtenHendelser(treffId)
            .associate { it.fødselsnummer to it.personTreffId }
        val jobbsøkereSomSkalLagres = finnJobbsøkereSomSkalLagres(
            ønskedeJobbsøkere = jobbsøkere,
            eksisterendeJobbsøkere = eksisterendeJobbsøkere,
        )
        val berikedeJobbsøkerBatcher = jobbsøkereSomSkalLagres
            .chunked(MAKS_ANTALL_JOBBSØKERE_PER_BATCH)
            .map { batch -> berikJobbsøkerBatch(batch, innkommendeToken) }

        dataSource.executeInTransaction { connection ->
            berikedeJobbsøkerBatcher.forEach { batch ->
                leggTilJobbsøkerBatch(
                    connection = connection,
                    batch = batch,
                    personTreffIdForGjenoppretting = personTreffIdForGjenoppretting,
                    treffId = treffId,
                    navIdent = navIdent,
                    lagtTilAvNavn = lagtTilAvNavn,
                )
            }
        }

        return LeggTilJobbsøkereResultat(
            antallLagtTil = jobbsøkereSomSkalLagres.size,
        )
    }

    private fun berikJobbsøkerBatch(
        batch: List<LeggTilJobbsøker>,
        innkommendeToken: String?,
    ): List<LeggTilJobbsøker> {
        if (batch.isEmpty() || innkommendeToken == null) return batch
        val klient = kandidatsøkKlient?.takeIf { it.erKonfigurert() } ?: return batch

        val kandidatsøkdataPerFødselsnummer = klient.hentJobbsokerInfo(
            fødselsnumre = batch.map { it.fødselsnummer },
            innkommendeToken = innkommendeToken,
        )
        return batch.map { jobbsøker ->
            val kandidatsøkdata = kandidatsøkdataPerFødselsnummer[jobbsøker.fødselsnummer] ?: return@map jobbsøker
            jobbsøker.copy(
                kontor = kandidatsøkdata.kontor,
                veilederNavn = kandidatsøkdata.veilederNavn,
                veilederNavIdent = kandidatsøkdata.veilederNavIdent,
                alder = kandidatsøkdata.alder,
                innsatsgruppe = kandidatsøkdata.innsatsgruppe,
            )
        }
    }

    fun inviter(personTreffIds: List<PersonTreffId>, treffId: TreffId, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            personTreffIds.forEach { personTreffId ->
                // Sjekk om jobbsøker er synlig - hopp over usynlige jobbsøkere
                val erSynlig = jobbsøkerRepository.erSynlig(connection, personTreffId)
                if (erSynlig == false) {
                    secureLogger.warn("Forsøkte å invitere jobbsøker $personTreffId som ikke er synlig i rekrutteringstreff - hopper over")
                    return@forEach
                }

                val `jobbsøkerstatus` = jobbsøkerRepository.hentStatus(connection, personTreffId)
                if (`jobbsøkerstatus` != null && `jobbsøkerstatus` != JobbsøkerStatus.LAGT_TIL) {
                    logger.info("Jobbsøker $personTreffId har allerede status $`jobbsøkerstatus`, hopper over invitasjon")
                    return@forEach
                }

                jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                    connection,
                    JobbsøkerHendelsestype.INVITERT,
                    listOf(personTreffId),
                    navIdent
                )
                jobbsøkerRepository.endreStatus(connection, personTreffId, JobbsøkerStatus.INVITERT)
            }
        }
    }

    fun svarJaTilInvitasjon(fnr: Fødselsnummer, treffId: TreffId, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            val personTreffId = jobbsøkerRepository.hentPersonTreffId(connection, treffId, fnr)
                ?: throw JobbsøkerIkkeFunnetException("Jobbsøker finnes ikke for dette treffet.")

            // Sjekk om jobbsøker er synlig
            val erSynlig = jobbsøkerRepository.erSynlig(connection, personTreffId)
            if (erSynlig == false) {
                throw JobbsøkerIkkeSynligException("Jobbsøker er ikke lenger synlig og kan ikke svare på invitasjonen.")
            }

            val nåværendeStatus = jobbsøkerRepository.hentStatus(connection, personTreffId)
            if (nåværendeStatus == JobbsøkerStatus.SVART_JA) {
                logger.info("Jobbsøker har allerede svart JA, ignorerer duplikat kall")
                return@executeInTransaction
            }

            jobbsøkerRepository.leggTilHendelse(connection, personTreffId, JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON, AktørType.JOBBSØKER, navIdent)
            jobbsøkerRepository.endreStatus(connection, personTreffId, JobbsøkerStatus.SVART_JA)
        }
    }

    fun svarNeiTilInvitasjon(fnr: Fødselsnummer, treffId: TreffId, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            val personTreffId = jobbsøkerRepository.hentPersonTreffId(connection, treffId, fnr)
                ?: throw JobbsøkerIkkeFunnetException("Jobbsøker finnes ikke for dette treffet.")

            // Sjekk om jobbsøker er synlig
            val erSynlig = jobbsøkerRepository.erSynlig(connection, personTreffId)
            if (erSynlig == false) {
                throw JobbsøkerIkkeSynligException("Jobbsøker er ikke lenger synlig og kan ikke svare på invitasjonen.")
            }

            val nåværendeStatus = jobbsøkerRepository.hentStatus(connection, personTreffId)
            if (nåværendeStatus == JobbsøkerStatus.SVART_NEI) {
                logger.info("Jobbsøker har allerede svart NEI, ignorerer duplikat kall")
                return@executeInTransaction
            }

            jobbsøkerRepository.leggTilHendelse(connection, personTreffId, JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON, AktørType.JOBBSØKER, navIdent)
            jobbsøkerRepository.endreStatus(connection, personTreffId, JobbsøkerStatus.SVART_NEI)
        }
    }

    fun svarPåVegneAvJobbsøker(personTreffId: PersonTreffId, navIdent: String, svar: Boolean?) {
        dataSource.executeInTransaction { connection ->
            val erSynlig = jobbsøkerRepository.erSynlig(connection, personTreffId)
            if (erSynlig == false) {
                throw JobbsøkerIkkeSynligException("Jobbsøker er ikke lenger synlig og kan ikke svare på invitasjonen.")
            }

            val nyStatus = when (svar) {
                true -> JobbsøkerStatus.SVART_JA
                false -> JobbsøkerStatus.SVART_NEI
                null -> JobbsøkerStatus.INVITERT
            }

            val nåværendeStatus = jobbsøkerRepository.hentStatus(connection, personTreffId)
            if (nåværendeStatus == nyStatus) {
                logger.info("Jobbsøker har allerede status ${nyStatus}, ignorerer duplikat kall")
                return@executeInTransaction
            }

            val hendelsesType = when (svar) {
                true -> JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER
                false -> JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER
                null -> JobbsøkerHendelsestype.SVAR_FJERNET_AV_EIER
            }

            jobbsøkerRepository.leggTilHendelse(connection, personTreffId, hendelsesType, AktørType.ARRANGØR, navIdent)
            jobbsøkerRepository.endreStatus(connection, personTreffId, nyStatus)
        }
    }

    fun registrerFåttJobb(connection: Connection, personTreffId: PersonTreffId, navIdent: String) {
        val nåværendeStatus = jobbsøkerRepository.hentStatus(connection, personTreffId)
        if (nåværendeStatus == JobbsøkerStatus.FÅTT_JOBB) {
            logger.info("Jobbsøker har allerede fått jobb, ignorerer duplikat kall")
            return
        }
        jobbsøkerRepository.leggTilHendelse(connection, personTreffId, JobbsøkerHendelsestype.FÅTT_JOBB, AktørType.ARRANGØR, navIdent)
        jobbsøkerRepository.endreStatus(connection, personTreffId, JobbsøkerStatus.FÅTT_JOBB)
    }

    fun markerSlettet(personTreffId: PersonTreffId, treffId: TreffId, navIdent: String): MarkerSlettetResultat {
        val fødselsnummer = jobbsøkerRepository.hentFødselsnummer(personTreffId)
            ?: return MarkerSlettetResultat.IKKE_FUNNET

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
            ?: return MarkerSlettetResultat.IKKE_FUNNET

        if (jobbsøker.status != JobbsøkerStatus.LAGT_TIL) {
            return MarkerSlettetResultat.IKKE_TILLATT
        }

        dataSource.executeInTransaction { connection ->
            jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                connection,
                JobbsøkerHendelsestype.SLETTET,
                listOf(personTreffId),
                navIdent
            )
            jobbsøkerRepository.endreStatus(connection, personTreffId, JobbsøkerStatus.SLETTET)
        }

        logger.info("Slettet jobbsøker $personTreffId for treff $treffId")
        return MarkerSlettetResultat.OK
    }

    fun hentJobbsøkere(treffId: TreffId): List<Jobbsøker> {
        return jobbsøkerRepository.hentJobbsøkere(treffId)
    }

    fun hentSlettedeJobbsøkereUtenHendelser(treffId: TreffId): List<Jobbsøker> {
        return jobbsøkerRepository.hentSlettedeJobbsøkere(treffId)
    }

    fun hentJobbsøker(treffId: TreffId, fnr: Fødselsnummer): Jobbsøker? {
        return jobbsøkerRepository.hentJobbsøker(treffId, fnr)
    }

    fun hentJobbsøkerHendelser(treffId: TreffId): List<JobbsøkerHendelseMedJobbsøkerData> {
        return jobbsøkerRepository.hentJobbsøkerHendelser(treffId)
    }

    fun registrerAktivitetskortOpprettelseFeilet(fnr: Fødselsnummer, treffId: TreffId, endretAv: String) {
        logger.info("Skal oppdatere hendelse for aktivitetskortfeil for TreffId: $treffId")
        secureLogger.info("Henter jobbsøker persontreffid for treff: ${treffId.somString} og fødselsnummer: ${fnr.asString}")

        val personTreffId = jobbsøkerRepository.hentPersonTreffId(treffId, fnr)
        if (personTreffId == null) {
            logger.error("Fant ingen jobbsøker med treffId: ${treffId.somString} og fødselsnummer: (se securelog)")
            secureLogger.error("Fant ingen jobbsøker med treffId: ${treffId.somString} og fødselsnummer: ${fnr.asString}")
            return
        }

        dataSource.executeInTransaction { connection ->
            jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                connection,
                JobbsøkerHendelsestype.AKTIVITETSKORT_OPPRETTELSE_FEIL,
                listOf(personTreffId),
                endretAv,
                AktørType.ARRANGØR
            )
        }
        logger.info("Registrerte hendelse om at opprettelse av aktivitetskort feilet for rekrutteringstreffId: ${treffId.somString}")
    }

    fun registrerMinsideVarselSvar(fnr: Fødselsnummer, treffId: TreffId, opprettetAv: String, hendelseData: String) {
        logger.info("Skal oppdatere hendelse for minside varsel svar for TreffId: $treffId")
        secureLogger.info("Henter jobbsøker persontreffid for treff: ${treffId.somString} og fødselsnummer: ${fnr.asString}")

        val personTreffId = jobbsøkerRepository.hentPersonTreffId(treffId, fnr)
        if (personTreffId == null) {
            logger.error("Fant ingen jobbsøker med treffId: ${treffId.somString} for minside varsel svar (fødselsnummer i securelog)")
            secureLogger.error("Fant ingen jobbsøker med treffId: ${treffId.somString} og fødselsnummer: ${fnr.asString}")
            return
        }

        dataSource.executeInTransaction { connection ->
            jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                connection,
                JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE,
                listOf(personTreffId),
                opprettetAv,
                AktørType.SYSTEM,
                hendelseData
            )
        }
        logger.info("Registrerte hendelse MOTTATT_SVAR_FRA_MINSIDE for rekrutteringstreffId: ${treffId.somString}")
    }

    /**
     * Filtrerer jobbsøkere som har aktivt svar ja, basert på gjeldende status.
     * Dekker både svar fra jobbsøker selv og svar lagt inn av eier på vegne av jobbsøker.
     */
    fun finnJobbsøkereMedAktivtSvarJa(jobbsøkere: List<Jobbsøker>): List<Jobbsøker> {
        return jobbsøkere.filter { it.harAktivtSvarJa() }
    }

    /**
     * Filtrerer jobbsøkere som er invitert men ikke har svart, basert på gjeldende status.
     * Status overskrives av svar (også svar via eier), så `INVITERT` representerer
     * nettopp jobbsøkere som er invitert og ennå ikke har svart.
     */
    fun finnJobbsøkereSomIkkeSvart(jobbsøkere: List<Jobbsøker>): List<Jobbsøker> {
        return jobbsøkere.filter { it.status == JobbsøkerStatus.INVITERT }
    }

    fun skalVarslesOmEndringer(jobbsøker: Jobbsøker): Boolean =
        jobbsøker.harAktivtSvarJa()

    fun oppdaterSynlighetFraEvent(fodselsnummer: String, erSynlig: Boolean, meldingTidspunkt: Instant): Int {
        return dataSource.executeInTransaction { connection ->
            jobbsøkerRepository.oppdaterSynlighetFraEvent(connection, fodselsnummer, erSynlig, meldingTidspunkt)
        }
    }

    fun oppdaterSynlighetFraNeed(fodselsnummer: String, erSynlig: Boolean, meldingTidspunkt: Instant): Int {
        return dataSource.executeInTransaction { connection ->
            jobbsøkerRepository.oppdaterSynlighetFraNeed(connection, fodselsnummer, erSynlig, meldingTidspunkt)
        }
    }

    fun hentFødselsnumreUtenEvaluertSynlighet(): List<String> =
        jobbsøkerRepository.hentFødselsnumreUtenEvaluertSynlighet()

    fun søkJobbsøkere(treffId: TreffId, request: JobbsøkerSøkRequest): JobbsøkerSøkRespons =
        jobbsøkerSokRepository.sok(treffId, request)

    fun hentAlleJobbsøkereForFormidling(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
    ): JobbsøkerFormidlingRespons =
        jobbsøkerFormidlingSokRepository.hentAlleForFormidling(treffId, request)

    fun hentEgneJobbsøkereForFormidling(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): JobbsøkerFormidlingRespons =
        jobbsøkerFormidlingSokRepository.hentEgneForFormidling(
            treffId,
            request,
            veilederNavIdent,
            tilknyttedeEnheter,
        )

    private fun finnJobbsøkereSomSkalLagres(
        ønskedeJobbsøkere: List<LeggTilJobbsøker>,
        eksisterendeJobbsøkere: List<Jobbsøker>,
    ): List<LeggTilJobbsøker> {
        val eksisterendeFødselsnumre = eksisterendeJobbsøkere.map { it.fødselsnummer }.toSet()
        return ønskedeJobbsøkere
            .filterNot { it.fødselsnummer in eksisterendeFødselsnumre }
            .distinctBy { it.fødselsnummer }
    }

    private fun leggTilJobbsøkerBatch(
        connection: java.sql.Connection,
        batch: List<LeggTilJobbsøker>,
        personTreffIdForGjenoppretting: Map<Fødselsnummer, PersonTreffId>,
        treffId: TreffId,
        navIdent: String,
        lagtTilAvNavn: String?,
    ) {
        val personTreffIder = leggTilEllerGjenopprett(
            connection = connection,
            batch = batch,
            treffId = treffId,
            personTreffIdForGjenoppretting = personTreffIdForGjenoppretting,
        )
        if (personTreffIder.isNotEmpty()) {
            jobbsøkerRepository.leggTilOpprettetHendelser(
                connection = connection,
                personTreffIder = personTreffIder,
                opprettetAv = navIdent,
                tidspunkt = Instant.now(),
                lagtTilAvNavn = lagtTilAvNavn,
            )
        }
    }

    private fun leggTilEllerGjenopprett(
        connection: java.sql.Connection,
        batch: List<LeggTilJobbsøker>,
        treffId: TreffId,
        personTreffIdForGjenoppretting: Map<Fødselsnummer, PersonTreffId>,
    ): List<PersonTreffId> {
        val (jobbsøkereSomSkalGjenopprettes, nyeJobbsøkere) = batch
            .partition { it.fødselsnummer in personTreffIdForGjenoppretting }
        val opprettedePersonTreffIder = if (nyeJobbsøkere.isEmpty()) {
            emptyList()
        } else {
            jobbsøkerRepository.leggTil(connection, nyeJobbsøkere, treffId).map { it.personTreffId }
        }
        val jobbsøkereForGjenoppretting = jobbsøkereSomSkalGjenopprettes.associateBy(
            keySelector = { personTreffIdForGjenoppretting.getValue(it.fødselsnummer) },
            valueTransform = { it },
        )
        val gjenopprettedePersonTreffIder = if (jobbsøkereForGjenoppretting.isEmpty()) {
            emptyList()
        } else {
            jobbsøkerRepository.gjenopprett(connection, jobbsøkereForGjenoppretting)
            jobbsøkereForGjenoppretting.keys.toList()
        }
        return opprettedePersonTreffIder + gjenopprettedePersonTreffIder
    }
}

enum class MarkerSlettetResultat {
    OK,
    IKKE_FUNNET,
    IKKE_TILLATT
}
