package no.nav.toi.jobbsoker

import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerData
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class JobbsøkerService(
    private val dataSource: DataSource,
    private val jobbsøkerRepository: JobbsøkerRepository
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun leggTilJobbsøkere(jobbsøkere: List<LeggTilJobbsøker>, treffId: TreffId, navIdent: String) {
        val eksisterendeJobbsøkere = hentJobbsøkere(treffId)
        val nyeJobbsøkere = jobbsøkere.filterNot { eksisterendeJobbsøkere.any { jobbsøker -> jobbsøker.fødselsnummer == it.fødselsnummer } }
        dataSource.executeInTransaction { connection ->
            val personTreffIder = jobbsøkerRepository.leggTil(connection, nyeJobbsøkere, treffId)
            jobbsøkerRepository.leggTilOpprettetHendelser(connection, personTreffIder, navIdent)
        }
    }

    fun inviter(personTreffIds: List<PersonTreffId>, treffId: TreffId, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            personTreffIds.forEach { personTreffId ->
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
                ?: throw IllegalStateException("Jobbsøker finnes ikke for dette treffet.")
            jobbsøkerRepository.leggTilHendelse(connection, personTreffId, JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON, AktørType.JOBBSØKER, navIdent)
            jobbsøkerRepository.endreStatus(connection, personTreffId, JobbsøkerStatus.SVART_JA)
        }
    }

    fun svarNeiTilInvitasjon(fnr: Fødselsnummer, treffId: TreffId, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            val personTreffId = jobbsøkerRepository.hentPersonTreffId(connection, treffId, fnr)
                ?: throw IllegalStateException("Jobbsøker finnes ikke for dette treffet.")
            jobbsøkerRepository.leggTilHendelse(connection, personTreffId, JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON, AktørType.JOBBSØKER, navIdent)
            jobbsøkerRepository.endreStatus(connection, personTreffId, JobbsøkerStatus.SVART_NEI)
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

    fun hentJobbsøker(treffId: TreffId, fnr: Fødselsnummer): Jobbsøker? {
        return jobbsøkerRepository.hentJobbsøker(treffId, fnr)
    }

    fun hentJobbsøkerHendelser(treffId: TreffId): List<JobbsøkerHendelseMedJobbsøkerData> {
        return jobbsøkerRepository.hentJobbsøkerHendelser(treffId)
    }

    fun registrerAktivitetskortOpprettelseFeilet(fnr: Fødselsnummer, treffId: TreffId, endretAv: String) {
        logger.info("Skal oppdatere hendelse for aktivitetskortfeil for TreffId: $treffId")
        secure(logger).info("Henter jobbsøker persontreffid for treff: ${treffId.somString} og fødselsnummer: ${fnr.asString}")

        val personTreffId = jobbsøkerRepository.hentPersonTreffId(treffId, fnr)
        if (personTreffId == null) {
            logger.error("Fant ingen jobbsøker med treffId: ${treffId.somString} og fødselsnummer: (se securelog)")
            secure(logger).error("Fant ingen jobbsøker med treffId: ${treffId.somString} og fødselsnummer: ${fnr.asString}")
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
        secure(logger).info("Henter jobbsøker persontreffid for treff: ${treffId.somString} og fødselsnummer: ${fnr.asString}")

        val personTreffId = jobbsøkerRepository.hentPersonTreffId(treffId, fnr)
        if (personTreffId == null) {
            logger.error("Fant ingen jobbsøker med treffId: ${treffId.somString} for minside varsel svar (fødselsnummer i securelog)")
            secure(logger).error("Fant ingen jobbsøker med treffId: ${treffId.somString} og fødselsnummer: ${fnr.asString}")
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
     * Filtrerer jobbsøkere som har aktivt svar ja (dvs. har svart ja og ikke svart nei etterpå)
     */
    fun finnJobbsøkereMedAktivtSvarJa(jobbsøkere: List<Jobbsøker>): List<Jobbsøker> {
        return jobbsøkere.filter { jobbsøker ->
            val hendelsestyper = jobbsøker.hendelser.map { it.hendelsestype }
            // Hendelser er sortert DESC (nyeste først), så indexOf finner den nyeste
            val nyesteJaIndex = hendelsestyper.indexOf(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON)
            val nyesteNeiIndex = hendelsestyper.indexOf(JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON)

            // Har svart ja, og ja er nyere enn eventuell nei (lavere index = nyere)
            nyesteJaIndex != -1 && (nyesteNeiIndex == -1 || nyesteJaIndex < nyesteNeiIndex)
        }
    }

    /**
     * Filtrerer jobbsøkere som har blitt invitert men ikke har svart
     */
    fun finnJobbsøkereSomIkkeSvart(jobbsøkere: List<Jobbsøker>): List<Jobbsøker> {
        return jobbsøkere.filter { jobbsøker ->
            val hendelsestyper = jobbsøker.hendelser.map { it.hendelsestype }.toSet()

            // Har INVITERT, men ikke svart ja eller nei
            hendelsestyper.contains(JobbsøkerHendelsestype.INVITERT) &&
            !hendelsestyper.contains(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON) &&
            !hendelsestyper.contains(JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON)
        }
    }

    /**
     * Avgjør om en jobbsøker skal varsles om endringer basert på hendelseshistorikk.
     * Varsler kun hvis siste relevante hendelse er INVITERT eller SVART_JA_TIL_INVITASJON.
     */
    fun skalVarslesOmEndringer(hendelser: List<JobbsøkerHendelse>): Boolean {
        if (hendelser.isEmpty()) return false

        // Filtrer kun invitasjons- og svar-hendelser
        val relevanteHendelser = hendelser.filter {
            it.hendelsestype in setOf(
                JobbsøkerHendelsestype.INVITERT,
                JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
                JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON
            )
        }.sortedByDescending { it.tidspunkt }

        if (relevanteHendelser.isEmpty()) return false

        // Ta den nyeste hendelsen
        val sisteRelevanteHendelse = relevanteHendelser.first()

        // Varsle kun hvis siste relevante hendelse er INVITERT eller SVART_JA_TIL_INVITASJON
        return sisteRelevanteHendelse.hendelsestype in setOf(
            JobbsøkerHendelsestype.INVITERT,
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON
        )
    }
}

enum class MarkerSlettetResultat {
    OK,
    IKKE_FUNNET,
    IKKE_TILLATT
}
