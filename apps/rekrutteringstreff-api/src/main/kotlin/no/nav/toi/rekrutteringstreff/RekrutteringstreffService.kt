package no.nav.toi.rekrutteringstreff

import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.log
import org.slf4j.Logger
import javax.sql.DataSource

class RekrutteringstreffService(
    private val dataSource: DataSource,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val jobbsøkerRepository: JobbsøkerRepository
) {
    private val logger: Logger = log

    fun avlys(treff: TreffId, avlystAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
            treff,
            avlystAv,
            RekrutteringstreffHendelsestype.AVLYST,
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST,
            RekrutteringstreffStatus.AVLYST,
        )
    }

    fun fullfør(treff: TreffId, fullfortAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
            treff,
            fullfortAv,
            RekrutteringstreffHendelsestype.FULLFØRT,
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT,
            RekrutteringstreffStatus.FULLFØRT
        )
    }

    private fun leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
        treffId: TreffId,
        ident: String,
        rekrutteringstreffHendelsestype: RekrutteringstreffHendelsestype,
        jobbsøkerHendelsestype: JobbsøkerHendelsestype,
        status: RekrutteringstreffStatus,
    ) {
       dataSource.executeInTransaction { connection ->
           // Hent rekrutteringstreff db-id
           val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treffId)

           // Legg til hendelse for rekrutteringstreff
           rekrutteringstreffRepository.leggTilHendelse(
               connection,
               dbId,
               rekrutteringstreffHendelsestype,
               AktørType.ARRANGØR,
               ident
           )

           // Hent jobbsøkere med aktivt svar ja
           val jobbsøkereMedAktivtSvarJa = jobbsøkerRepository.hentJobbsøkereMedAktivtSvarJa(connection, treffId)

           // Legg til hendelser for alle jobbsøkere med aktivt svar ja
           if (jobbsøkereMedAktivtSvarJa.isNotEmpty()) {
               jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                   connection,
                   jobbsøkerHendelsestype,
                   jobbsøkereMedAktivtSvarJa,
                   ident
               )
           }

           rekrutteringstreffRepository.endreStatus(connection, treffId, status)
        }
    }

    fun registrerEndring(treff: TreffId, endringer: String, endretAv: String) {
        dataSource.executeInTransaction { connection ->
            // Hent rekrutteringstreff db-id
            val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treff)

            // Legg til hendelse for rekrutteringstreff med endringer som JSON
            rekrutteringstreffRepository.leggTilHendelse(
                connection,
                dbId,
                RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING,
                AktørType.ARRANGØR,
                endretAv,
                endringer
            )

            // Hent alle jobbsøkere med hendelser og filtrer i service-laget
            val alleJobbsøkere = jobbsøkerRepository.hentJobbsøkere(connection, treff)
            val jobbsøkereSomSkalVarsles = alleJobbsøkere
                .filter { skalVarslesOmEndringer(it.hendelser) }
                .map { it.personTreffId }

            // Legg til hendelser for alle relevante jobbsøkere med endringer som JSON
            if (jobbsøkereSomSkalVarsles.isNotEmpty()) {
                jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                    connection,
                    JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON,
                    jobbsøkereSomSkalVarsles,
                    endretAv,
                    hendelseData = endringer
                )
                logger.info("Registrert endring for rekrutteringstreff med ${jobbsøkereSomSkalVarsles.size} jobbsøkere som skal varsles")
            }
        }
    }

    /**
     * Sjekker om en jobbsøker skal varsles om endringer basert på hendelseshistorikk.
     *
     * Logikk:
     * - Jobbsøker skal varsles hvis siste hendelse er INVITERT eller SVART_JA_TIL_INVITASJON
     * - Jobbsøker skal varsles hvis siste hendelse er SVART_JA_TREFF_AVLYST eller SVART_JA_TREFF_FULLFØRT,
     *   OG nest-siste hendelse var INVITERT eller SVART_JA_TIL_INVITASJON
     */
    private fun skalVarslesOmEndringer(hendelser: List<JobbsøkerHendelse>): Boolean {
        if (hendelser.isEmpty()) return false

        val sisteHendelse = hendelser.first() // Hendelser er sortert DESC (nyeste først)

        return when (sisteHendelse.hendelsestype) {
            JobbsøkerHendelsestype.INVITERT,
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON -> true

            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST,
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT -> {
                // Kun hvis nest-siste var INVITERT eller SVART_JA
                hendelser.getOrNull(1)?.hendelsestype in listOf(
                    JobbsøkerHendelsestype.INVITERT,
                    JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON
                )
            }

            else -> false
        }
    }

    private fun leggTilHendelseForTreffMedJobbsøkerhendelser(
        treff: TreffId,
        ident: String,
        rekrutteringstreffHendelsestype: RekrutteringstreffHendelsestype,
        jobbsøkerHendelsestype: JobbsøkerHendelsestype
    ) {
        dataSource.executeInTransaction { connection ->
            // Hent rekrutteringstreff db-id
            val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treff)

            // Legg til hendelse for rekrutteringstreff
            rekrutteringstreffRepository.leggTilHendelse(
                connection,
                dbId,
                rekrutteringstreffHendelsestype,
                AktørType.ARRANGØR,
                ident
            )

            // Hent jobbsøkere med aktivt svar ja
            val jobbsøkereMedAktivtSvarJa = jobbsøkerRepository.hentJobbsøkereMedAktivtSvarJa(connection, treff)

            // Legg til hendelser for alle jobbsøkere med aktivt svar ja
            if (jobbsøkereMedAktivtSvarJa.isNotEmpty()) {
                jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                    connection,
                    jobbsøkerHendelsestype,
                    jobbsøkereMedAktivtSvarJa,
                    ident
                )
                logger.info("Lagt til hendelse ${rekrutteringstreffHendelsestype.name} for ${jobbsøkereMedAktivtSvarJa.size} jobbsøkere")
            }
        }
    }
}
