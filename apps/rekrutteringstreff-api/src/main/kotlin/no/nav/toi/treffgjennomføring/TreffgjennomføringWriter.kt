package no.nav.toi.treffgjennomføring

import no.nav.toi.medLåstTreff
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import java.sql.Connection
import javax.sql.DataSource

class TreffgjennomføringWriter(
    private val dataSource: DataSource,
    private val kontekstRepository: TreffkontekstRepository,
    private val stegRepository: StegRepository,
    private val reader: TreffgjennomføringReader,
) {

    fun skriv(
        treffId: TreffId,
        operasjon: (Connection, Treffkontekst, Treffgjennomføringsrad) -> Unit,
    ): TreffgjennomføringDto = dataSource.medLåstTreff(treffId) { connection ->
        val kontekst = kontekstRepository.krevKontekst(connection, treffId)
        val rad = stegRepository.sikreRad(connection, kontekst.treffDbId)
        operasjon(connection, kontekst, rad)
        reader.les(connection, kontekst)
    }
}
