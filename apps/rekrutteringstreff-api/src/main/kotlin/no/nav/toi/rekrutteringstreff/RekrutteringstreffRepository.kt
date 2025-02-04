package no.nav.toi.rekrutteringstreff

import java.sql.Timestamp
import javax.sql.DataSource

class RekrutteringstreffRepository(private val dataSource: DataSource) {
    fun opprett(dto: OpprettRekrutteringstreffDto, navIdent: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO rekrutteringstreff 
                (id, tittel, status, opprettet_av_person, opprettet_av_kontor, opprettet_av_tidspunkt, fratid, tiltid, sted, eiere)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, java.util.UUID.randomUUID())
                stmt.setString(2, dto.tittel)
                stmt.setString(3, Status.Utkast.name)
                stmt.setString(4, navIdent)
                stmt.setString(5, dto.kontor)
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
}

