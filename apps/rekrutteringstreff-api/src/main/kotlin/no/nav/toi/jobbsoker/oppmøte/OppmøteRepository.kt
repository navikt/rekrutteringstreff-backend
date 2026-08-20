package no.nav.toi.jobbsoker.oppmøte

import no.nav.toi.jobbsoker.Oppmøte
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.tilListe
import java.sql.Connection

data class Deltakernummer(val personTreffId: PersonTreffId, val nummer: Int)

class OppmøteRepository {

    fun hentFremmøtteJobbsøkere(connection: Connection, treffDbId: Long): List<PersonTreffId> {
        val sql = """
            SELECT j.id::text
            FROM jobbsoker j
            LEFT JOIN deltakernummer d ON d.jobbsoker_id = j.jobbsoker_id
            WHERE j.rekrutteringstreff_id = ?
              AND j.status != 'SLETTET'
              AND j.oppmote = ?
            ORDER BY d.nummer NULLS LAST, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setString(2, Oppmøte.REGISTRERT_OPPMØTE.name)
            stmt.executeQuery().use { rs -> rs.tilListe { PersonTreffId(it.getString(1)) } }
        }
    }

    fun hentDeltakernumre(connection: Connection, treffDbId: Long): List<Deltakernummer> {
        val sql = """
            SELECT j.id::text, d.nummer
            FROM deltakernummer d
            JOIN jobbsoker j ON j.jobbsoker_id = d.jobbsoker_id
            WHERE d.rekrutteringstreff_id = ? AND j.status != 'SLETTET'
            ORDER BY d.nummer
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { Deltakernummer(PersonTreffId(it.getString(1)), it.getInt(2)) }
            }
        }
    }

    fun tildelDeltakernummer(connection: Connection, treffDbId: Long, jobbsøkerId: Long): Int {
        val sql = """
            INSERT INTO deltakernummer (rekrutteringstreff_id, jobbsoker_id, nummer)
            SELECT ?, ?, COALESCE(MAX(nummer), 0) + 1 FROM deltakernummer WHERE rekrutteringstreff_id = ?
            ON CONFLICT (rekrutteringstreff_id, jobbsoker_id) DO NOTHING
            RETURNING nummer
        """.trimIndent()
        val nytt = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setLong(2, jobbsøkerId)
            stmt.setLong(3, treffDbId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }
        return nytt ?: hentDeltakernummerFor(connection, treffDbId, jobbsøkerId)
    }

    private fun hentDeltakernummerFor(connection: Connection, treffDbId: Long, jobbsøkerId: Long): Int {
        val sql = "SELECT nummer FROM deltakernummer WHERE rekrutteringstreff_id = ? AND jobbsoker_id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setLong(2, jobbsøkerId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun settOppmøte(connection: Connection, personTreffId: PersonTreffId, oppmøte: Oppmøte) {
        connection.prepareStatement(
            """
            UPDATE jobbsoker
            SET oppmote=?
            WHERE id=?
            """
        ).use { stmt ->
            stmt.setString(1, oppmøte.name)
            stmt.setObject(2, personTreffId.somUuid)
            stmt.executeUpdate()
        }
    }

    fun hentOppmøte(connection: Connection, personTreffId: PersonTreffId): Oppmøte? =
        connection.prepareStatement("SELECT oppmote FROM jobbsoker WHERE id=?").use { stmt ->
            stmt.setObject(1, personTreffId.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) Oppmøte.fraDatabase(rs.getString("oppmote")) else null
            }
        }
}
