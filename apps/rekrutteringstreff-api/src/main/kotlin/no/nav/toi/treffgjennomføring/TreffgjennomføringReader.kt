package no.nav.toi.treffgjennomføring

import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.dto.tilDto
import java.sql.Connection

/**
 * Eier sammensettinga av svaret. Samtlige endepunkter – også skriveoperasjonene –
 * returnerer hele aggregatet, og det er denne ene veien inn til det.
 *
 * Poenget er å ha ett sted som kjenner DTO-en når domenet deles opp i møteplan,
 * matching og oppfølging. Uten readeren ville hvert subdomene måttet lese alt for å
 * kunne svare, og da ville oppdelinga vært reversert av lesevegen.
 *
 * Lesinga skal skje i få, store kall. I dag er det ti spørringer på én connection,
 * og [no.nav.toi.treffgjennomføring.TreffgjennomføringReaderTest] holder tallet i sjakk.
 */
class TreffgjennomføringReader(
    private val repository: TreffgjennomføringRepository,
    private val oppfølgingRepository: OppfølgingRepository,
) {

    fun les(connection: Connection, kontekst: Treffkontekst): TreffgjennomføringDto =
        repository.hentAggregat(connection, kontekst)
            .tilDto(
                rekrutteringstreffId = kontekst.treffId.somString,
                vurderinger = oppfølgingRepository.hentForTreff(connection, kontekst.treffDbId),
            )
}
