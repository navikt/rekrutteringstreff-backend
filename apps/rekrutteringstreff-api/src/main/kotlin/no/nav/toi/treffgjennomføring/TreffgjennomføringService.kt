package no.nav.toi.treffgjennomføring

import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import javax.sql.DataSource

class TreffgjennomføringService(
    private val dataSource: DataSource,
    private val writer: TreffgjennomføringWriter,
    private val reader: TreffgjennomføringReader,
) {

    fun hent(treffId: TreffId): TreffgjennomføringDto = dataSource.executeInTransaction { connection ->
        reader.les(connection, writer.hentKontekst(connection, treffId))
    }
}
