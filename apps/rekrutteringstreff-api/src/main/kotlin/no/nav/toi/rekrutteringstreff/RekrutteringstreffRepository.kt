package no.nav.toi.rekrutteringstreff

import java.sql.ResultSet
import java.sql.Timestamp
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
                stmt.setObject(1, java.util.UUID.randomUUID())
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

    fun hentAlle() = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM $tabellnavn
            """.trimIndent()
        ).use { stmt ->
            stmt.executeQuery().let { resultSet ->
                generateSequence {
                    if(resultSet.next())
                        resultSet.tilRekrutteringstreff()
                    else null
                }.toList()
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
        getString(tittel),
        getTimestamp(fratid).toInstant().atOslo(),
        getTimestamp(tiltid).toInstant().atOslo(),
        getString(sted),
        getString(status),
        getString(opprettetAvPersonNavident),
        getString(opprettetAvPersonNavident)
    )

}


