package no.nav.toi.rekrutteringstreff

import no.nav.arbeidsgiver.toi.logging.log
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.exception.UlovligOppdateringException
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.rekrutteringstreff.dto.FellesHendelseOutboundDto
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import org.slf4j.Logger
import java.sql.Connection
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.sql.DataSource

class RekrutteringstreffService(
    private val dataSource: DataSource,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository,
    private val jobbsøkerService: JobbsøkerService,
    private val miljø: Miljø,
) {
    private val logger: Logger = log

    fun avlys(treffId: TreffId, avlystAv: String) {
        dataSource.medLåstTreff(treffId) { connection ->
            val treff = hentTreff(connection, treffId)
            if (treff.status == RekrutteringstreffStatus.FULLFØRT) {
                logger.warn("Forsøk på å avlyse fullført rekrutteringstreff. treffId: $treffId")
                throw UlovligOppdateringException("Kan ikke avlyse rekrutteringstreff som allerede er fullført")
            }
            if (treff.status == RekrutteringstreffStatus.AVLYST) {
                logger.warn("Forsøk på å avlyse allerede avlyst rekrutteringstreff. treffId: $treffId")
                throw UlovligOppdateringException("Rekrutteringstreff er allerede avlyst")
            }

            avsluttTreff(
                connection,
                treffId,
                avlystAv,
                RekrutteringstreffHendelsestype.AVLYST,
                RekrutteringstreffStatus.AVLYST,
                Jobbsøker::hendelseNårTreffetAvlyses,
            )
        }
    }

    fun publiser(treffId: TreffId, navIdent: String) {
        dataSource.medLåstTreff(treffId) { connection ->
            val treff = hentTreff(connection, treffId)
            if (treff.status != RekrutteringstreffStatus.UTKAST) {
                logger.warn("Forsøk på å publisere rekrutteringstreff som ikke er utkast. treffId: $treffId status: ${treff.status}")
                throw UlovligOppdateringException("Kan kun publisere rekrutteringstreff som er i UTKAST status")
            }

            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.PUBLISERT, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.PUBLISERT)
        }
    }

    fun fullfør(treffId: TreffId, fullfortAv: String) {
        log.info("Fullfører treff med id $treffId")
        dataSource.medLåstTreff(treffId) { connection ->
            val treff = hentTreff(connection, treffId)
            if (treff.status != RekrutteringstreffStatus.PUBLISERT) {
                logger.warn("Forsøk på å fullføre rekrutteringstreff som ikke er publisert. treffId: $treffId status: ${treff.status}")
                throw UlovligOppdateringException("Kan kun fullføre rekrutteringstreff som er i PUBLISERT status")
            }
            if (treff.tilTid == null || treff.tilTid.isAfter(ZonedDateTime.now(ZoneId.of("Europe/Oslo")))) {
                logger.warn("Forsøk på å fullføre rekrutteringstreff som fortsatt er i gang. treffId: $treffId")
                throw UlovligOppdateringException("Rekrutteringstreff med id $treffId er fremdeles i gang og kan ikke fullføres")
            }

            avsluttTreff(
                connection,
                treffId,
                fullfortAv,
                RekrutteringstreffHendelsestype.FULLFØRT,
                RekrutteringstreffStatus.FULLFØRT,
                Jobbsøker::hendelseNårTreffetFullføres,
            )
        }
        log.info("Fullførte treff med id $treffId")
    }

    /**
     * Markerer et rekrutteringstreff og tilhørende arbeidsgivere som slettet (soft-delete).
     * Kan kun gjøres på treff i UTKAST-status uten jobbsøkere.
     */
    fun markerSlettet(treffId: TreffId, navIdent: String) {
        dataSource.medLåstTreff(treffId) { connection ->
            val treff = hentTreff(connection, treffId)
            if (treff.status != RekrutteringstreffStatus.UTKAST || jobbsøkerRepository.hentJobbsøkere(connection, treffId).isNotEmpty()) {
                throw UlovligOppdateringException("Kan ikke slette treff med id $treffId")
            }

            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.SLETTET, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.SLETTET)

            arbeidsgiverRepository.hentArbeidsgivere(connection, treffId).forEach { arbeidsgiver ->
                arbeidsgiverRepository.markerSlettet(connection, arbeidsgiver.arbeidsgiverTreffId.somUuid)
                arbeidsgiverRepository.leggTilHendelse(connection, arbeidsgiver.arbeidsgiverTreffId, ArbeidsgiverHendelsestype.SLETTET, AktørType.ARRANGØR, navIdent)
            }
        }
    }

    fun hentRekrutteringstreff(treffId: TreffId): RekrutteringstreffDto? {
        val rekrutteringstreff = rekrutteringstreffRepository.hent(treffId)
        if (rekrutteringstreff == null) {
            logger.info("Fant ikke rekrutteringstreff med id: $treffId")
            return null
        }
        val antallArbeidsgivere = arbeidsgiverRepository.hentAntallArbeidsgivere(treffId)
        val antallJobbsøkere = jobbsøkerRepository.hentAntallJobbsøkere(treffId)
        val antallJobbsøkereSvartJa = jobbsøkerRepository.hentAntallJobbsøkereSvartJa(treffId)
        val antallJobbsøkereFåttJobb = jobbsøkerRepository.hentAntallJobbsøkereFåttJobb(treffId)
        return rekrutteringstreff.tilRekrutteringstreffDto(antallArbeidsgivere, antallJobbsøkere, antallJobbsøkereSvartJa, antallJobbsøkereFåttJobb)
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

    /** Kalleren må ha låst treffet. Statusene til jobbsøkerne leses etter at de er låst. */
    private fun avsluttTreff(
        connection: Connection,
        treffId: TreffId,
        ident: String,
        hendelsestype: RekrutteringstreffHendelsestype,
        nyStatus: RekrutteringstreffStatus,
        jobbsøkerhendelse: (Jobbsøker) -> JobbsøkerHendelsestype?,
    ) {
        connection.låsAlleJobbsøkerePåTreff(treffId)
        rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, hendelsestype, ident)

        jobbsøkerRepository.hentJobbsøkere(connection, treffId)
            .groupBy(jobbsøkerhendelse)
            .forEach { (jobbsøkerhendelsestype, jobbsøkere) ->
                if (jobbsøkerhendelsestype != null) {
                    jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                        connection,
                        jobbsøkerhendelsestype,
                        jobbsøkere.map { it.personTreffId },
                        ident
                    )
                }
            }

        rekrutteringstreffRepository.endreStatus(connection, treffId, nyStatus)
    }

    private fun hentTreff(connection: Connection, treffId: TreffId): Rekrutteringstreff =
        rekrutteringstreffRepository.hent(connection, treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")

    fun registrerEndring(treffId: TreffId, endringer: Rekrutteringstreffendringer, endretAv: String) {
        dataSource.medLåstTreff(treffId) { connection ->
            val treff = hentTreff(connection, treffId)
            if (treff.status != RekrutteringstreffStatus.PUBLISERT) {
                logger.warn("Forsøk på å registrere endring for rekrutteringstreff som ikke er publisert. treffId: $treffId status: ${treff.status}")
                throw UlovligOppdateringException("Kan kun registrere endringer for treff som har publisert status")
            }
            connection.låsAlleJobbsøkerePåTreff(treffId)

            val endringerJson = JacksonConfig.mapper.writeValueAsString(endringer)

            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection,
                treffId,
                RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING,
                endretAv
            )

            val alleJobbsøkere = jobbsøkerRepository.hentJobbsøkere(connection, treffId)

            val jobbsøkereSomSkalOppdateres = alleJobbsøkere
                .filter { it.erInvitert() }
                .map { it.personTreffId }

            if (jobbsøkereSomSkalOppdateres.isNotEmpty()) {
                jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                    connection,
                    JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING,
                    jobbsøkereSomSkalOppdateres,
                    endretAv
                )
                logger.info("Registrert endring på rekrutteringstreff ${treffId.somString} for ${jobbsøkereSomSkalOppdateres.size} jobbsøkere")
            }

            val jobbsøkereSomSkalVarsles = alleJobbsøkere
                .filter { jobbsøkerService.skalVarslesOmEndringer(it) }
                .map { it.personTreffId }

            if (jobbsøkereSomSkalVarsles.isNotEmpty()) {
                jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                    connection,
                    JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON,
                    jobbsøkereSomSkalVarsles,
                    endretAv,
                    hendelseData = endringerJson
                )
                logger.info("Registrert at varsel om oppdatert treff ${treffId.somString} skal sendes til ${jobbsøkereSomSkalVarsles.size} jobbsøkere")
            }
        }
    }

    fun avpubliser(treffId: TreffId, navIdent: String) {
        dataSource.medLåstTreff(treffId) { connection ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.AVPUBLISERT, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.UTKAST)
        }
    }

    fun opprett(internalDto: OpprettRekrutteringstreffInternalDto): TreffId {
        if (internalDto.kategori == RekrutteringstreffKategori.WORKOP && miljø.erProd) {
            logger.warn("Forsøk på å opprette WorkOp i produksjon. Avvist.")
            throw UlovligOppdateringException("WorkOp kan ikke opprettes i produksjon")
        }
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
        dataSource.medLåstTreff(treffId) { connection ->
            rekrutteringstreffRepository.oppdater(connection, treffId, dto, navIdent)
            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.OPPDATERT, navIdent)
        }
    }

    fun hentHendelser(treffId: TreffId): List<RekrutteringstreffHendelse> {
        return rekrutteringstreffRepository.hentHendelser(treffId)
    }

    fun hentAlleHendelser(treffId: TreffId): List<FellesHendelseOutboundDto> {
        return rekrutteringstreffRepository.hentAlleHendelser(treffId)
    }

    fun gjenåpne(treffId: TreffId, navIdent: String) {
        dataSource.medLåstTreff(treffId) { connection ->
            val treff = hentTreff(connection, treffId)
            if (treff.status != RekrutteringstreffStatus.AVLYST) {
                logger.warn("Forsøk på å gjenåpne rekrutteringstreff som ikke er avlyst. treffId: $treffId status: ${treff.status}")
                throw UlovligOppdateringException("Kan kun gjenåpne rekrutteringstreff som er i AVLYST status")
            }

            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.GJENÅPNET, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.PUBLISERT)
        }
    }

    fun hentPubliserteTreffHvorTilTidErPassert(): List<Rekrutteringstreff> {
        return rekrutteringstreffRepository.hentPubliserteTreffHvorTilTidErPassert()
    }
}
