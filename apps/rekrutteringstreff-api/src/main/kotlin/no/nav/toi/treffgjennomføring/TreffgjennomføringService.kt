package no.nav.toi.treffgjennomføring

import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import javax.sql.DataSource

class TreffgjennomføringService(
    private val dataSource: DataSource,
    private val kontekstRepository: TreffkontekstRepository,
    private val reader: TreffgjennomføringReader,
    private val writer: TreffgjennomføringWriter,
    private val stegRepository: StegRepository,
) {

    fun hent(treffId: TreffId): TreffgjennomføringDto = dataSource.executeInTransaction { connection ->
        reader.les(connection, kontekstRepository.krevKontekst(connection, treffId))
    }

    fun settGjeldendeSteg(treffId: TreffId, steg: TreffgjennomføringSteg): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            stegRepository.settGjeldendeSteg(connection, kontekst.treffDbId, rad.gjeldendeSteg, steg)
        }
}
