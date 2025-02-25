package no.nav.toi.rekrutteringstreff.rekrutteringstreff.eier

import no.nav.toi.rekrutteringstreff.rekrutteringstreff.Kolonnenavn
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.Tabellnavn
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.TreffId
import java.util.*
import javax.sql.DataSource


class EierRepository(
    private val dataSource: DataSource,
    private val rekrutteringstreff: Tabellnavn,
    private val eiere: Kolonnenavn,
    id: Kolonnenavn
) {
    private val idKolonne = id
    fun hent(id: TreffId): List<Eier>? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT $eiere FROM $rekrutteringstreff WHERE $idKolonne = ?").use { stmt ->
                stmt.setObject(1, id.somUuid)
                val resultSet = stmt.executeQuery()
                return if (resultSet.next()) {
                    (resultSet.getArray("eiere").array as Array<*>)
                        .map(Any?::toString)
                        .map(::Eier)
                } else null
            }
        }
    }

    fun leggTilEiere(id: TreffId, nyeEiere: List<String>) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE $rekrutteringstreff SET $eiere = array_cat($eiere, array_remove(?, unnest($eiere))) WHERE $idKolonne = ?").use { stmt ->
                stmt.setArray(1, connection.createArrayOf("text", nyeEiere.toTypedArray()))
                stmt.setObject(2, id.somUuid)
                stmt.executeUpdate()
            }
        }
    }
}