package no.nav.toi.rekrutteringstreff

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
                val emptyArray = connection.createArrayOf("text", arrayOf<String>())
                stmt.setArray(10, emptyArray)
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

    private companion object {
        const val tabellnavn = "rekrutteringstreff"
        const val tittel = "tittel"
        const val status = "status"
        const val opprettetAvPersonNavident = "opprettet_av_person_navident"
        const val opprettetAvKontorEnhetid = "opprettet_av_kontor_enhetid"
        const val opprettetAvTidspunkt = "opprettet_av_tidspunkt"
        const val fratid = "fratid"
        const val tiltid = "tiltid"
        const val sted = "sted"
        const val eiere = "eiere"
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
