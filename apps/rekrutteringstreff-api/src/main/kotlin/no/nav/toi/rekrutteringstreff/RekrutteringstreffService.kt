package no.nav.toi.rekrutteringstreff

import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.jobbsoker.JobbsøkerRepository
import javax.sql.DataSource

class RekrutteringstreffService(
    private val dataSource: DataSource,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val jobbsøkerRepository: JobbsøkerRepository
) {

    fun avlys(treff: TreffId, avlystAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelser(
            treff,
            avlystAv,
            RekrutteringstreffHendelsestype.AVLYST,
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST
        )
    }

    fun fullfor(treff: TreffId, fullfortAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelser(
            treff,
            fullfortAv,
            RekrutteringstreffHendelsestype.FULLFØRT,
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT
        )
    }

    private fun leggTilHendelseForTreffMedJobbsøkerhendelser(
        treff: TreffId,
        ident: String,
        rekrutteringstreffHendelsestype: RekrutteringstreffHendelsestype,
        jobbsøkerHendelsestype: JobbsøkerHendelsestype
    ) {
        dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                // Hent rekrutteringstreff db-id
                val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(c, treff)

                // Legg til hendelse for rekrutteringstreff
                rekrutteringstreffRepository.leggTilHendelse(c, dbId, rekrutteringstreffHendelsestype, AktørType.ARRANGØR, ident)

                // Hent jobbsøkere med aktivt svar ja
                val jobbsøkereMedAktivtSvarJa = jobbsøkerRepository.hentJobbsøkereMedAktivtSvarJa(c, treff)

                // Legg til hendelser for alle jobbsøkere med aktivt svar ja
                if (jobbsøkereMedAktivtSvarJa.isNotEmpty()) {
                    jobbsøkerRepository.leggTilHendelserForJobbsøkere(c, jobbsøkerHendelsestype, jobbsøkereMedAktivtSvarJa, ident)
                }

                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = true
            }
        }
    }

    fun registrerEndring(treff: TreffId, endringer: String, endretAv: String) {
        dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                // Hent rekrutteringstreff db-id
                val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(c, treff)

                // Legg til hendelse for rekrutteringstreff med endringer som JSON
                rekrutteringstreffRepository.leggTilHendelse(
                    c,
                    dbId,
                    RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING,
                    AktørType.ARRANGØR,
                    endretAv,
                    endringer
                )

                // Hent jobbsøkere som skal varsles
                val jobbsøkereSomSkalVarsles = jobbsøkerRepository.hentJobbsøkereSomSkalVarslesOmEndringer(c, treff)

                // Legg til hendelser for alle relevante jobbsøkere med endringer som JSON
                if (jobbsøkereSomSkalVarsles.isNotEmpty()) {
                    jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                        c,
                        JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON,
                        jobbsøkereSomSkalVarsles,
                        endretAv,
                        hendelseData = endringer
                    )
                }

                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = true
            }
        }
    }
}
