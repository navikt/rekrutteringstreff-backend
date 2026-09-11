package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.NotFoundResponse
import no.nav.toi.executeInTransaction
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

    fun leggTil(treff: TreffId, eierNavIdent: String, kontorEnhetId: String, eierNavn: String? = null) {
        dataSource.executeInTransaction { connection ->
            leggTil(connection, treff, eierNavIdent, kontorEnhetId, eierNavn)
        }
    }

    fun leggTil(connection: Connection, treff: TreffId, eierNavIdent: String, kontorEnhetId: String, eierNavn: String? = null) {
        require(kontorEnhetId.isNotBlank()) { "Eier må ha kontortilknytning" }
        require(eierNavIdent.isNotBlank()) { "Eier må ha Nav-ident" }
        connection.prepareStatement(
                """
                    WITH oppdatert_treff AS (
                        UPDATE $rekrutteringstreff
                        SET $eiere = array(SELECT DISTINCT unnest(array_append($eiere, ?)))
                        WHERE $id = ?
                        RETURNING rekrutteringstreff_id
                    )
                    INSERT INTO rekrutteringstreff_eier (rekrutteringstreff_id, nav_ident, kontor_enhetid, lagt_til_av, eier_navn)
                    SELECT rekrutteringstreff_id, ?, ?, ?, ?
                    FROM oppdatert_treff
                    ON CONFLICT (rekrutteringstreff_id, nav_ident)
                    DO UPDATE SET kontor_enhetid = EXCLUDED.kontor_enhetid,
                                  eier_navn = coalesce(EXCLUDED.eier_navn, rekrutteringstreff_eier.eier_navn)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, eierNavIdent)
                stmt.setObject(2, treff.somUuid)
                stmt.setString(3, eierNavIdent)
                stmt.setString(4, kontorEnhetId)
                stmt.setString(5, eierNavIdent)
                stmt.setString(6, eierNavn)
                if (stmt.executeUpdate() == 0) {
                    throw NotFoundResponse("Rekrutteringstreff med id ${treff.somString} finnes ikke")
                }
            }
    }

    fun slett(treff: TreffId, eier: String): Boolean {
        return dataSource.executeInTransaction { connection ->
            slett(connection, treff, eier)
        }
    }

    fun slett(connection: Connection, treff: TreffId, eier: String): Boolean {
        connection.prepareStatement(
            """
                    WITH oppdatert_treff AS (
                        UPDATE $rekrutteringstreff
                        SET $eiere = array_remove($eiere, ?)
                        WHERE $id = ? AND array_length($eiere, 1) > 1 AND $eiere @> ARRAY[?]::text[]
                        RETURNING rekrutteringstreff_id
                    ), slettet_eier AS (
                        DELETE FROM rekrutteringstreff_eier
                        WHERE rekrutteringstreff_id IN (SELECT rekrutteringstreff_id FROM oppdatert_treff)
                          AND nav_ident = ?
                    )
                    SELECT EXISTS (SELECT 1 FROM oppdatert_treff)
                """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, eier)
            stmt.setObject(2, treff.somUuid)
            stmt.setString(3, eier)
            stmt.setString(4, eier)
            return stmt.executeQuery().use { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        }
    }
}
