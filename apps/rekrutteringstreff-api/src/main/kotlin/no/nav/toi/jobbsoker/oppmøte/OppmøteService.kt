package no.nav.toi.jobbsoker.oppmøte

import io.javalin.http.BadRequestResponse
import no.nav.toi.HendelseWriter
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.Treffkontekst
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.matching.MatchingRepository
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import java.sql.Connection

class OppmøteService(
    private val treffgjennomføringWriter: TreffgjennomføringWriter,
    private val oppmøteRepository: OppmøteRepository,
    private val matchingRepository: MatchingRepository,
    private val møteplanRepository: MøteplanRepository,
    private val oppfølgingRepository: OppfølgingRepository,
    private val jobbsøkerService: JobbsøkerService,
    private val hendelseWriter: HendelseWriter,
) {

    fun oppdaterOppmøte(treffId: TreffId, oppmøteRequestDto: OppmøteRequestDto, navIdent: String): TreffgjennomføringDto =
        treffgjennomføringWriter.skriv(treffId) { connection, kontekst, _ ->
            val personTreffId = PersonTreffId(oppmøteRequestDto.personTreffId)
            val jobbsøkerId = kontekst.jobbsøkerId(personTreffId)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")

            val harMøtt = personTreffId in oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
            if (oppmøteRequestDto.møtt != harMøtt) {
                if (oppmøteRequestDto.møtt) registrerOppmøte(connection, kontekst, personTreffId, jobbsøkerId, navIdent)
                else fjernOppmøte(connection, personTreffId, jobbsøkerId, navIdent)
            }
        }

    private fun registrerOppmøte(
        connection: Connection,
        treffkontekst: Treffkontekst,
        personTreffId: PersonTreffId,
        jobbsøkerId: Long,
        navIdent: String,
    ) {
        val deltakernummer =
            if (treffkontekst.erWorkOp) {
                oppmøteRepository.tildelDeltakernummer(connection, treffkontekst.treffDbId, jobbsøkerId)
            } else null

        jobbsøkerService.registrerOppmøte(connection, personTreffId)
        hendelseWriter.forJobbsøker(
            connection, personTreffId, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE, navIdent,
            deltakernummer?.let { mapOf("deltakernummer" to it) } ?: emptyMap(),
        )
    }

    private fun fjernOppmøte(
        connection: Connection,
        personTreffId: PersonTreffId,
        jobbsøkerId: Long,
        navIdent: String,
    ) {
        val registreringer = Registreringer(
            interesser = matchingRepository.tellInteresserForJobbsøker(connection, jobbsøkerId),
            vurderinger = oppfølgingRepository.tellForJobbsøker(connection, jobbsøkerId),
        )
        // Registreringer forutsetter oppmøte, så de må ryddes først. Da kan ingenting gå tapt her.
        if (registreringer.finnesRegistreringer()) throw OppmøteKanIkkeFjernesException(registreringer)

        møteplanRepository.slettRomForJobbsøker(connection, jobbsøkerId)
        jobbsøkerService.fjernOppmøte(connection, personTreffId)
        hendelseWriter.forJobbsøker(
            connection, personTreffId, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET, navIdent,
        )
    }
}
