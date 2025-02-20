package no.nav.toi.rekrutteringstreff.rekrutteringstreff

import no.nav.toi.rekrutteringstreff.Status
import no.nav.toi.rekrutteringstreff.atOslo
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.eier.Eier
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class RekrutteringstreffRepository(private val dataSource: DataSource) {

    fun opprett(dto: OpprettRekrutteringstreffDto, navIdent: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO $tabellnavn 
                (id, $tittel, $status, $opprettetAvPersonNavident, $opprettetAvKontorEnhetid, $opprettetAvTidspunkt, $fratid, $tiltid, $sted, $eiere)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setString(2, dto.tittel)
                stmt.setString(3, Status.Utkast.name)
                stmt.setString(4, navIdent)
                stmt.setString(5, dto.opprettetAvNavkontorEnhetId)
                stmt.setTimestamp(6, Timestamp.from(java.time.Instant.now()))
                stmt.setTimestamp(7, Timestamp.from(dto.fraTid.toInstant()))
                stmt.setTimestamp(8, Timestamp.from(dto.tilTid.toInstant()))
                stmt.setString(9, dto.sted)
                stmt.setArray(10, connection.createArrayOf("text", arrayOf(navIdent)))
                stmt.executeUpdate()
            }
        }
    }

    fun oppdater(id: UUID, dto: OppdaterRekrutteringstreffDto, navIdent: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE $tabellnavn 
                SET $tittel = ?, $fratid = ?, $tiltid = ?, $sted = ?
                WHERE id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, dto.tittel)
                stmt.setTimestamp(2, Timestamp.from(dto.fraTid.toInstant()))
                stmt.setTimestamp(3, Timestamp.from(dto.tilTid.toInstant()))
                stmt.setString(4, dto.sted)
                stmt.setObject(5, id)
                stmt.executeUpdate()
            }
        }
    }

    fun slett(id: UUID) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM $tabellnavn WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                stmt.executeUpdate()
            }
        }
    }

    fun hentAlle() = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM $tabellnavn").use { stmt ->
            stmt.executeQuery().let { resultSet ->
                generateSequence {
                    if (resultSet.next())
                        resultSet.tilRekrutteringstreff()
                    else null
                }.toList()
            }
        }
    }

    fun hent(id: UUID): Rekrutteringstreff? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM $tabellnavn WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                val resultSet = stmt.executeQuery()
                return if (resultSet.next()) resultSet.tilRekrutteringstreff() else null
            }
        }
    }

    val eierRepository = EierRepository(dataSource)

    companion object {
        private const val tabellnavn = "rekrutteringstreff"
        private const val tittel = "tittel"
        private const val status = "status"
        private const val opprettetAvPersonNavident = "opprettet_av_person_navident"
        private const val opprettetAvKontorEnhetid = "opprettet_av_kontor_enhetid"
        private const val opprettetAvTidspunkt = "opprettet_av_tidspunkt"
        private const val fratid = "fratid"
        private const val tiltid = "tiltid"
        private const val sted = "sted"
        private const val eiere = "eiere"

        class EierRepository(private val dataSource: DataSource) {
            fun hent(id: UUID): List<Eier>? {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT $eiere FROM $tabellnavn WHERE id = ?").use { stmt ->
                        stmt.setObject(1, id)
                        val resultSet = stmt.executeQuery()
                        return if (resultSet.next()) {
                            (resultSet.getArray("eiere").array as Array<*>)
                                .map(Any?::toString)
                                .map(::Eier)
                        } else null
                    }
                }
            }
        }
    }

    private fun ResultSet.tilRekrutteringstreff() = Rekrutteringstreff(
        id = getObject("id", UUID::class.java),
        tittel = getString(tittel),
        fraTid = getTimestamp(fratid).toInstant().atOslo(),
        tilTid = getTimestamp(tiltid).toInstant().atOslo(),
        sted = getString(sted),
        status = getString(status),
        opprettetAvPersonNavident = getString(opprettetAvPersonNavident),
        opprettetAvNavkontorEnhetId = getString(opprettetAvKontorEnhetid)
    )
}
