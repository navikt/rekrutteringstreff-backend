package no.nav.toi.rekrutteringstreff

import io.javalin.http.NotFoundResponse
import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.exception.UlovligOppdateringException
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import java.util.ArrayList
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.dto.FellesHendelseOutboundDto
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import org.slf4j.Logger
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.sql.DataSource

class RekrutteringstreffService(
    private val dataSource: DataSource,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository
) {
    private val logger: Logger = log

    fun avlys(treffId: TreffId, avlystAv: String) {
        leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
            treffId,
            avlystAv,
            RekrutteringstreffHendelsestype.AVLYST,
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST,
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST,
            RekrutteringstreffStatus.AVLYST,
        )
    }

    fun publiser(treffId: TreffId, navIdent: String) {
        val treff = rekrutteringstreffRepository.hent(treffId)
            ?: throw IllegalStateException("Rekrutteringstreff med id $treffId ikke funnet")

        rekrutteringstreffRepository.publiser(treffId, navIdent)
    }

    fun fullfør(treffId: TreffId, fullfortAv: String) {
        val treff = rekrutteringstreffRepository.hent(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")

        if (treff.status != RekrutteringstreffStatus.PUBLISERT) {
            throw UlovligOppdateringException("Kan kun publisere rekrutteringstreff som er i PUBLISERT status")
        }

        if (treff.tilTid == null || treff.tilTid.isAfter(ZonedDateTime.now(ZoneId.of("Europe/Oslo")))) {
            throw UlovligOppdateringException("Rekrutteringstreff med id $treffId er fremdeles i gang og kan ikke publiseres")
        }

        leggTilHendelseForTreffMedJobbsøkerhendelserOgEndreStatusPåTreff(
            treffId,
            fullfortAv,
            RekrutteringstreffHendelsestype.FULLFØRT,
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT,
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT,
            RekrutteringstreffStatus.FULLFØRT
        )
    }

    fun kanSletteJobbtreff(treffId: TreffId, status: RekrutteringstreffStatus): Boolean {
        if (status != RekrutteringstreffStatus.UTKAST) {
            // Kun treff i utkaststatus kan slettes
            return false
        }
        val jobbsøkere = jobbsøkerRepository.hentJobbsøkere(treffId)
        return jobbsøkere.isEmpty()
    }

    fun slett(treffId: TreffId, navIdent: String) {
        val status = rekrutteringstreffRepository.hent(treffId)?.status ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")
        if (kanSletteJobbtreff(treffId, status).not()) {
            throw UlovligOppdateringException("Kan ikke slette treff med id $treffId")
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
        val alleRekrutteringstreff = rekrutteringstreffRepository.hentAlleSomIkkeErSlettet()
        val rekrutteringstreffDto: ArrayList<RekrutteringstreffDto> = ArrayList<RekrutteringstreffDto>()
        alleRekrutteringstreff.forEach {
            val antallArbeidsgivere = arbeidsgiverRepository.hentAntallArbeidsgivere(it.id)
            val antallJobbsøkere = jobbsøkerRepository.hentAntallJobbsøkere(it.id)
            rekrutteringstreffDto.add(it.tilRekrutteringstreffDto(antallArbeidsgivere, antallJobbsøkere))
        }
        return rekrutteringstreffDto
    }

    fun hentRekrutteringstreff(treffId: TreffId): RekrutteringstreffDto {
        val rekrutteringstreff = rekrutteringstreffRepository.hent(treffId)
        val antallArbeidsgivere = arbeidsgiverRepository.hentAntallArbeidsgivere(treffId)
        val antallJobbsøkere = jobbsøkerRepository.hentAntallJobbsøkere(treffId)
        return rekrutteringstreff?.tilRekrutteringstreffDto(antallArbeidsgivere, antallJobbsøkere) ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")
    }

    fun hentRekrutteringstreffMedHendelser(treffId: TreffId): RekrutteringstreffDetaljOutboundDto {
        val rekrutteringstreff = hentRekrutteringstreff(treffId)
        val hendelser = rekrutteringstreffRepository.hentAlleHendelser(treffId)
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

    fun registrerEndring(treffId: TreffId, endringer: String, endretAv: String) {

        dataSource.executeInTransaction { connection ->
            val dbId = rekrutteringstreffRepository.hentRekrutteringstreffDbId(connection, treffId)

            rekrutteringstreffRepository.leggTilHendelse(
                connection,
                dbId,
                RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING,
                AktørType.ARRANGØR,
                endretAv,
                endringer
            )
            val alleJobbsøkere = jobbsøkerRepository.hentJobbsøkere(connection, treffId)

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
                logger.info("Registrert endring for rekrutteringstreff  ${treffId.somString} med ${jobbsøkereSomSkalVarsles.size} jobbsøkere som skal varsles")
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

    fun avpubliser(treffId: TreffId, navIdent: String) {
        rekrutteringstreffRepository.avpubliser(treffId, navIdent)
    }

    fun opprett(internalDto: OpprettRekrutteringstreffInternalDto) {
        rekrutteringstreffRepository.opprett(internalDto)
    }

    fun oppdater(treffId: TreffId, dto: OppdaterRekrutteringstreffDto, navIdent: String) {
        rekrutteringstreffRepository.oppdater(treffId, dto, navIdent)
    }

    fun hentHendelser(treffId: TreffId): List<RekrutteringstreffHendelse> {
        return rekrutteringstreffRepository.hentHendelser(treffId)
    }

    fun hentAlleHendelser(treffId: TreffId): List<FellesHendelseOutboundDto> {
        return rekrutteringstreffRepository.hentAlleHendelser(treffId)
    }

    fun gjenåpne(treffId: TreffId, navIdent: String) {
        rekrutteringstreffRepository.gjenåpne(treffId, navIdent)
    }
}
