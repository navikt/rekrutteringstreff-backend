package no.nav.toi.treffgjennomføring

import java.sql.Connection

data class Treffgjennomføringsrad(val id: Long, val gjeldendeSteg: TreffgjennomføringSteg)

class StegRepository {

    fun hentGjeldendeSteg(connection: Connection, treffDbId: Long): TreffgjennomføringSteg? {
        val sql = "SELECT gjeldende_steg FROM treffgjennomforing WHERE rekrutteringstreff_id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) tilSteg(rs.getString(1)) else null
            }
        }
    }

    fun sikreRad(connection: Connection, treffDbId: Long): Treffgjennomføringsrad {
        val sql = """
            INSERT INTO treffgjennomforing (rekrutteringstreff_id, gjeldende_steg)
            VALUES (?, ?)
            ON CONFLICT (rekrutteringstreff_id) DO UPDATE SET gjeldende_steg = treffgjennomforing.gjeldende_steg
            RETURNING treffgjennomforing_id, gjeldende_steg
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setString(2, TreffgjennomføringSteg.OPPMØTE.name)
            stmt.executeQuery().use { rs ->
                rs.next()
                Treffgjennomføringsrad(rs.getLong(1), tilSteg(rs.getString(2)))
            }
        }
    }

    fun settGjeldendeSteg(
        connection: Connection,
        treffDbId: Long,
        nåværendeSteg: TreffgjennomføringSteg,
        nyttSteg: TreffgjennomføringSteg,
    ) {
        if (nyttSteg.ordinal <= nåværendeSteg.ordinal) return
        connection.prepareStatement("UPDATE treffgjennomforing SET gjeldende_steg = ? WHERE rekrutteringstreff_id = ?").use { stmt ->
            stmt.setString(1, nyttSteg.name)
            stmt.setLong(2, treffDbId)
            stmt.executeUpdate()
        }
    }

    private fun tilSteg(verdi: String?) =
        TreffgjennomføringSteg.entries.firstOrNull { it.name == verdi }
            ?: throw IllegalStateException("Ukjent gjeldende_steg i databasen: $verdi")
}
