package no.nav.toi.treffgjennomføring

import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.dto.tilDto
import no.nav.toi.treffgjennomføring.matching.MatchingRepository
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import java.sql.Connection

class TreffgjennomføringReader(
    private val faseRepository: FaseRepository,
    private val oppmøteRepository: OppmøteRepository,
    private val møteplanRepository: MøteplanRepository,
    private val matchingRepository: MatchingRepository,
    private val oppfølgingRepository: OppfølgingRepository,
) {

    fun les(connection: Connection, kontekst: Treffkontekst): TreffgjennomføringDto {
        val oppmøte = oppmøteRepository.hentFremmøtte(connection, kontekst.treffDbId)

        return Treffgjennomføring(
            fase = faseRepository.hentFase(connection, kontekst.treffDbId) ?: TreffgjennomføringFase.OPPMØTE,
            antallRom = kontekst.antallRom,
            oppmøte = oppmøte,
            deltakernummer = oppmøteRepository.hentDeltakernummer(connection, kontekst.treffDbId),
            møteplan = møteplanRepository.hentFor(connection, kontekst, oppmøte),
            matching = matchingRepository.hentFor(connection, kontekst),
        ).tilDto(
            rekrutteringstreffId = kontekst.treffId.somString,
            vurderinger = oppfølgingRepository.hentForTreff(connection, kontekst.treffDbId),
        )
    }
}
