package no.nav.toi.arbeidsgiver

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiversBehovDto
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.medLåstTreff
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.Treffkontekst
import no.nav.toi.treffgjennomføring.TreffkontekstRepository
import no.nav.toi.treffgjennomføring.krevKontekst
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

class ArbeidsgiverService(
    private val dataSource: DataSource,
    private val arbeidsgiverRepository: ArbeidsgiverRepository,
    private val objectMapper: ObjectMapper,
    private val kontekstRepository: TreffkontekstRepository,
    private val møteplanRepository: MøteplanRepository,
    private val oppmøteRepository: OppmøteRepository,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun leggTilArbeidsgiver(arbeidsgiver: LeggTilArbeidsgiver, treffId: TreffId, navIdent: String): ArbeidsgiverTreffId {
        val arbeidsgiverTreffId = dataSource.medLåstTreff(treffId) { connection ->
            oppdaterMøteplan(connection, kontekstRepository.krevKontekst(connection, treffId))
            val id = opprettArbeidsgiverMedNæringskoder(connection, arbeidsgiver, treffId, navIdent)
            oppdaterMøteplan(connection, kontekstRepository.krevKontekst(connection, treffId))
            id
        }
        logger.info("La til arbeidsgiver ${arbeidsgiver.orgnr.asString} for treff $treffId")
        return arbeidsgiverTreffId
    }

    fun leggTilArbeidsgiverMedBehov(
        arbeidsgiver: LeggTilArbeidsgiver,
        behov: ArbeidsgiversBehov,
        treffId: TreffId,
        navIdent: String,
    ) {
        dataSource.medLåstTreff(treffId) { connection ->
            oppdaterMøteplan(connection, kontekstRepository.krevKontekst(connection, treffId))
            val reaktivert = arbeidsgiverRepository.reaktiverArbeidsgiver(connection, treffId, arbeidsgiver)
            val arbeidsgiverTreffId = if (reaktivert != null) {
                arbeidsgiverRepository.leggTilHendelse(connection, reaktivert, ArbeidsgiverHendelsestype.REAKTIVERT, AktørType.ARRANGØR, navIdent)
                reaktivert
            } else {
                opprettArbeidsgiverMedNæringskoder(connection, arbeidsgiver, treffId, navIdent)
            }
            arbeidsgiverRepository.upsertBehov(connection, treffId, arbeidsgiverTreffId, behov)
            arbeidsgiverRepository.leggTilHendelse(
                connection,
                arbeidsgiverTreffId,
                ArbeidsgiverHendelsestype.BEHOV_ENDRET,
                AktørType.ARRANGØR,
                navIdent,
                hendelseData = serialiserBehov(behov),
            )
            oppdaterMøteplan(connection, kontekstRepository.krevKontekst(connection, treffId))
        }
        logger.info("La til arbeidsgiver med behov ${arbeidsgiver.orgnr.asString} for treff $treffId")
    }

    private fun opprettArbeidsgiverMedNæringskoder(
        connection: Connection,
        arbeidsgiver: LeggTilArbeidsgiver,
        treffId: TreffId,
        navIdent: String,
    ): ArbeidsgiverTreffId {
        val ny = arbeidsgiverRepository.opprettArbeidsgiver(connection, arbeidsgiver, treffId)
        arbeidsgiverRepository.leggTilHendelse(connection, ny, ArbeidsgiverHendelsestype.OPPRETTET, AktørType.ARRANGØR, navIdent)
        arbeidsgiverRepository.leggTilNaringskoder(connection, ny, arbeidsgiver.næringskoder)
        return ny
    }

    fun oppdaterBehov(
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        treffId: TreffId,
        behov: ArbeidsgiversBehov,
        navIdent: String,
    ): ArbeidsgiverMedBehov? {
        val oppdatert = dataSource.executeInTransaction { connection ->
            val oppdatert = arbeidsgiverRepository.upsertBehov(connection, treffId, arbeidsgiverTreffId, behov)
            if (!oppdatert) {
                return@executeInTransaction false
            }
            arbeidsgiverRepository.leggTilHendelse(
                connection,
                arbeidsgiverTreffId,
                ArbeidsgiverHendelsestype.BEHOV_ENDRET,
                AktørType.ARRANGØR,
                navIdent,
                hendelseData = serialiserBehov(behov),
            )
            true
        }

        if (!oppdatert) return null

        return arbeidsgiverRepository.hentArbeidsgivereMedBehov(treffId)
            .firstOrNull { it.arbeidsgiver.arbeidsgiverTreffId.somString == arbeidsgiverTreffId.somString }
    }

    private fun serialiserBehov(behov: ArbeidsgiversBehov): String =
        objectMapper.writeValueAsString(ArbeidsgiversBehovDto.fra(behov))

    fun markerArbeidsgiverSlettet(arbeidsgiverId: UUID, treffId: TreffId, navIdent: String): Boolean {
        val resultat = dataSource.medLåstTreff(treffId) { connection ->
            val arbeidsgiverTreffId = ArbeidsgiverTreffId(arbeidsgiverId)
            val kontekst = kontekstRepository.krevKontekst(connection, treffId)
            val internId = kontekst.arbeidsgiverId(arbeidsgiverTreffId) ?: return@medLåstTreff false
            oppdaterMøteplan(connection, kontekst)
            val registreringer = arbeidsgiverRepository.sjekkRegistreringer(connection, internId, kontekst.treffDbId)
            if (registreringer.finnesRegistreringer()) {
                throw ArbeidsgiverKanIkkeSlettesException(registreringer)
            }
            val markert = arbeidsgiverRepository.markerSlettet(connection, arbeidsgiverId)
            if (markert) {
                arbeidsgiverRepository.leggTilHendelse(connection, arbeidsgiverTreffId, ArbeidsgiverHendelsestype.SLETTET, AktørType.ARRANGØR, navIdent)
                møteplanRepository.fjernArbeidsgiverOgKompakter(connection, internId, kontekst.treffDbId)
            }
            markert
        }
        if (resultat) {
            logger.info("Markert arbeidsgiver $arbeidsgiverId som slettet for treff $treffId")
        }
        return resultat
    }

    // Lagrer også eldre beregnede plasseringer før arbeidsgiverantallet endres.
    private fun oppdaterMøteplan(connection: Connection, kontekst: Treffkontekst) {
        val oppmøte = oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
        val møteplan = møteplanRepository.hentMøteplan(connection, kontekst, oppmøte)
        møteplanRepository.lagreMøteplan(connection, kontekst, møteplan)
    }

    fun hentArbeidsgivere(treffId: TreffId): List<Arbeidsgiver> {
        return arbeidsgiverRepository.hentArbeidsgivere(treffId)
    }

    fun hentArbeidsgivereMedBehov(treffId: TreffId): List<ArbeidsgiverMedBehov> {
        return arbeidsgiverRepository.hentArbeidsgivereMedBehov(treffId)
    }

    fun hentArbeidsgiver(treffId: TreffId, orgnr: Orgnr): Arbeidsgiver? {
        return arbeidsgiverRepository.hentArbeidsgiver(treffId, orgnr)
    }

    fun hentArbeidsgiverHendelser(treffId: TreffId): List<ArbeidsgiverHendelseMedArbeidsgiverData> {
        return arbeidsgiverRepository.hentArbeidsgiverHendelser(treffId)
    }
}
