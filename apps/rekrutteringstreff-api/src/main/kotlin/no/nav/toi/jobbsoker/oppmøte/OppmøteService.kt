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

/**
 * Eier hele oppmøteoperasjonen: statusoppdatering, hendelse, deltakernummer og
 * kaskadesletting skjer i én transaksjon (via [TreffgjennomføringWriter]).
 */
class OppmøteService(
    private val writer: TreffgjennomføringWriter,
    private val oppmøteRepository: OppmøteRepository,
    private val matchingRepository: MatchingRepository,
    private val møteplanRepository: MøteplanRepository,
    private val oppfølgingRepository: OppfølgingRepository,
    private val hendelseWriter: HendelseWriter,
) {

    fun oppdaterOppmøte(treffId: TreffId, dto: OppmøteRequestDto, navIdent: String): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, _ ->
            val person = PersonTreffId(dto.personTreffId)
            val jobbsøkerId = kontekst.jobbsøkerId(person)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")

            val harMøtt = oppmøteRepository.hentOppmøte(connection, person)?.harMøtt == true
            if (dto.møtt != harMøtt) {
                if (dto.møtt) registrerOppmøte(connection, kontekst, person, jobbsøkerId, navIdent)
                else fjernOppmøte(connection, person, jobbsøkerId, dto.bekreftSlettRegistreringer, navIdent)
            }
        }

    private fun registrerOppmøte(
        connection: Connection,
        kontekst: Treffkontekst,
        person: PersonTreffId,
        jobbsøkerId: Long,
        navIdent: String,
    ) {
        val deltakernummer =
            if (kontekst.erWorkOp) {
                oppmøteRepository.tildelDeltakernummer(connection, kontekst.treffDbId, jobbsøkerId)
            } else null

        oppmøteRepository.settOppmøte(connection, person, Oppmøte.REGISTRERT_OPPMØTE)
        hendelseWriter.forJobbsøker(
            connection, person, Oppmøte.REGISTRERT_OPPMØTE.hendelsestype, navIdent,
            deltakernummer?.let { mapOf("deltakernummer" to it) } ?: emptyMap(),
        )
    }

    private fun fjernOppmøte(
        connection: Connection,
        person: PersonTreffId,
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
        if (registreringer.finnesNoen() && !bekreftet) throw OppmøteHarRegistreringerException(registreringer)

        matchingRepository.slettForJobbsøker(connection, jobbsøkerId)
        møteplanRepository.slettRomForJobbsøker(connection, jobbsøkerId)
        oppfølgingRepository.slettForJobbsøker(connection, jobbsøkerId)
        oppmøteRepository.settOppmøte(connection, person, Oppmøte.REGISTRERT_OPPMØTE_FJERNET)
        hendelseWriter.forJobbsøker(
            connection, person, Oppmøte.REGISTRERT_OPPMØTE_FJERNET.hendelsestype, navIdent,
            mapOf(
                "interesser" to registreringer.interesser,
                "intervjuplasser" to registreringer.intervjuplasser,
                "vurderinger" to registreringer.vurderinger,
            ),
        )
    }
}
