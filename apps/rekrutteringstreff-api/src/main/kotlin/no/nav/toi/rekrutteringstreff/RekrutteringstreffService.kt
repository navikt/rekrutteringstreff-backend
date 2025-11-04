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
           val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treffId)

           rekrutteringstreffRepository.leggTilHendelse(
               connection,
               dbId,
               rekrutteringstreffHendelsestype,
               AktørType.ARRANGØR,
               ident
           )

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
            val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treff)

            rekrutteringstreffRepository.leggTilHendelse(
                connection,
                dbId,
                RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING,
                AktørType.ARRANGØR,
                endretAv,
                endringer
            )
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
                logger.info("Registrert endring for rekrutteringstreff  ${treff.somString} med ${jobbsøkereSomSkalVarsles.size} jobbsøkere som skal varsles")
            }
        }
    }

    private fun skalVarslesOmEndringer(hendelser: List<JobbsøkerHendelse>): Boolean {
        if (hendelser.isEmpty()) return false

        // Filtrer kun invitasjons- og svar-hendelser
        val relevanteHendelser = hendelser.filter {
            it.hendelsestype in setOf(
                JobbsøkerHendelsestype.INVITERT,
                JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
                JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON
            )
        }

        if (relevanteHendelser.isEmpty()) return false

        // Hendelser er allerede sortert DESC (nyeste først), ta første av de relevante
        val sisteRelevanteHendelse = relevanteHendelser.first()

        // Varsle kun hvis siste relevante hendelse er INVITERT eller SVART_JA_TIL_INVITASJON
        return sisteRelevanteHendelse.hendelsestype in setOf(
            JobbsøkerHendelsestype.INVITERT,
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON
        )
    }
}
