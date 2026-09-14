package no.nav.toi.treffgjennomføring

import java.sql.Connection

class RegistreringerRepository {
    fun hentForJobbsøker(connection: Connection, jobbsøkerId: Long): Registreringer =
        hent(connection, jobbsøkerId, """
            SELECT
                (SELECT COUNT(*) FROM interesse WHERE jobbsoker_id = ?),
                (SELECT COUNT(*) FROM intervjufordeling WHERE jobbsoker_id = ?),
                (SELECT COUNT(*) FROM vurdering WHERE jobbsoker_id = ?)
        """.trimIndent())

    fun hentForArbeidsgiver(connection: Connection, arbeidsgiverId: Long): Registreringer =
        hent(connection, arbeidsgiverId, """
            SELECT
                (SELECT COUNT(*) FROM interesse WHERE arbeidsgiver_id = ?),
                (SELECT COUNT(*) FROM intervjufordeling WHERE arbeidsgiver_id = ?),
                (SELECT COUNT(*) FROM vurdering WHERE arbeidsgiver_id = ?)
        """.trimIndent())

    private fun hent(connection: Connection, id: Long, sql: String): Registreringer =
        connection.prepareStatement(sql).use { stmt ->
            (1..3).forEach { stmt.setLong(it, id) }
            stmt.executeQuery().use { rs ->
                rs.next()
                Registreringer(
                    interesser = rs.getInt(1),
                    intervjufordelinger = rs.getInt(2),
                    vurderinger = rs.getInt(3),
                )
            }
        }
}
