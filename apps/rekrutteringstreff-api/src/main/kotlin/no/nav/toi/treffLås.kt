package no.nav.toi

import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection

/** Radlås på treffet som serialiserer alle skrivinger i treffgjennomføringen. */
fun Connection.låsTreff(treffId: TreffId) {
    val sql = "SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ? FOR UPDATE"
    prepareStatement(sql).use { stmt ->
        stmt.setObject(1, treffId.somUuid)
        stmt.executeQuery().use { it.next() }
    }
}
