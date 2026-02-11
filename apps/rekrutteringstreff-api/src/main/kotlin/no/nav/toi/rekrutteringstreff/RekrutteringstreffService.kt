package no.nav.toi.rekrutteringstreff

import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.exception.UlovligOppdateringException
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import java.util.ArrayList
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.dto.FellesHendelseOutboundDto
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import org.slf4j.Logger
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.sql.DataSource

class RekrutteringstreffService(
    private val dataSource: DataSource,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository,
    private val jobbsøkerService: JobbsøkerService
) {
    private val logger: Logger = log

    fun avlys(treffId: TreffId, avlystAv: String) {
        val treff = rekrutteringstreffRepository.hent(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")

        if (treff.status == RekrutteringstreffStatus.FULLFØRT) {
            logger.warn("Forsøk på å avlyse fullført rekrutteringstreff. treffId: $treffId")
            throw UlovligOppdateringException("Kan ikke avlyse rekrutteringstreff som allerede er fullført")
        }

        if (treff.status == RekrutteringstreffStatus.AVLYST) {
            logger.warn("Forsøk på å avlyse allerede avlyst rekrutteringstreff. treffId: $treffId")
            throw UlovligOppdateringException("Rekrutteringstreff er allerede avlyst")
        }

        leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
            treffId,
            avlystAv,
            RekrutteringstreffHendelsestype.AVLYST,
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST,
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST,
            RekrutteringstreffStatus.AVLYST,
        )
    }

    fun publiser(treffId: TreffId, navIdent: String) {
        val treff = rekrutteringstreffRepository.hent(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")

        if (treff.status != RekrutteringstreffStatus.UTKAST) {
            logger.warn("Forsøk på å publisere rekrutteringstreff som ikke er utkast. treffId: $treffId status: ${treff.status}")
            throw UlovligOppdateringException("Kan kun publisere rekrutteringstreff som er i UTKAST status")
        }

        dataSource.executeInTransaction { connection ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.PUBLISERT, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.PUBLISERT)
        }
    }

    fun fullfør(treffId: TreffId, fullfortAv: String) {
        val treff = rekrutteringstreffRepository.hent(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")

        if (treff.status != RekrutteringstreffStatus.PUBLISERT) {
            logger.warn("Forsøk på å fullføre rekrutteringstreff som ikke er publisert. treffId: $treffId status: ${treff.status}")
            throw UlovligOppdateringException("Kan kun fullføre rekrutteringstreff som er i PUBLISERT status")
        }

        if (treff.tilTid == null || treff.tilTid.isAfter(ZonedDateTime.now(ZoneId.of("Europe/Oslo")))) {
            logger.warn("Forsøk på å fullføre rekrutteringstreff som fortsatt er i gang. treffId: $treffId")
            throw UlovligOppdateringException("Rekrutteringstreff med id $treffId er fremdeles i gang og kan ikke fullføres")
        }

        leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
            treffId,
            fullfortAv,
            RekrutteringstreffHendelsestype.FULLFØRT,
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT,
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT,
            RekrutteringstreffStatus.FULLFØRT
        )
    }

    fun kanSletteJobbtreff(treffId: TreffId, status: RekrutteringstreffStatus): Boolean {
        if (status != RekrutteringstreffStatus.UTKAST) {
            return false
        }
        val jobbsøkere = jobbsøkerRepository.hentJobbsøkere(treffId)
        return jobbsøkere.isEmpty()
    }

    /**
     * Markerer et rekrutteringstreff og tilhørende arbeidsgivere som slettet (soft-delete).
     * Kan kun gjøres på treff i UTKAST-status uten jobbsøkere.
     */
    fun markerSlettet(treffId: TreffId, navIdent: String) {
        val status = rekrutteringstreffRepository.hent(treffId)?.status ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")
        if (kanSletteJobbtreff(treffId, status).not()) {
            throw UlovligOppdateringException("Kan ikke slette treff med id $treffId")
        }
        dataSource.executeInTransaction { connection ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.SLETTET, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.SLETTET)

            val arbeidsgivere = arbeidsgiverRepository.hentArbeidsgivere(treffId)
            arbeidsgivere.forEach { arbeidsgiver ->
                val arbeidsgiverTreffId = ArbeidsgiverTreffId(arbeidsgiver.arbeidsgiverTreffId.somUuid)
                arbeidsgiverRepository.markerSlettet(connection, arbeidsgiver.arbeidsgiverTreffId.somUuid)
                arbeidsgiverRepository.leggTilHendelse(connection, arbeidsgiverTreffId, ArbeidsgiverHendelsestype.SLETTET, AktørType.ARRANGØR, navIdent)
            }
        }
    }

    fun hentAlleRekrutteringstreff(): List<RekrutteringstreffDto> {
        val alleRekrutteringstreff = rekrutteringstreffRepository.hentAlleSomIkkeErSlettet()
        return tilDtoListeMedAntallArbeidsgivereOgJobbsøkere(alleRekrutteringstreff)
    }

    fun hentAlleRekrutteringstreffForEttKontor(kontorId: String): List<RekrutteringstreffDto> {
        val alleRekrutteringstreffForKontor = rekrutteringstreffRepository.hentAlleForEttKontorSomIkkeErSlettet(kontorId)
        return tilDtoListeMedAntallArbeidsgivereOgJobbsøkere(alleRekrutteringstreffForKontor)
    }

    private fun tilDtoListeMedAntallArbeidsgivereOgJobbsøkere(rekrutteringstreffListe: List<Rekrutteringstreff>): List<RekrutteringstreffDto> {
        val rekrutteringstreffDto: ArrayList<RekrutteringstreffDto> = ArrayList<RekrutteringstreffDto>()
        rekrutteringstreffListe.forEach {
            val antallArbeidsgivere = arbeidsgiverRepository.hentAntallArbeidsgivere(it.id)
            val antallJobbsøkere = jobbsøkerRepository.hentAntallJobbsøkere(it.id)
            rekrutteringstreffDto.add(it.tilRekrutteringstreffDto(antallArbeidsgivere, antallJobbsøkere))
        }
        return rekrutteringstreffDto
    }

    fun hentRekrutteringstreff(treffId: TreffId): RekrutteringstreffDto? {
        val rekrutteringstreff = rekrutteringstreffRepository.hent(treffId)
        if (rekrutteringstreff == null) {
            logger.info("Fant ikke rekrutteringstreff med id: $treffId")
            return null
        }
        val antallArbeidsgivere = arbeidsgiverRepository.hentAntallArbeidsgivere(treffId)
        val antallJobbsøkere = jobbsøkerRepository.hentAntallJobbsøkere(treffId)
        return rekrutteringstreff.tilRekrutteringstreffDto(antallArbeidsgivere, antallJobbsøkere)
    }

    fun hentRekrutteringstreffMedHendelser(treffId: TreffId): RekrutteringstreffDetaljOutboundDto? {
        val rekrutteringstreff = hentRekrutteringstreff(treffId)
        if (rekrutteringstreff == null) {
            logger.info("Fant ikke rekrutteringstreff med id: $treffId")
            return null
        }
        val hendelser = rekrutteringstreffRepository.hentAlleHendelser(treffId)
        return RekrutteringstreffDetaljOutboundDto(
            rekrutteringstreff,
            hendelser.map { RekrutteringstreffHendelseOutboundDto(
                id = it.id,
                tidspunkt = it.tidspunkt,
                hendelsestype = it.hendelsestype,
                opprettetAvAktørType = it.opprettetAvAktørType,
                aktørIdentifikasjon = it.aktørIdentifikasjon
            )}
        )
    }

    private fun leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
        treffId: TreffId,
        ident: String,
        rekrutteringstreffHendelsestype: RekrutteringstreffHendelsestype,
        jobbsøkerHendelsestypeSvartJa: JobbsøkerHendelsestype,
        jobbsøkerHendelsestypeIkkeSvart: JobbsøkerHendelsestype,
        status: RekrutteringstreffStatus,
    ) {
       dataSource.executeInTransaction { connection ->
           val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treffId)

           rekrutteringstreffRepository.leggTilHendelse(
               connection,
               dbId,
               rekrutteringstreffHendelsestype,
               AktørType.ARRANGØR,
               ident
           )

           val alleJobbsøkere = jobbsøkerRepository.hentJobbsøkere(connection, treffId)

           val jobbsøkereMedAktivtSvarJa = jobbsøkerService.finnJobbsøkereMedAktivtSvarJa(alleJobbsøkere)
           if (jobbsøkereMedAktivtSvarJa.isNotEmpty()) {
               jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                   connection,
                   jobbsøkerHendelsestypeSvartJa,
                   jobbsøkereMedAktivtSvarJa.map { it.personTreffId },
                   ident
               )
           }

           val jobbsøkereSomIkkeSvart = jobbsøkerService.finnJobbsøkereSomIkkeSvart(alleJobbsøkere)
           if (jobbsøkereSomIkkeSvart.isNotEmpty()) {
               jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                   connection,
                   jobbsøkerHendelsestypeIkkeSvart,
                   jobbsøkereSomIkkeSvart.map { it.personTreffId },
                   ident
               )
           }

           rekrutteringstreffRepository.endreStatus(connection, treffId, status)
        }
    }

    fun registrerEndring(treffId: TreffId, endringer: String, endretAv: String) {
        dataSource.executeInTransaction { connection ->
            val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treffId)

            rekrutteringstreffRepository.leggTilHendelse(
                connection,
                dbId,
                RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING,
                AktørType.ARRANGØR,
                endretAv,
                endringer
            )

            val alleJobbsøkere = jobbsøkerRepository.hentJobbsøkere(connection, treffId)
            val jobbsøkereSomSkalVarsles = alleJobbsøkere
                .filter { jobbsøkerService.skalVarslesOmEndringer(it.hendelser) }
                .map { it.personTreffId }

            if (jobbsøkereSomSkalVarsles.isNotEmpty()) {
                jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                    connection,
                    JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON,
                    jobbsøkereSomSkalVarsles,
                    endretAv,
                    hendelseData = endringer
                )
                logger.info("Registrert endring for rekrutteringstreff  ${treffId.somString} med ${jobbsøkereSomSkalVarsles.size} jobbsøkere som skal varsles")
            }
        }
    }

    fun avpubliser(treffId: TreffId, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.AVPUBLISERT, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.UTKAST)
        }
    }

    fun opprett(internalDto: OpprettRekrutteringstreffInternalDto): TreffId {
        return dataSource.executeInTransaction { connection ->
            val (treffId, dbId) = rekrutteringstreffRepository.opprett(connection, internalDto)
            rekrutteringstreffRepository.leggTilHendelse(
                connection,
                dbId,
                RekrutteringstreffHendelsestype.OPPRETTET,
                AktørType.ARRANGØR,
                internalDto.opprettetAvPersonNavident
            )
            treffId
        }
    }

    fun oppdater(treffId: TreffId, dto: OppdaterRekrutteringstreffDto, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treffId)
            rekrutteringstreffRepository.oppdater(connection, treffId, dto, navIdent)
            rekrutteringstreffRepository.leggTilHendelse(
                connection,
                dbId,
                RekrutteringstreffHendelsestype.OPPDATERT,
                AktørType.ARRANGØR,
                navIdent
            )
        }
    }

    fun hentHendelser(treffId: TreffId): List<RekrutteringstreffHendelse> {
        return rekrutteringstreffRepository.hentHendelser(treffId)
    }

    fun hentAlleHendelser(treffId: TreffId): List<FellesHendelseOutboundDto> {
        return rekrutteringstreffRepository.hentAlleHendelser(treffId)
    }

    fun gjenåpne(treffId: TreffId, navIdent: String) {
        val treff = rekrutteringstreffRepository.hent(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")

        if (treff.status != RekrutteringstreffStatus.AVLYST) {
            logger.warn("Forsøk på å gjenåpne rekrutteringstreff som ikke er avlyst. treffId: $treffId status: ${treff.status}")
            throw UlovligOppdateringException("Kan kun gjenåpne rekrutteringstreff som er i AVLYST status")
        }

        dataSource.executeInTransaction { connection ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.GJENÅPNET, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.PUBLISERT)
        }
    }
}
