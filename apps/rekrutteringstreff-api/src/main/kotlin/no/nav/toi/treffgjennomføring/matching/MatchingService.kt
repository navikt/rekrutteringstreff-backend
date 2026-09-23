package no.nav.toi.treffgjennomføring.matching

import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import no.nav.toi.HendelseWriter
import no.nav.toi.Miljø
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.StegRepository
import no.nav.toi.treffgjennomføring.TreffgjennomføringSteg
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
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
    private val miljø: Miljø,
) {

    fun settInteresse(treffId: TreffId, dto: InteresseRequestDto): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            kontekst.krevWorkOpEllerLokalUtvikling(miljø)
            val person = PersonTreffId(dto.personTreffId)
            val arbeidsgiver = ArbeidsgiverTreffId(dto.arbeidsgiverTreffId)
            val jobbsøkerId = kontekst.krevJobbsøkerId(person)
            val arbeidsgiverId = kontekst.krevArbeidsgiverId(arbeidsgiver)

            if (dto.interessert && person !in oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)) {
                throw BadRequestResponse("Bare fremmøtte jobbsøkere kan registrere interesse")
            }
            if (!dto.interessert && oppfølgingRepository.finnesForPar(connection, jobbsøkerId, arbeidsgiverId)) {
                throw InteresseKanIkkeFjernesException()
            }

            val endret = repository.settInteresse(connection, jobbsøkerId, arbeidsgiverId, dto.interessert)
            if (!endret) return@skriv

            speilInteresseIFordeling(connection, kontekst, rad.gjeldendeSteg, person, arbeidsgiver, dto.interessert)
            stegRepository.flyttFramTil(connection, rad, TreffgjennomføringSteg.INTERESSE)
        }

    /**
     * Når intervjufordelingen er påbegynt, holdes den i takt med interessene,
     * så ingen faller ut av eller blir hengende igjen i fordelingen.
     */
    private fun speilInteresseIFordeling(
        connection: Connection,
        kontekst: Treffkontekst,
        gjeldendeSteg: TreffgjennomføringSteg,
        person: PersonTreffId,
        arbeidsgiver: ArbeidsgiverTreffId,
        interessert: Boolean,
    ) {
        val fordelinger = repository.hentFor(connection, kontekst).intervjufordelinger
        val erFordelingStartet = fordelinger.isNotEmpty() || gjeldendeSteg >= TreffgjennomføringSteg.FORDELING
        if (!erFordelingStartet) return

        val eksisterende = fordelinger.firstOrNull { it.arbeidsgiverTreffId == arbeidsgiver }
            ?: ArbeidsgiverIntervjufordeling.tom(arbeidsgiver)
        val oppdatert = if (interessert) eksisterende.medPerson(person) else eksisterende.utenPerson(person)
        if (oppdatert != eksisterende) repository.erstattIntervjufordelinger(connection, listOf(oppdatert), kontekst)
    }

    fun lagreIntervjufordeling(
        treffId: TreffId,
        dto: ArbeidsgiverIntervjufordelingDto,
    ): TreffgjennomføringDto = writer.skriv(treffId) { connection, kontekst, rad ->
        kontekst.krevWorkOp()
        MatchingValidering.intervjufordeling(dto.inkludertePersonTreffIder, dto.ekskludertePersonTreffIder)

        val arbeidsgiver = ArbeidsgiverTreffId(dto.arbeidsgiverTreffId)
        kontekst.krevArbeidsgiverId(arbeidsgiver)
        val ny = ArbeidsgiverIntervjufordeling(
            arbeidsgiverTreffId = arbeidsgiver,
            inkludertePersonTreffIder = kontekst.krevJobbsøkere(dto.inkludertePersonTreffIder),
            ekskludertePersonTreffIder = kontekst.krevJobbsøkere(dto.ekskludertePersonTreffIder),
        )
        krevSammePersonerSomInteressene(connection, kontekst, ny)

        repository.erstattIntervjufordelinger(connection, listOf(ny), kontekst)
        stegRepository.flyttFramTil(connection, rad, TreffgjennomføringSteg.FORDELING)
    }

    /**
     * Fordelingen skal inneholde nøyaktig de som har interesse for arbeidsgiveren. Et avvik betyr at klienten
     * bygger på utdatert tilstand, for eksempel en interesse som ble lagt til eller fjernet i mellomtiden.
     * Siden interesse krever oppmøte, sikrer dette også at bare fremmøtte blir fordelt.
     */
    private fun krevSammePersonerSomInteressene(
        connection: Connection,
        kontekst: Treffkontekst,
        fordeling: ArbeidsgiverIntervjufordeling,
    ) {
        val interesserte = repository.hentFor(connection, kontekst).interesser
            .filter { it.arbeidsgiverTreffId == fordeling.arbeidsgiverTreffId }
            .map { it.personTreffId }
            .toSet()
        val fordelte = (fordeling.inkludertePersonTreffIder + fordeling.ekskludertePersonTreffIder).toSet()
        if (fordelte != interesserte) {
            throw ConflictResponse("Fordelingen må inneholde de samme jobbsøkerne som har registrert interesse for arbeidsgiveren")
        }
    }

    private fun Treffkontekst.krevJobbsøkere(personTreffIder: List<String>): List<PersonTreffId> =
        personTreffIder.map(::PersonTreffId).onEach { krevJobbsøkerId(it) }

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
            stegRepository.flyttFramTil(connection, rad, TreffgjennomføringSteg.FORDELING)
        }
}
