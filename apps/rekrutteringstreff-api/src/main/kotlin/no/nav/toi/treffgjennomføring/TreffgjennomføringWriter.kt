package no.nav.toi.treffgjennomføring

import no.nav.toi.executeInTransaction
import no.nav.toi.låsTreff
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import java.sql.Connection
import javax.sql.DataSource

class TreffgjennomføringWriter(
    private val dataSource: DataSource,
    private val kontekstRepository: TreffkontekstRepository,
    private val faseRepository: FaseRepository,
    private val reader: TreffgjennomføringReader,
) {

    fun skriv(
        treffId: TreffId,
        operasjon: (Connection, Treffkontekst, Treffgjennomføringsrad) -> Unit,
    ): TreffgjennomføringDto = dataSource.executeInTransaction { connection ->
        connection.låsTreff(treffId)
        val kontekst = kontekstRepository.krevKontekst(connection, treffId)
        val rad = faseRepository.sikreRad(connection, kontekst.treffDbId)
        operasjon(connection, kontekst, rad)
        reader.les(connection, kontekst)
    }
}
