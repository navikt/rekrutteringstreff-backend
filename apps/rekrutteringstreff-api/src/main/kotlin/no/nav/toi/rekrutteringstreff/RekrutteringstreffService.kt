package no.nav.toi.rekrutteringstreff

import io.javalin.http.NotFoundResponse
import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import java.util.ArrayList
import javax.sql.DataSource

class RekrutteringstreffService(
    private val dataSource: DataSource,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository
) {

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

    fun hentRekrutteringstreffMedHendelser(treff: TreffId): RekrutteringstreffDetaljOutboundDto {
        val rekrutteringstreff = hentRekrutteringstreff(treff)
        val hendelser = rekrutteringstreffRepository.hentAlleHendelser(treff)
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
}
