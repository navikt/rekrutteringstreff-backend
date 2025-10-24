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
                val jobbsøkereMedAktivtSvarJa = jobbsøkerRepository.hentJobbsøkereMedAktivtSvarJa(treff)

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

}
