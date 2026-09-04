package no.nav.toi.treffgjennomføring.matching

import io.javalin.http.BadRequestResponse
import no.nav.toi.HendelseWriter
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.StegRepository
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.TreffgjennomføringSteg
import no.nav.toi.treffgjennomføring.Treffkontekst
import no.nav.toi.treffgjennomføring.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomføring.dto.InteresseRequestDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import java.sql.Connection

class MatchingService(
    private val writer: TreffgjennomføringWriter,
    private val repository: MatchingRepository,
    private val oppmøteRepository: OppmøteRepository,
    private val oppfølgingRepository: OppfølgingRepository,
    private val stegRepository: StegRepository,
    private val hendelseWriter: HendelseWriter,
) {

    fun settInteresse(treffId: TreffId, dto: InteresseRequestDto): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            val person = PersonTreffId(dto.personTreffId)
            val arbeidsgiver = ArbeidsgiverTreffId(dto.arbeidsgiverTreffId)
            val jobbsøkerId = kontekst.jobbsøkerId(person)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
            val arbeidsgiverId = kontekst.arbeidsgiverId(arbeidsgiver)
                ?: throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

            if (dto.interessert && person !in oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)) {
                throw BadRequestResponse("Bare fremmøtte jobbsøkere kan registrere interesse")
            }
            if (!dto.interessert && oppfølgingRepository.finnesForPar(connection, jobbsøkerId, arbeidsgiverId)) {
                throw InteresseKanIkkeFjernesException()
            }

            if (!repository.settInteresse(connection, jobbsøkerId, arbeidsgiverId, dto.interessert)) return@skriv

            speilInteresseIFordeling(connection, kontekst, person, arbeidsgiver, dto.interessert)
            stegRepository.settGjeldendeSteg(connection, kontekst.treffDbId, rad.gjeldendeSteg, TreffgjennomføringSteg.INTERESSE)
        }

    private fun speilInteresseIFordeling(
        connection: Connection,
        kontekst: Treffkontekst,
        person: PersonTreffId,
        arbeidsgiver: ArbeidsgiverTreffId,
        interessert: Boolean,
    ) {
        val eksisterende = repository.hentFor(connection, kontekst).intervjufordelinger
            .firstOrNull { it.arbeidsgiverTreffId == arbeidsgiver } ?: return

        val oppdatert = if (interessert) {
            if (person in eksisterende.inkludertePersonTreffIder || person in eksisterende.ekskludertePersonTreffIder) return
            eksisterende.copy(inkludertePersonTreffIder = eksisterende.inkludertePersonTreffIder + person)
        } else {
            eksisterende.copy(
                inkludertePersonTreffIder = eksisterende.inkludertePersonTreffIder - person,
                ekskludertePersonTreffIder = eksisterende.ekskludertePersonTreffIder - person,
            )
        }
        repository.erstattIntervjufordelinger(connection, listOf(oppdatert), kontekst)
    }

    fun lagreIntervjufordeling(
        treffId: TreffId,
        dto: ArbeidsgiverIntervjufordelingDto,
    ): TreffgjennomføringDto = writer.skriv(treffId) { connection, kontekst, rad ->
        kontekst.krevWorkOp()
        MatchingValidering.intervjufordeling(dto.inkludertePersonTreffIder, dto.ekskludertePersonTreffIder)

        val arbeidsgiver = ArbeidsgiverTreffId(dto.arbeidsgiverTreffId)
        if (!kontekst.erArbeidsgiverPåTreff(arbeidsgiver)) throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

        val ny = ArbeidsgiverIntervjufordeling(
            arbeidsgiverTreffId = arbeidsgiver,
            inkludertePersonTreffIder = dto.inkludertePersonTreffIder.map(::PersonTreffId).krevPåTreff(kontekst),
            ekskludertePersonTreffIder = dto.ekskludertePersonTreffIder.map(::PersonTreffId).krevPåTreff(kontekst),
        )

        repository.erstattIntervjufordelinger(connection, listOf(ny), kontekst)
        stegRepository.settGjeldendeSteg(connection, kontekst.treffDbId, rad.gjeldendeSteg, TreffgjennomføringSteg.FORDELING)
    }

    private fun List<PersonTreffId>.krevPåTreff(kontekst: Treffkontekst): List<PersonTreffId> = also {
        firstOrNull { !kontekst.erPersonPåTreff(it) }?.let {
            throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
        }
    }

    fun fordelIntervjuer(treffId: TreffId, navIdent: String): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            kontekst.krevWorkOp()
            val matching = repository.hentFor(connection, kontekst)
            val fordelinger = Intervjufordeler.fordel(
                interesser = matching.interesser,
                eksisterendeFordelinger = matching.intervjufordelinger,
                arbeidsgivere = kontekst.arbeidsgiverTreffIder,
            )
            repository.erstattIntervjufordelinger(connection, fordelinger, kontekst)

            hendelseWriter.forTreff(
                connection, treffId,
                RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_INTERVJUFORDELING_FORDELT, navIdent,
                mapOf(
                    "antallArbeidsgivere" to fordelinger.size,
                    "antallPlasseringer" to fordelinger.sumOf { it.inkludertePersonTreffIder.size },
                ),
            )
            stegRepository.settGjeldendeSteg(connection, kontekst.treffDbId, rad.gjeldendeSteg, TreffgjennomføringSteg.FORDELING)
        }
}
