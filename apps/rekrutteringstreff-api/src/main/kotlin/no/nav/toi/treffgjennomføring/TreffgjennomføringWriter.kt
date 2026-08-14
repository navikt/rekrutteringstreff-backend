package no.nav.toi.treffgjennomføring

import io.javalin.http.NotFoundResponse
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
        block: (Connection, Treffkontekst, Treffgjennomføringsrad) -> Unit,
    ): TreffgjennomføringDto = dataSource.executeInTransaction { connection ->
        val kontekst = hentKontekst(connection, treffId)
        connection.låsTreff(kontekst.treffDbId)
        val rad = faseRepository.sikreRad(connection, kontekst.treffDbId)
        block(connection, kontekst, rad)
        reader.les(connection, kontekst)
    }

    fun hentKontekst(connection: Connection, treffId: TreffId): Treffkontekst =
        kontekstRepository.hent(connection, treffId)
            ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
}
