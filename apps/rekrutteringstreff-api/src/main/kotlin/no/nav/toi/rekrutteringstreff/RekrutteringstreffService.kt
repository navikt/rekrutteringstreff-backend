package no.nav.toi.rekrutteringstreff

import io.javalin.http.NotFoundResponse
import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.JobbsøkerRepository
import java.util.ArrayList
import javax.sql.DataSource

class RekrutteringstreffService(
    private val dataSource: DataSource,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository
) {

    fun avlys(treff: TreffId, avlystAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelser(
            treff,
            avlystAv,
            RekrutteringstreffHendelsestype.AVLYST,
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST
        )
    }

    fun fullfør(treff: TreffId, fullfortAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelser(
            treff,
            fullfortAv,
            RekrutteringstreffHendelsestype.FULLFØRT,
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT
        )
    }

    fun hentAlleRekrutteringstreff(): List<RekrutteringstreffDto> {
        val alleRekrutteringstreff = rekrutteringstreffRepository.hentAlle()
        val rekrutteringstreffDto: ArrayList<RekrutteringstreffDto> = ArrayList<RekrutteringstreffDto>()
        alleRekrutteringstreff.forEach {
            val antallArbeidsgivere = arbeidsgiverRepository.hentAntallArbeidsgivere(it.id)
            val antallJobbsøkere = jobbsøkerRepository.hentAntallJobbsøkere(it.id)
            rekrutteringstreffDto.add(it.tilRekrutteringstreffDto(antallArbeidsgivere, antallJobbsøkere))
        }
        return rekrutteringstreffDto
    }

    fun hentRekrutteringstreff(treff: TreffId): RekrutteringstreffDto {
        val rekrutteringstreff = rekrutteringstreffRepository.hent(treff)
        val antallArbeidsgivere = arbeidsgiverRepository.hentAntallArbeidsgivere(treff)
        val antallJobbsøkere = jobbsøkerRepository.hentAntallJobbsøkere(treff)
        return rekrutteringstreff?.tilRekrutteringstreffDto(antallArbeidsgivere, antallJobbsøkere) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
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
}
