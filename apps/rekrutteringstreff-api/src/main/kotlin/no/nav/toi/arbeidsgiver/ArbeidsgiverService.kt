package no.nav.toi.arbeidsgiver

import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

class ArbeidsgiverService(
    private val dataSource: DataSource,
    private val arbeidsgiverRepository: ArbeidsgiverRepository
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun leggTilArbeidsgiver(arbeidsgiver: LeggTilArbeidsgiver, treffId: TreffId, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            val arbeidsgiverDbId = arbeidsgiverRepository.opprettArbeidsgiver(connection, arbeidsgiver, treffId)
            arbeidsgiverRepository.leggTilHendelse(connection, arbeidsgiverDbId, ArbeidsgiverHendelsestype.OPPRETTET, AktørType.ARRANGØR, navIdent)
            arbeidsgiverRepository.leggTilNaringskoder(connection, arbeidsgiverDbId, arbeidsgiver.næringskoder)
        }
        logger.info("La til arbeidsgiver ${arbeidsgiver.orgnr.asString} for treff $treffId")
    }

    /**
     * Markerer en arbeidsgiver som slettet (soft-delete).
     */
    fun markerArbeidsgiverSlettet(arbeidsgiverId: UUID, treffId: TreffId, navIdent: String): Boolean {
        val resultat = dataSource.executeInTransaction { connection ->
            arbeidsgiverRepository.markerSlettet(connection, arbeidsgiverId, navIdent)
        }
        if (resultat) {
            logger.info("Markert arbeidsgiver $arbeidsgiverId som slettet for treff $treffId")
        }
        return resultat
    }

    fun hentArbeidsgivere(treffId: TreffId): List<Arbeidsgiver> {
        return arbeidsgiverRepository.hentArbeidsgivere(treffId)
    }

    fun hentArbeidsgiver(treffId: TreffId, orgnr: Orgnr): Arbeidsgiver? {
        return arbeidsgiverRepository.hentArbeidsgiver(treffId, orgnr)
    }

    fun hentArbeidsgiverHendelser(treffId: TreffId): List<ArbeidsgiverHendelseMedArbeidsgiverData> {
        return arbeidsgiverRepository.hentArbeidsgiverHendelser(treffId)
    }
}
