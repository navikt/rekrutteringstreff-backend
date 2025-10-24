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
        // Hent alle jobbsøkere med hendelser - gjør filtrering i Kotlin
        val alleJobbsøkere = jobbsøkerRepository.hentJobbsøkereHendelser(treff)

        // Business logic: Finn jobbsøkere som har aktivt svar JA
        val jobbsøkereMedAktivtSvarJa = alleJobbsøkere
            .filter { harAktivtSvarJa(it.hendelser) }
            .map { it.personTreffId }

        dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                // Hent rekrutteringstreff db-id
                val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(c, treff)

                // Legg til hendelse for rekrutteringstreff
                rekrutteringstreffRepository.leggTilHendelse(c, dbId, rekrutteringstreffHendelsestype, AktørType.ARRANGØR, ident)

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

    private fun harAktivtSvarJa(hendelser: List<no.nav.toi.jobbsoker.JobbsøkerHendelse>): Boolean {
        val jaSvar = hendelser.filter { it.hendelsestype == JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON }
        val neiSvar = hendelser.filter { it.hendelsestype == JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON }

        if (jaSvar.isEmpty()) return false

        if (neiSvar.isEmpty()) return true

        val sisteJa = jaSvar.maxByOrNull { it.tidspunkt }!!
        val sisteNei = neiSvar.maxByOrNull { it.tidspunkt }!!

        return sisteJa.tidspunkt.isAfter(sisteNei.tidspunkt)
    }

}
