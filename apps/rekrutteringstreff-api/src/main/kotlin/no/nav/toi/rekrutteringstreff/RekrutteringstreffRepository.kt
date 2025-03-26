package no.nav.toi.rekrutteringstreff

import no.nav.toi.Status
import no.nav.toi.atOslo
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.sql.DataSource

class RekrutteringstreffRepository(private val dataSource: DataSource) {

    fun opprett(dto: OpprettRekrutteringstreffInternalDto): TreffId {
        val nyTreffId = TreffId(UUID.randomUUID())
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
            INSERT INTO $tabellnavn 
            ($id, $tittel, $status, $opprettetAvPersonNavident, $opprettetAvKontorEnhetid, $opprettetAvTidspunkt, $eiere)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            ).use { stmt ->
                var c = 0
                stmt.setObject(++c, nyTreffId.somUuid)
                stmt.setString(++c, dto.tittel)
                stmt.setString(++c, Status.Utkast.name)
                stmt.setString(++c, dto.opprettetAvPersonNavident)
                stmt.setString(++c, dto.opprettetAvNavkontorEnhetId)
                stmt.setTimestamp(++c, Timestamp.from(Instant.now()))
                stmt.setArray(++c, connection.createArrayOf("text", arrayOf(dto.opprettetAvPersonNavident)))
                stmt.executeUpdate()
            }
        }
        return nyTreffId
    }

    fun oppdater(treff: TreffId, dto: OppdaterRekrutteringstreffDto) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE $tabellnavn 
                SET $tittel = ?, $beskrivelse = ?, $fratid = ?, $tiltid = ?, $sted = ?
                WHERE $id = ?
                """.trimIndent()
            ).use { stmt ->
                var c = 0
                stmt.setString(++c, dto.tittel)
                stmt.setString(++c, dto.beskrivelse)
                stmt.setTimestamp(++c, Timestamp.from(dto.fraTid.toInstant()))
                stmt.setTimestamp(++c, Timestamp.from(dto.tilTid.toInstant()))
                stmt.setString(++c, dto.sted)
                stmt.setObject(++c, treff.somUuid)
                stmt.executeUpdate()
            }
        }
    }

    fun slett(treff: TreffId) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM $tabellnavn WHERE $id = ?").use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeUpdate()
            }
        }
    }

    fun hentAlle() = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM $tabellnavn").use { stmt ->
            stmt.executeQuery().let { resultSet ->
                generateSequence {
                    if (resultSet.next()) resultSet.tilRekrutteringstreff()
                    else null
                }.toList()
            }
        }
    }

    fun hent(treff: TreffId): Rekrutteringstreff? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM $tabellnavn WHERE $id = ?").use { stmt ->
                stmt.setObject(1, treff.somUuid)
                val resultSet = stmt.executeQuery()
                return if (resultSet.next()) resultSet.tilRekrutteringstreff() else null
            }
        }
    }

    val eierRepository = EierRepository(
        dataSource,
        rekrutteringstreff = Tabellnavn(tabellnavn),
        eiere = Kolonnenavn(eiere),
        id = Kolonnenavn(id)
    )

    companion object {
        private const val tabellnavn = "rekrutteringstreff"
        private const val id = "id"
        private const val tittel = "tittel"
        private const val beskrivelse = "beskrivelse"
        private const val status = "status"
        private const val opprettetAvPersonNavident = "opprettet_av_person_navident"
        private const val opprettetAvKontorEnhetid = "opprettet_av_kontor_enhetid"
        private const val opprettetAvTidspunkt = "opprettet_av_tidspunkt"
        private const val fratid = "fratid"
        private const val tiltid = "tiltid"
        private const val sted = "sted"
        private const val eiere = "eiere"
    }

    private fun ResultSet.tilRekrutteringstreff(): Rekrutteringstreff {
        return Rekrutteringstreff(
            id = TreffId(getObject(id, UUID::class.java)),
            tittel = getString(tittel),
            beskrivelse = getString(beskrivelse),
            fraTid = getTimestamp(fratid)?.toInstant()?.atOslo(),
            tilTid = getTimestamp(tiltid)?.toInstant()?.atOslo(),
            sted = getString(sted),
            status = getString(status),
            opprettetAvPersonNavident = getString(opprettetAvPersonNavident),
            opprettetAvNavkontorEnhetId = getString(opprettetAvKontorEnhetid),
            opprettetAvTidspunkt = getTimestamp(opprettetAvTidspunkt).toInstant().atOslo()
        )
    }
}


class Tabellnavn(private val navn: String) {
    override fun toString() = navn
}

class Kolonnenavn(private val navn: String) {
    override fun toString() = navn
}
