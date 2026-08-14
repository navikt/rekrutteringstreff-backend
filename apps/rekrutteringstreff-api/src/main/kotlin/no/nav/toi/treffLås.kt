package no.nav.toi

import java.sql.Connection

fun Connection.låsTreff(treffDbId: Long) {
    val sql = "SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE rekrutteringstreff_id = ? FOR UPDATE"
    prepareStatement(sql).use { stmt ->
        stmt.setLong(1, treffDbId)
        stmt.executeQuery().use { it.next() }
    }
}
