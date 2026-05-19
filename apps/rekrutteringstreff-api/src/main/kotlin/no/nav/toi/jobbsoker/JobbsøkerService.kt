package no.nav.toi.jobbsoker

import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.SecureLog
import no.nav.toi.exception.JobbsøkerIkkeFunnetException
import no.nav.toi.exception.JobbsøkerIkkeSynligException
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerData
import no.nav.toi.jobbsoker.sok.JobbsøkerFormidlingRepository
import no.nav.toi.jobbsoker.sok.JobbsøkerFormidlingRequest
import no.nav.toi.jobbsoker.sok.JobbsøkerFormidlingRespons
import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import no.nav.toi.jobbsoker.sok.JobbsøkerSøkRequest
import no.nav.toi.jobbsoker.sok.JobbsøkerSøkRespons
import no.nav.toi.kandidatsok.JobbsokerInfo
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.sql.DataSource

data class LeggTilJobbsøkereResultat(
    val antallLagtTil: Int,
)

private const val MAKS_ANTALL_JOBBSØKERE_PER_BATCH = 500

class JobbsøkerService(
    private val dataSource: DataSource,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val jobbsøkerSokRepository: JobbsøkerSokRepository,
    private val jobbsøkerFormidlingRepository: JobbsøkerFormidlingRepository =
        JobbsøkerFormidlingRepository(dataSource),
    private val kandidatsøkKlient: KandidatsøkKlient? = null,
) {
    constructor(dataSource: DataSource, jobbsøkerRepository: JobbsøkerRepository) : this(
        dataSource,
        jobbsøkerRepository,
        JobbsøkerSokRepository(dataSource),
        JobbsøkerFormidlingRepository(dataSource),
        null,
    )
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
        val jobbsøkereSomSkalLagres:List<LeggTilJobbsøker>  = finnJobbsøkereSomSkalLagres(
            ønskedeJobbsøkere = jobbsøkere,
            eksisterendeJobbsøkere = eksisterendeJobbsøkere,
            personTreffIdForGjenoppretting = personTreffIdForGjenoppretting,
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
        val kandidatsøkdataPerFødselsnummer = hentKandidatsøkdataPerFødselsnummer(batch, innkommendeToken)
        if (kandidatsøkdataPerFødselsnummer.isEmpty()) return batch

        return batch.map { jobbsøker ->
            val kandidatsøkdata = kandidatsøkdataPerFødselsnummer[jobbsøker.fødselsnummer] ?: JobbsokerInfo.tom
            berikJobbsøker(jobbsøker, kandidatsøkdata)
        }
    }

    private fun hentKandidatsøkdataPerFødselsnummer(
        batch: List<LeggTilJobbsøker>,
        innkommendeToken: String?,
    ): Map<Fødselsnummer, JobbsokerInfo> {
        if (batch.isEmpty()) return emptyMap()

        val konfigurertKandidatsøkKlient = kandidatsøkKlient?.takeIf { it.erKonfigurert() } ?: return emptyMap()

        val token = innkommendeToken ?: error("Innkommende token mangler for berikning av jobbsøkere")
        return konfigurertKandidatsøkKlient.hentJobbsokerInfo(
            fødselsnumre = batch.map { it.fødselsnummer }.distinct(),
            innkommendeToken = token,
        )
    }

    private fun berikJobbsøker(jobbsøker: LeggTilJobbsøker, kandidatsøkdata: JobbsokerInfo): LeggTilJobbsøker =
        jobbsøker.copy(
            navkontor = kandidatsøkdata.navkontor ?: jobbsøker.navkontor,
            veilederNavn = kandidatsøkdata.veilederNavn ?: jobbsøker.veilederNavn,
            veilederNavIdent = kandidatsøkdata.veilederNavIdent ?: jobbsøker.veilederNavIdent,
            alder = kandidatsøkdata.alder ?: jobbsøker.alder,
            innsatsgruppe = kandidatsøkdata.innsatsgruppe ?: jobbsøker.innsatsgruppe,
        )

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
        return jobbsøkere.filter { it.status == JobbsøkerStatus.SVART_JA }
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
        jobbsøker.status == JobbsøkerStatus.SVART_JA

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

    fun hentJobbsøkereForFormidling(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
        kunForVeilederNavIdent: String? = null,
    ): JobbsøkerFormidlingRespons =
        jobbsøkerFormidlingRepository.hentForFormidling(treffId, request, kunForVeilederNavIdent)

    private fun finnJobbsøkereSomSkalLagres(
        ønskedeJobbsøkere: List<LeggTilJobbsøker>,
        eksisterendeJobbsøkere: List<Jobbsøker>,
        personTreffIdForGjenoppretting: Map<Fødselsnummer, PersonTreffId>,
    ): List<LeggTilJobbsøker> {
        val eksisterendeFødselsnumre = eksisterendeJobbsøkere.map { it.fødselsnummer }.toSet()
        val ønskedeJobbsøkereUtenEksisterende = ønskedeJobbsøkere.filterNot { it.fødselsnummer in eksisterendeFødselsnumre }
        val førsteGjenopprettingsindekser = ønskedeJobbsøkereUtenEksisterende
            .withIndex()
            .filter { (_, ønsketJobbsøker) ->
                ønsketJobbsøker.fødselsnummer in personTreffIdForGjenoppretting
            }
            .distinctBy { (_, ønsketJobbsøker) -> ønsketJobbsøker.fødselsnummer }
            .map { it.index }
            .toSet()

        return ønskedeJobbsøkereUtenEksisterende.withIndex().mapNotNull { (index, ønsketJobbsøker) ->
            val erTidligereSlettet = ønsketJobbsøker.fødselsnummer in personTreffIdForGjenoppretting
            if (erTidligereSlettet && index !in førsteGjenopprettingsindekser) {
                return@mapNotNull null
            }

            ønsketJobbsøker
        }
    }

    private fun leggTilJobbsøkerBatch(
        connection: java.sql.Connection,
        batch: List<LeggTilJobbsøker>,
        personTreffIdForGjenoppretting: Map<Fødselsnummer, PersonTreffId>,
        treffId: TreffId,
        navIdent: String,
        lagtTilAvNavn: String?,
    ) {
        val nyeJobbsøkere = batch
            .filterNot { it.fødselsnummer in personTreffIdForGjenoppretting }
        val gjenopprettedeJobbsøkere = batch.mapNotNull { jobbsøker ->
            personTreffIdForGjenoppretting[jobbsøker.fødselsnummer]?.let { personTreffId ->
                JobbsøkerRepository.GjenopprettetJobbsøker(
                    personTreffId = personTreffId,
                    jobbsøker = jobbsøker,
                )
            }
        }

        val opprettedePersonTreffIder = opprettNyeJobbsøkere(connection, nyeJobbsøkere, treffId)
        val gjenopprettedePersonTreffIder = gjenopprettJobbsøkere(connection, gjenopprettedeJobbsøkere, treffId)
        val personTreffIder = opprettedePersonTreffIder + gjenopprettedePersonTreffIder
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

    private fun opprettNyeJobbsøkere(
        connection: java.sql.Connection,
        jobbsøkere: List<LeggTilJobbsøker>,
        treffId: TreffId,
    ): List<PersonTreffId> {
        if (jobbsøkere.isEmpty()) return emptyList()

        log.info("Legger til ${jobbsøkere.size} nye jobbsøkere for treff $treffId")
        val opprettedeJobbsøkere = jobbsøkerRepository.leggTil(
            connection = connection,
            jobbsøkere = jobbsøkere,
            treff = treffId,
        )
        return opprettedeJobbsøkere.map { it.personTreffId }
    }

    private fun gjenopprettJobbsøkere(
        connection: java.sql.Connection,
        jobbsøkere: List<JobbsøkerRepository.GjenopprettetJobbsøker>,
        treffId: TreffId,
    ): List<PersonTreffId> {
        if (jobbsøkere.isEmpty()) return emptyList()

        log.info("Gjenoppretter ${jobbsøkere.size} jobbsøkere for treff $treffId som tidligere har vært slettet")
        val personTreffIder = jobbsøkere.map { it.personTreffId }
        jobbsøkerRepository.gjenopprett(connection, jobbsøkere)
        return personTreffIder
    }
}

enum class MarkerSlettetResultat {
    OK,
    IKKE_FUNNET,
    IKKE_TILLATT
}
