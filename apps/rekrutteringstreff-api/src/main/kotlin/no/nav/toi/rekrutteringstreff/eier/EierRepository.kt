package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.NotFoundResponse
import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import java.sql.Connection
import javax.sql.DataSource

class EierRepository(
    private val dataSource: DataSource,
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

    fun hent(connection: Connection, treff: TreffId, forUpdate: Boolean = false): List<Eier>? {
        check(!forUpdate || !connection.autoCommit) { "FOR UPDATE krever en transaksjon" }
        // Treffraden låses først, også uten eierrader
        val sql = "SELECT rekrutteringstreff_id FROM $rekrutteringstreff WHERE $id = ?" +
            if (forUpdate) " FOR UPDATE" else ""
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
            """.trimIndent() + if (forUpdate) " FOR UPDATE" else ""
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
                    INSERT INTO rekrutteringstreff_eier (rekrutteringstreff_id, nav_ident, kontor_enhetid, lagt_til_av, eier_navn)
                    SELECT rekrutteringstreff_id, ?, ?, ?, ?
                    FROM $rekrutteringstreff
                    WHERE $id = ?
                    FOR UPDATE
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
        val gjeldendeEiere = hent(connection, treff, forUpdate = true)?.tilNavIdenter() ?: return false
        if (gjeldendeEiere.size <= 1 || eier !in gjeldendeEiere) return false
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
