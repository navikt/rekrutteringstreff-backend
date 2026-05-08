package no.nav.toi.jobbsoker

import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.SecureLog
import no.nav.toi.exception.JobbsøkerIkkeFunnetException
import no.nav.toi.exception.JobbsøkerIkkeSynligException
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerData
import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import no.nav.toi.jobbsoker.sok.JobbsøkerSøkRequest
import no.nav.toi.jobbsoker.sok.JobbsøkerSøkRespons
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.sql.DataSource

data class LeggTilJobbsøkereResultat(
    val antallLagtTil: Int,
)

class JobbsøkerService(
    private val dataSource: DataSource,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val jobbsøkerSokRepository: JobbsøkerSokRepository,
) {
    constructor(dataSource: DataSource, jobbsøkerRepository: JobbsøkerRepository) : this(
        dataSource, jobbsøkerRepository, JobbsøkerSokRepository(dataSource)
    )
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val secureLogger: Logger = SecureLog(logger)

    fun leggTilJobbsøkere(
        jobbsøkere: List<LeggTilJobbsøker>,
        treffId: TreffId,
        navIdent: String,
        lagtTilAvNavn: String? = null,
    ): LeggTilJobbsøkereResultat {
        val eksisterendeJobbsøkere = hentJobbsøkere(treffId)
        val slettedeJobbsøkere = hentSlettedeJobbsøkereUtenHendelser(treffId)
        val nyeJobbsøkere = finnNyeJobbsøkere(jobbsøkere, eksisterendeJobbsøkere, slettedeJobbsøkere)
        val gjenopprettedeJobbsøkere = finnGjenopprettedeJobbsøkere(jobbsøkere, slettedeJobbsøkere)

        dataSource.executeInTransaction { connection ->
            opprettNyeJobbsøkere(connection, nyeJobbsøkere, treffId, navIdent, lagtTilAvNavn)
            gjenopprettJobbsøkere(connection, gjenopprettedeJobbsøkere, treffId, navIdent, lagtTilAvNavn)
        }

        return LeggTilJobbsøkereResultat(
            antallLagtTil = nyeJobbsøkere.size + gjenopprettedeJobbsøkere.size,
        )
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

    private fun finnNyeJobbsøkere(
        ønskedeJobbsøkere: List<LeggTilJobbsøker>,
        eksisterendeJobbsøkere: List<Jobbsøker>,
        slettedeJobbsøkere: List<Jobbsøker>,
    ): List<LeggTilJobbsøker> {
        val eksisterendeFødselsnumre = eksisterendeJobbsøkere.map { it.fødselsnummer }.toSet()
        val slettedeFødselsnumre = slettedeJobbsøkere.map { it.fødselsnummer }.toSet()
        return ønskedeJobbsøkere.filterNot {
            it.fødselsnummer in eksisterendeFødselsnumre || it.fødselsnummer in slettedeFødselsnumre
        }
    }

    private fun finnGjenopprettedeJobbsøkere(
        ønskedeJobbsøkere: List<LeggTilJobbsøker>,
        slettedeJobbsøkere: List<Jobbsøker>,
    ): List<PersonTreffId> {
        val ønskedeFødselsnumre = ønskedeJobbsøkere.map { it.fødselsnummer }.toSet()
        return slettedeJobbsøkere
            .filter { it.fødselsnummer in ønskedeFødselsnumre }
            .map { it.personTreffId }
    }

    private fun opprettNyeJobbsøkere(
        connection: java.sql.Connection,
        jobbsøkere: List<LeggTilJobbsøker>,
        treffId: TreffId,
        navIdent: String,
        lagtTilAvNavn: String?,
    ) {
        if (jobbsøkere.isEmpty()) return

        log.info("Legger til ${jobbsøkere.size} nye jobbsøkere for treff $treffId")
        val now = Instant.now()
        val opprettedeJobbsøkere = jobbsøkerRepository.leggTil(connection, jobbsøkere, treffId, navIdent, now)
        val personTreffIder = opprettedeJobbsøkere.map { it.personTreffId }

        jobbsøkerRepository.leggTilOpprettetHendelser(connection, personTreffIder, navIdent, now, lagtTilAvNavn)
    }

    private fun gjenopprettJobbsøkere(
        connection: java.sql.Connection,
        personTreffIder: List<PersonTreffId>,
        treffId: TreffId,
        navIdent: String,
        lagtTilAvNavn: String?,
    ) {
        if (personTreffIder.isEmpty()) return

        log.info("Gjenoppretter ${personTreffIder.size} jobbsøkere for treff $treffId som tidligere har vært slettet")
        jobbsøkerRepository.endreStatus(
            connection = connection,
            personTreffIder = personTreffIder,
            jobbsøkerStatus = JobbsøkerStatus.LAGT_TIL,
        )
        jobbsøkerRepository.leggTilOpprettetHendelser(
            connection = connection,
            personTreffIder = personTreffIder,
            opprettetAv = navIdent,
            tidspunkt = Instant.now(),
            lagtTilAvNavn = lagtTilAvNavn,
        )
    }
}

enum class MarkerSlettetResultat {
    OK,
    IKKE_FUNNET,
    IKKE_TILLATT
}
