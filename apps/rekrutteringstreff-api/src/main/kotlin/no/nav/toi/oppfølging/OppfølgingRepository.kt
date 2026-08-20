package no.nav.toi.oppfølging

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.tilListe
import java.sql.Connection
import java.sql.ResultSet

class OppfølgingRepository {

    fun hentForTreff(connection: Connection, treffDbId: Long): List<Vurdering> {
        val sql = """
            SELECT j.id::text, a.id::text, v.vurderingsstatus, v.vurderingsnotat,
                   v.avtalt_intervju, v.avtalt_intervju_dato, v.jobbtilbud
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
                        personTreffId = PersonTreffId(it.getString(1)),
                        arbeidsgiverTreffId = ArbeidsgiverTreffId(it.getString(2)),
                        vurderingsstatus = Vurderingsvalg.entries.firstOrNull { valg -> valg.name == it.getString(3) },
                        vurderingsnotat = it.lesNotater(4),
                        avtaltIntervju = it.getBoolean(5),
                        avtaltIntervjuDato = it.getDate(6)?.toLocalDate(),
                        jobbtilbud = it.getBoolean(7),
                    )
                }
            }
        }
    }

    private fun ResultSet.lesNotater(kolonne: Int): List<Vurderingsnotat> {
        val array = getArray(kolonne) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val navn = array.array as Array<String?>
        return navn.mapNotNull { verdi -> Vurderingsnotat.entries.firstOrNull { it.name == verdi } }
    }

    fun lagre(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long, vurdering: Vurdering) {
        val sql = """
            INSERT INTO vurdering (jobbsoker_id, arbeidsgiver_id, vurderingsstatus, avtalt_intervju, avtalt_intervju_dato, jobbtilbud, vurderingsnotat)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (jobbsoker_id, arbeidsgiver_id) DO UPDATE SET
                vurderingsstatus = EXCLUDED.vurderingsstatus,
                avtalt_intervju = EXCLUDED.avtalt_intervju,
                avtalt_intervju_dato = EXCLUDED.avtalt_intervju_dato,
                jobbtilbud = EXCLUDED.jobbtilbud,
                vurderingsnotat = EXCLUDED.vurderingsnotat
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.setString(3, vurdering.vurderingsstatus?.name)
            stmt.setBoolean(4, vurdering.avtaltIntervju)
            stmt.setDate(5, vurdering.avtaltIntervjuDato?.let { java.sql.Date.valueOf(it) })
            stmt.setBoolean(6, vurdering.jobbtilbud)
            stmt.setArray(7, connection.createArrayOf("text", vurdering.vurderingsnotat.distinct().map { it.name }.toTypedArray()))
            stmt.executeUpdate()
        }
    }

    fun slett(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long) {
        connection.prepareStatement("DELETE FROM vurdering WHERE jobbsoker_id = ? AND arbeidsgiver_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.executeUpdate()
        }
    }

    fun tellForJobbsøker(connection: Connection, jobbsøkerId: Long): Int =
        connection.prepareStatement("SELECT COUNT(*) FROM vurdering WHERE jobbsoker_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.executeQuery().use { it.next(); it.getInt(1) }
        }

    fun finnesForPar(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long): Boolean =
        connection.prepareStatement("SELECT 1 FROM vurdering WHERE jobbsoker_id = ? AND arbeidsgiver_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.executeQuery().use { it.next() }
        }
}
