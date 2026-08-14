package no.nav.toi.treffgjennomforing

import no.nav.toi.treffgjennomforing.dto.TreffgjennomforingDto
import no.nav.toi.treffgjennomforing.dto.tilDto
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
 * og [no.nav.toi.treffgjennomforing.TreffgjennomføringReaderTest] holder tallet i sjakk.
 */
class TreffgjennomføringReader(
    private val repository: TreffgjennomforingRepository,
) {

    fun les(connection: Connection, kontekst: Treffkontekst): TreffgjennomforingDto =
        repository.hentAggregat(connection, kontekst).tilDto(kontekst.treffId.somString)
}
