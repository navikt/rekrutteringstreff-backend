package no.nav.toi.rekrutteringstreff.eier

import no.nav.toi.rekrutteringstreff.Kolonnenavn
import no.nav.toi.rekrutteringstreff.Tabellnavn
import no.nav.toi.rekrutteringstreff.TreffId
import javax.sql.DataSource

class EierRepository(
    private val dataSource: DataSource,
    private val rekrutteringstreff: Tabellnavn,
    private val eiere: Kolonnenavn,
    private val id: Kolonnenavn
) {
    fun hent(treff: TreffId): List<Eier>? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT $eiere FROM $rekrutteringstreff WHERE $id = ?")
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
    }

    fun leggTilEiere(treff: TreffId, nyeEiere: List<String>) {
        dataSource.connection.use { connection ->
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
    }

    fun slett(treff: TreffId, eier: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    UPDATE $rekrutteringstreff
                    SET $eiere = array_remove($eiere, ?) 
                    WHERE $id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, eier)
                stmt.setObject(2, treff.somUuid)
                stmt.executeUpdate()
            }
        }
    }
}
