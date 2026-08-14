package no.nav.toi.oppfølging

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import java.sql.Connection
import java.sql.ResultSet

class OppfølgingRepository {

    fun hentForTreff(connection: Connection, treffDbId: Long): List<Vurdering> {
        val notater = hentNotater(connection, treffDbId)
        val sql = """
            SELECT v.vurdering_id, j.id::text, a.id::text, v.vurdering,
                   v.andregangsintervju, v.andregangsintervju_dato, v.jobbtilbud
            FROM vurdering v
            JOIN jobbsoker j ON j.jobbsoker_id = v.jobbsoker_id
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = v.arbeidsgiver_id
            WHERE j.rekrutteringstreff_id = ? AND j.status != 'SLETTET' AND a.status = 'AKTIV'
            ORDER BY a.arbeidsgiver_id, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe {
                    Vurdering(
                        personTreffId = PersonTreffId(it.getString(2)),
                        arbeidsgiverTreffId = ArbeidsgiverTreffId(it.getString(3)),
                        vurdering = Vurderingsvalg.entries.firstOrNull { valg -> valg.name == it.getString(4) },
                        notater = notater[it.getLong(1)].orEmpty(),
                        andregangsintervju = it.getBoolean(5),
                        andregangsintervjuDato = it.getDate(6)?.toLocalDate(),
                        jobbtilbud = it.getBoolean(7),
                    )
                }
            }
        }
    }

    private fun hentNotater(connection: Connection, treffDbId: Long): Map<Long, List<Vurderingsnotat>> {
        val sql = """
            SELECT n.vurdering_id, n.notat
            FROM vurdering_notat n
            JOIN vurdering v ON v.vurdering_id = n.vurdering_id
            JOIN jobbsoker j ON j.jobbsoker_id = v.jobbsoker_id
            WHERE j.rekrutteringstreff_id = ?
            ORDER BY n.vurdering_notat_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { it.getLong(1) to it.getString(2) }
                    .mapNotNull { (id, navn) ->
                        Vurderingsnotat.entries.firstOrNull { it.name == navn }?.let { id to it }
                    }
                    .groupBy({ it.first }, { it.second })
            }
        }
    }

    fun lagre(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long, vurdering: Vurdering) {
        val sql = """
            INSERT INTO vurdering (jobbsoker_id, arbeidsgiver_id, vurdering, andregangsintervju, andregangsintervju_dato, jobbtilbud)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (jobbsoker_id, arbeidsgiver_id) DO UPDATE SET
                vurdering = EXCLUDED.vurdering,
                andregangsintervju = EXCLUDED.andregangsintervju,
                andregangsintervju_dato = EXCLUDED.andregangsintervju_dato,
                jobbtilbud = EXCLUDED.jobbtilbud
            RETURNING vurdering_id
        """.trimIndent()
        val vurderingId = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.setString(3, vurdering.vurdering?.name)
            stmt.setBoolean(4, vurdering.andregangsintervju)
            stmt.setDate(5, vurdering.andregangsintervjuDato?.let { java.sql.Date.valueOf(it) })
            stmt.setBoolean(6, vurdering.jobbtilbud)
            stmt.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
        erstattNotater(connection, vurderingId, vurdering.notater)
    }

    private fun erstattNotater(connection: Connection, vurderingId: Long, notater: List<Vurderingsnotat>) {
        connection.prepareStatement("DELETE FROM vurdering_notat WHERE vurdering_id = ?").use { stmt ->
            stmt.setLong(1, vurderingId)
            stmt.executeUpdate()
        }
        if (notater.isEmpty()) return
        connection.prepareStatement("INSERT INTO vurdering_notat (vurdering_id, notat) VALUES (?, ?)").use { stmt ->
            notater.distinct().forEach { notat ->
                stmt.setLong(1, vurderingId)
                stmt.setString(2, notat.name)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun slett(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long) {
        connection.prepareStatement("DELETE FROM vurdering WHERE jobbsoker_id = ? AND arbeidsgiver_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.executeUpdate()
        }
    }

    fun slettForJobbsøker(connection: Connection, jobbsøkerId: Long) {
        connection.prepareStatement("DELETE FROM vurdering WHERE jobbsoker_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.executeUpdate()
        }
    }

    fun tellForJobbsøker(connection: Connection, jobbsøkerId: Long): Int =
        connection.prepareStatement("SELECT COUNT(*) FROM vurdering WHERE jobbsoker_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.executeQuery().use { it.next(); it.getInt(1) }
        }
}

private fun <T> ResultSet.tilListe(les: (ResultSet) -> T): List<T> =
    generateSequence { if (next()) les(this) else null }.toList()
