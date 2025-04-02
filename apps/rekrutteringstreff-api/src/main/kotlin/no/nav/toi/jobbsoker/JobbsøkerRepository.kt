package no.nav.toi.jobbsoker

import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

class JobbsøkerRepository(
    private val dataSource: DataSource,
) {

    private fun hentTreffDbId(
        connection: Connection,
        treff: TreffId
    ): Long? {
        connection.prepareStatement(
            "SELECT db_id FROM rekrutteringstreff WHERE id = ?"
        ).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getLong("db_id")
                } else return null
            }
        }
    }

    private fun finnesIDb(connection: Connection, treffId: TreffId): Boolean =
        hentTreffDbId(connection, treffId) != null


    fun leggTil(jobbsøker: LeggTilJobbsøker, treff: TreffId) {

        fun leggTilJobbsøker(connection: Connection, jobbsøker: LeggTilJobbsøker, treffDbId: Long) {
            connection.prepareStatement(
                "INSERT INTO jobbsoker (treff_db_id, fodselsnummer, kandidatnummer, fornavn, etternavn) VALUES (?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setLong(1, treffDbId)
                stmt.setString(2, jobbsøker.fødselsnummer.asString)
                stmt.setString(3, jobbsøker.kandidatnummer?.asString)
                stmt.setString(4, jobbsøker.fornavn.asString)
                stmt.setString(5, jobbsøker.etternavn.asString)
                stmt.executeUpdate()
            }
        }

        dataSource.connection.use { connection ->
            val treffDbId: Long = hentTreffDbId(connection, treff)
                ?: throw IllegalArgumentException("Kan ikke legge til jobbsøker på treffet fordi det ikke finnes noe treff med id ${treff.somUuid}. jobbsøker=$jobbsøker")
            leggTilJobbsøker(connection, jobbsøker, treffDbId)
        }
    }

    fun hentJobbsøkere(treff: TreffId): List<Jobbsøker> {
        dataSource.connection.use { connection ->
            if (!finnesIDb(connection, treff))
                throw IllegalArgumentException("Kan ikke hente jobbsøkere fordi det ikke finnes noe rekrutteringstreff med id $treff.")

            connection.prepareStatement(
                """
                SELECT js.fodselsnummer, js.kandidatnummer, js.fornavn, js.etternavn, rt.id as treff_id
                FROM jobbsoker js
                JOIN rekrutteringstreff rt ON js.treff_db_id = rt.db_id
                WHERE rt.id = ?
                ORDER BY js.db_id ASC;
            """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                val resultSet = stmt.executeQuery()
                return resultSet.use(this::tilJobbsøkere)
            }
        }
    }

    private fun tilJobbsøkere(rs: ResultSet): List<Jobbsøker> {
        val jobbsøkere = mutableListOf<Jobbsøker>()
        while (rs.next()) {
            jobbsøkere.add(
                Jobbsøker(
                    treffId = TreffId(rs.getString("treff_id")),
                    fødselsnummer = Fødselsnummer(rs.getString("fodselsnummer")),
                    kandidatnummer = rs.getString("kandidatnummer")?.let { Kandidatnummer(it) },
                    fornavn = Fornavn(rs.getString("fornavn")),
                    etternavn = Etternavn(rs.getString("etternavn"))
                )
            )
        }
        return jobbsøkere
    }

}
