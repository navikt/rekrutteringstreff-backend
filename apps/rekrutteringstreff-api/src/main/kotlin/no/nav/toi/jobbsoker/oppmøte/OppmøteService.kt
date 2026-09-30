package no.nav.toi.jobbsoker.oppmøte

import no.nav.toi.HendelseWriter
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.RegistreringerRepository
import no.nav.toi.treffgjennomføring.Treffkontekst
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import no.nav.toi.treffgjennomføring.møteplan.Møteplansynk
import java.sql.Connection

class OppmøteService(
    private val treffgjennomføringWriter: TreffgjennomføringWriter,
    private val oppmøteRepository: OppmøteRepository,
    private val registreringerRepository: RegistreringerRepository,
    private val møteplanRepository: MøteplanRepository,
    private val møteplansynk: Møteplansynk,
    private val jobbsøkerService: JobbsøkerService,
    private val hendelseWriter: HendelseWriter,
) {

    fun oppdaterOppmøte(treffId: TreffId, oppmøteRequestDto: OppmøteRequestDto, navIdent: String): TreffgjennomføringDto =
        treffgjennomføringWriter.skriv(treffId) { connection, kontekst, _ ->
            val personTreffId = PersonTreffId(oppmøteRequestDto.personTreffId)
            val jobbsøkerId = kontekst.krevJobbsøkerId(personTreffId)

            val harMøtt = personTreffId in oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
            if (oppmøteRequestDto.møtt == harMøtt) return@skriv

            møteplansynk.medLagretMøteplan(connection, treffId) {
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
        val registreringer = registreringerRepository.hentForJobbsøker(connection, jobbsøkerId)
        if (registreringer.finnesRegistreringer()) throw OppmøteKanIkkeFjernesException(registreringer)

        møteplanRepository.slettRomForJobbsøker(connection, jobbsøkerId)
        jobbsøkerService.fjernOppmøte(connection, personTreffId)
        hendelseWriter.forJobbsøker(
            connection, personTreffId, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET, navIdent,
        )
    }
}
