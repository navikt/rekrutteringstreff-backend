package no.nav.toi.jobbsoker.oppmøte

import io.javalin.http.BadRequestResponse
import no.nav.toi.HendelseWriter
import no.nav.toi.jobbsoker.Oppmøte
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
    private val hendelseWriter: HendelseWriter,
) {

    fun oppdaterOppmøte(treffId: TreffId, oppmøteRequestDto: OppmøteRequestDto, navIdent: String): TreffgjennomføringDto =
        treffgjennomføringWriter.skriv(treffId) { connection, kontekst, _ ->
            val personTreffId = PersonTreffId(oppmøteRequestDto.personTreffId)
            val jobbsøkerId = kontekst.jobbsøkerId(personTreffId)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")

            val harMøtt = oppmøteRepository.hentOppmøte(connection, personTreffId)?.harMøtt == true
            if (oppmøteRequestDto.møtt != harMøtt) {
                if (oppmøteRequestDto.møtt) registrerOppmøte(connection, kontekst, personTreffId, jobbsøkerId, navIdent)
                else fjernOppmøte(connection, personTreffId, jobbsøkerId, oppmøteRequestDto.bekreftSlettRegistreringer, navIdent)
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

        oppmøteRepository.settOppmøte(connection, personTreffId, Oppmøte.REGISTRERT_OPPMØTE)
        hendelseWriter.forJobbsøker(
            connection, personTreffId, Oppmøte.REGISTRERT_OPPMØTE.hendelsestype, navIdent,
            deltakernummer?.let { mapOf("deltakernummer" to it) } ?: emptyMap(),
        )
    }

    private fun fjernOppmøte(
        connection: Connection,
        personTreffId: PersonTreffId,
        jobbsøkerId: Long,
        bekreftet: Boolean,
        navIdent: String,
    ) {
        val (interesser, intervjuplasser) = matchingRepository.tellForJobbsøker(connection, jobbsøkerId)
        val registreringer = Registreringer(
            interesser = interesser,
            intervjuplasser = intervjuplasser,
            vurderinger = oppfølgingRepository.tellForJobbsøker(connection, jobbsøkerId),
        )
        if (registreringer.finnesRegistreringer() && !bekreftet) throw OppmøteHarRegistreringerException(registreringer)

        matchingRepository.slettForJobbsøker(connection, jobbsøkerId)
        møteplanRepository.slettRomForJobbsøker(connection, jobbsøkerId)
        oppfølgingRepository.slettForJobbsøker(connection, jobbsøkerId)
        oppmøteRepository.settOppmøte(connection, personTreffId, Oppmøte.REGISTRERT_OPPMØTE_FJERNET)
        hendelseWriter.forJobbsøker(
            connection, personTreffId, Oppmøte.REGISTRERT_OPPMØTE_FJERNET.hendelsestype, navIdent,
            mapOf(
                "interesser" to registreringer.interesser,
                "intervjuplasser" to registreringer.intervjuplasser,
                "vurderinger" to registreringer.vurderinger,
            ),
        )
    }
}
