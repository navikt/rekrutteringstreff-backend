package no.nav.toi.arbeidsgiver

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverBehovDto
import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

class ArbeidsgiverService(
    private val dataSource: DataSource,
    private val arbeidsgiverRepository: ArbeidsgiverRepository,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun leggTilArbeidsgiver(arbeidsgiver: LeggTilArbeidsgiver, treffId: TreffId, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            val arbeidsgiverTreffId = arbeidsgiverRepository.opprettArbeidsgiver(connection, arbeidsgiver, treffId)
            arbeidsgiverRepository.leggTilHendelse(connection, arbeidsgiverTreffId, ArbeidsgiverHendelsestype.OPPRETTET, AktørType.ARRANGØR, navIdent)
            arbeidsgiverRepository.leggTilNaringskoder(connection, arbeidsgiverTreffId, arbeidsgiver.næringskoder)
        }
        logger.info("La til arbeidsgiver ${arbeidsgiver.orgnr.asString} for treff $treffId")
    }

    fun leggTilArbeidsgiverMedBehov(
        arbeidsgiver: LeggTilArbeidsgiver,
        behov: ArbeidsgiverBehov,
        treffId: TreffId,
        navIdent: String,
    ) {
        dataSource.executeInTransaction { connection ->
            val reaktivert = arbeidsgiverRepository.reaktiverArbeidsgiver(connection, treffId, arbeidsgiver)
            val arbeidsgiverTreffId = if (reaktivert != null) {
                arbeidsgiverRepository.leggTilHendelse(connection, reaktivert, ArbeidsgiverHendelsestype.REAKTIVERT, AktørType.ARRANGØR, navIdent)
                reaktivert
            } else {
                val ny = arbeidsgiverRepository.opprettArbeidsgiver(connection, arbeidsgiver, treffId)
                arbeidsgiverRepository.leggTilHendelse(connection, ny, ArbeidsgiverHendelsestype.OPPRETTET, AktørType.ARRANGØR, navIdent)
                arbeidsgiverRepository.leggTilNaringskoder(connection, ny, arbeidsgiver.næringskoder)
                ny
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
        }
        logger.info("La til arbeidsgiver med behov ${arbeidsgiver.orgnr.asString} for treff $treffId")
    }

    fun oppdaterBehov(
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        treffId: TreffId,
        behov: ArbeidsgiverBehov,
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

    private fun serialiserBehov(behov: ArbeidsgiverBehov): String =
        objectMapper.writeValueAsString(ArbeidsgiverBehovDto.fra(behov))

    fun markerArbeidsgiverSlettet(arbeidsgiverId: UUID, treffId: TreffId, navIdent: String): Boolean {
        val resultat = dataSource.executeInTransaction { connection ->
            val arbeidsgiverTreffId = ArbeidsgiverTreffId(arbeidsgiverId)
            val markert = arbeidsgiverRepository.markerSlettet(connection, arbeidsgiverId)
            if (markert) {
                arbeidsgiverRepository.leggTilHendelse(connection, arbeidsgiverTreffId, ArbeidsgiverHendelsestype.SLETTET, AktørType.ARRANGØR, navIdent)
            }
            markert
        }
        if (resultat) {
            logger.info("Markert arbeidsgiver $arbeidsgiverId som slettet for treff $treffId")
        }
        return resultat
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
