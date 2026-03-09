package no.nav.toi.rekrutteringstreff.eier

import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import javax.sql.DataSource

class EierRepository(
    private val dataSource: DataSource,
) {
    companion object {
        private const val rekrutteringstreff = "rekrutteringstreff"
        private const val eiere = "eiere"
        private const val id = "id"
    }


    fun hent(treff: TreffId): List<Eier>? {
        dataSource.connection.use { connection ->
            return hent(connection, treff)
        }
    }

    fun hent(connection: Connection, treff: TreffId, forUpdate: Boolean = false): List<Eier>? {
        val sql = "SELECT $eiere FROM $rekrutteringstreff WHERE $id = ?" +
            if (forUpdate) " FOR UPDATE" else ""
        connection.prepareStatement(sql)
            .use { stmt ->
                stmt.setObject(1, treff.somUuid)
                val resultSet = stmt.executeQuery()
                return if (resultSet.next()) {
                    (resultSet.getArray("$eiere").array as Array<*>)
                        .map(Any?::toString)
                        .map(::Eier)
                } else null
            }
    }

    fun leggTil(treff: TreffId, nyeEiere: List<String>) {
        dataSource.connection.use { connection ->
            leggTil(connection, treff, nyeEiere)
        }
    }

    fun leggTil(connection: Connection, treff: TreffId, nyeEiere: List<String>) {
        connection.prepareStatement(
                """
                    UPDATE $rekrutteringstreff
                    SET $eiere = array(SELECT DISTINCT unnest(array_cat($eiere, ?)))
                    WHERE $id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setArray(1, connection.createArrayOf("text", nyeEiere.toTypedArray()))
                stmt.setObject(2, treff.somUuid)
                stmt.executeUpdate()
            }
    }

    fun slett(treff: TreffId, eier: String): Boolean {
        dataSource.connection.use { connection ->
            return slett(connection, treff, eier)
        }
    }

    fun slett(connection: Connection, treff: TreffId, eier: String): Boolean {
        connection.prepareStatement(
            """
                    UPDATE $rekrutteringstreff
                    SET $eiere = array_remove($eiere, ?) 
                    WHERE $id = ? AND array_length($eiere, 1) > 1
                """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, eier)
            stmt.setObject(2, treff.somUuid)
            return stmt.executeUpdate() > 0
        }
    }
}
