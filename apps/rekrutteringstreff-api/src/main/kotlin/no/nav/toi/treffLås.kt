package no.nav.toi

import io.javalin.http.NotFoundResponse
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import javax.sql.DataSource

fun <T> DataSource.medLåstTreff(treffId: TreffId, block: (Connection) -> T): T =
    executeInLockingTransaction { connection ->
        connection.låsTreff(treffId)
        block(connection)
    }

/** Radlås på treffet som serialiserer alle skrivinger i treffgjennomføringen. */
fun Connection.låsTreff(treffId: TreffId) {
    val sql = "SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ? FOR UPDATE"
    prepareStatement(sql).use { stmt ->
        stmt.setObject(1, treffId.somUuid)
        stmt.executeQuery().use {
            if (!it.next()) throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
        }
    }
}
