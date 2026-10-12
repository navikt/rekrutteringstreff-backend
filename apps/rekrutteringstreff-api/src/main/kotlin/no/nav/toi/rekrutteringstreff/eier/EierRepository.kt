package no.nav.toi.rekrutteringstreff.eier

import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import javax.sql.DataSource

class EierRepository(
    internal val dataSource: DataSource,
) {
    companion object {
        private const val rekrutteringstreff = "rekrutteringstreff"
        private const val id = "id"
    }


    fun hent(treff: TreffId): List<Eier>? {
        dataSource.connection.use { connection ->
            return hent(connection, treff)
        }
    }

    /** Returnerer null hvis treffet ikke finnes. */
    fun hent(connection: Connection, treff: TreffId): List<Eier>? {
        val sql = "SELECT rekrutteringstreff_id FROM $rekrutteringstreff WHERE $id = ?"
        val treffDbId = connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getLong("rekrutteringstreff_id") else return null
            }
        }
        return connection.prepareStatement(
            """
            SELECT nav_ident, kontor_enhetid
            FROM rekrutteringstreff_eier
            WHERE rekrutteringstreff_id = ?
            ORDER BY rekrutteringstreff_eier_id
            """.trimIndent()
        ).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(Eier(rs.getString("nav_ident"), rs.getString("kontor_enhetid")))
                    }
                }
            }
        }
    }

    fun leggTil(connection: Connection, treff: TreffId, eierNavIdent: String, kontorEnhetId: String, eierNavn: String? = null) {
        require(kontorEnhetId.isNotBlank()) { "Eier må ha kontortilknytning" }
        require(eierNavIdent.isNotBlank()) { "Eier må ha Nav-ident" }
        connection.prepareStatement(
                """
                    INSERT INTO rekrutteringstreff_eier (rekrutteringstreff_id, nav_ident, kontor_enhetid, lagt_til_av, eier_navn)
                    SELECT rekrutteringstreff_id, ?, ?, ?, ?
                    FROM $rekrutteringstreff
                    WHERE $id = ?
                    ON CONFLICT (rekrutteringstreff_id, nav_ident)
                    DO UPDATE SET kontor_enhetid = EXCLUDED.kontor_enhetid,
                                  eier_navn = coalesce(EXCLUDED.eier_navn, rekrutteringstreff_eier.eier_navn)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, eierNavIdent)
                stmt.setString(2, kontorEnhetId)
                stmt.setString(3, eierNavIdent)
                stmt.setString(4, eierNavn)
                stmt.setObject(5, treff.somUuid)
                if (stmt.executeUpdate() == 0) {
                    throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id ${treff.somString} finnes ikke")
                }
            }
    }

    /** Regelen om at siste eier ikke kan slettes, ligger i [EierService.slettEier]. */
    fun slett(connection: Connection, treff: TreffId, eier: String): Boolean {
        connection.prepareStatement(
            """
                    DELETE FROM rekrutteringstreff_eier e
                    USING $rekrutteringstreff rt
                    WHERE rt.rekrutteringstreff_id = e.rekrutteringstreff_id
                      AND rt.$id = ?
                      AND e.nav_ident = ?
                """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.setString(2, eier)
            return stmt.executeUpdate() > 0
        }
    }
}
