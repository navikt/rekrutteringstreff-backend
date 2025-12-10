package no.nav.toi.rekrutteringstreff

import io.javalin.http.NotFoundResponse
import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import java.util.ArrayList
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.log
import org.slf4j.Logger
import javax.sql.DataSource

class RekrutteringstreffService(
    private val dataSource: DataSource,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository
) {
    private val logger: Logger = log

    fun avlys(treff: TreffId, avlystAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
            treff,
            avlystAv,
            RekrutteringstreffHendelsestype.AVLYST,
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST,
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST,
            RekrutteringstreffStatus.AVLYST,
        )
    }

    fun fullfør(treff: TreffId, fullfortAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
            treff,
            fullfortAv,
            RekrutteringstreffHendelsestype.FULLFØRT,
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT,
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT,
            RekrutteringstreffStatus.FULLFØRT
        )
    }

    fun slett(treffId: TreffId, navIdent: String) {
        val jobbsøkere = jobbsøkerRepository.hentJobbsøkere(treffId)
        if (jobbsøkere.isNotEmpty()) {
            throw IllegalStateException("Kan ikke slette rekrutteringstreff med registrerte jobbsøkere")
        }
        dataSource.executeInTransaction { connection ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.SLETTET, navIdent)
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.SLETTET)

            val arbeidsgivere = arbeidsgiverRepository.hentArbeidsgivere(treffId)
            arbeidsgivere.forEach { arbeidsgiver ->
                arbeidsgiverRepository.slett(connection, arbeidsgiver.arbeidsgiverTreffId.somUuid, navIdent)
            }
        }
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
        jobbsøkerHendelsestypeSvartJa: JobbsøkerHendelsestype,
        jobbsøkerHendelsestypeIkkeSvart: JobbsøkerHendelsestype,
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

           // Hent alle jobbsøkere for treffet med deres hendelser
           val alleJobbsøkere = jobbsøkerRepository.hentJobbsøkere(connection, treffId)

           // Filtrer i Kotlin: finn de som har aktivt svar ja
           val jobbsøkereMedAktivtSvarJa = finnJobbsøkereMedAktivtSvarJa(alleJobbsøkere)

           // Legg til hendelser for alle jobbsøkere med aktivt svar ja
           if (jobbsøkereMedAktivtSvarJa.isNotEmpty()) {
               jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                   connection,
                   jobbsøkerHendelsestypeSvartJa,
                   jobbsøkereMedAktivtSvarJa.map { it.personTreffId },
                   ident
               )
           }

           // Filtrer i Kotlin: finn de som ikke har svart
           val jobbsøkereSomIkkeSvart = finnJobbsøkereSomIkkeSvart(alleJobbsøkere)

           // Legg til hendelser for alle jobbsøkere som ikke har svart
           if (jobbsøkereSomIkkeSvart.isNotEmpty()) {
               jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                   connection,
                   jobbsøkerHendelsestypeIkkeSvart,
                   jobbsøkereSomIkkeSvart.map { it.personTreffId },
                   ident
               )
           }

           rekrutteringstreffRepository.endreStatus(connection, treffId, status)
        }
    }

    private fun finnJobbsøkereMedAktivtSvarJa(jobbsøkere: List<Jobbsøker>): List<Jobbsøker> {
        return jobbsøkere.filter { jobbsøker ->
            val hendelsestyper = jobbsøker.hendelser.map { it.hendelsestype }
            // Hendelser er sortert DESC (nyeste først), så indexOf finner den nyeste
            val nyesteJaIndex = hendelsestyper.indexOf(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON)
            val nyesteNeiIndex = hendelsestyper.indexOf(JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON)

            // Har svart ja, og ja er nyere enn eventuell nei (lavere index = nyere)
            nyesteJaIndex != -1 && (nyesteNeiIndex == -1 || nyesteJaIndex < nyesteNeiIndex)
        }
    }

    private fun finnJobbsøkereSomIkkeSvart(jobbsøkere: List<Jobbsøker>): List<Jobbsøker> {
        return jobbsøkere.filter { jobbsøker ->
            val hendelsestyper = jobbsøker.hendelser.map { it.hendelsestype }.toSet()

            // Har INVITERT, men ikke svart ja eller nei
            hendelsestyper.contains(JobbsøkerHendelsestype.INVITERT) &&
            !hendelsestyper.contains(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON) &&
            !hendelsestyper.contains(JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON)
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
