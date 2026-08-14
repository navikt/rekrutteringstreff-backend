package no.nav.toi.treffgjennomføring

import java.sql.Connection

data class Treffgjennomføringsrad(val id: Long, val fase: TreffgjennomføringFase)

class FaseRepository {

    fun hentFase(connection: Connection, treffDbId: Long): TreffgjennomføringFase? {
        val sql = "SELECT fase FROM treffgjennomforing WHERE rekrutteringstreff_id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) tilFase(rs.getString(1)) else null
            }
        }
    }

    /**
     * Oppretter raden hvis den mangler. Møteoppsettet trenger `treffgjennomforing_id`
     * på grunn av fremmednøkkelen. Serialiseringa tas av [no.nav.toi.låsTreff].
     */
    fun sikreRad(connection: Connection, treffDbId: Long): Treffgjennomføringsrad {
        val sql = """
            INSERT INTO treffgjennomforing (rekrutteringstreff_id, fase)
            VALUES (?, ?)
            ON CONFLICT (rekrutteringstreff_id) DO UPDATE SET fase = treffgjennomforing.fase
            RETURNING treffgjennomforing_id, fase
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setString(2, TreffgjennomføringFase.OPPMØTE.name)
            stmt.executeQuery().use { rs ->
                rs.next()
                Treffgjennomføringsrad(rs.getLong(1), tilFase(rs.getString(2)))
            }
        }
    }

    /** Fasen går bare framover — en lavere fase enn den lagrede er en no-op. */
    fun settFase(
        connection: Connection,
        treffDbId: Long,
        nåværende: TreffgjennomføringFase,
        ny: TreffgjennomføringFase,
    ) {
        if (ny.ordinal <= nåværende.ordinal) return
        connection.prepareStatement("UPDATE treffgjennomforing SET fase = ? WHERE rekrutteringstreff_id = ?").use { stmt ->
            stmt.setString(1, ny.name)
            stmt.setLong(2, treffDbId)
            stmt.executeUpdate()
        }
    }

    private fun tilFase(verdi: String?) =
        TreffgjennomføringFase.entries.firstOrNull { it.name == verdi } ?: TreffgjennomføringFase.OPPMØTE
}
