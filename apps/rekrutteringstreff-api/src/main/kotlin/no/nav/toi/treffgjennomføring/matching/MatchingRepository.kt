package no.nav.toi.treffgjennomføring.matching

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.treffgjennomføring.Treffkontekst
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class MatchingRepository {

    fun hentFor(connection: Connection, kontekst: Treffkontekst) = Matching(
        interesser = hentInteresser(connection, kontekst.treffDbId),
        intervjufordelinger = hentIntervjufordelinger(connection, kontekst),
    )

    private fun hentInteresser(connection: Connection, treffDbId: Long): List<Interesse> {
        val sql = """
            SELECT j.id::text, a.id::text
            FROM interesse i
            JOIN jobbsoker j ON j.jobbsoker_id = i.jobbsoker_id
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = i.arbeidsgiver_id
            WHERE j.rekrutteringstreff_id = ? AND j.status != 'SLETTET' AND a.status = 'AKTIV'
            ORDER BY a.arbeidsgiver_id, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { Interesse(PersonTreffId(it.getString(1)), ArbeidsgiverTreffId(it.getString(2))) }
            }
        }
    }

    /** Plasseringen er tidsluka, så rekkefølgen på de inkluderte er data — ikke pynt. */
    private fun hentIntervjufordelinger(
        connection: Connection,
        kontekst: Treffkontekst,
    ): List<ArbeidsgiverIntervjufordeling> {
        val sql = """
            SELECT a.id::text, j.id::text, f.inkludert
            FROM intervju_fordeling f
            JOIN jobbsoker j ON j.jobbsoker_id = f.jobbsoker_id
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = f.arbeidsgiver_id
            WHERE j.rekrutteringstreff_id = ? AND j.status != 'SLETTET' AND a.status = 'AKTIV'
            ORDER BY a.arbeidsgiver_id, f.plassering
        """.trimIndent()
        val rader = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, kontekst.treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe {
                    Triple(ArbeidsgiverTreffId(it.getString(1)), PersonTreffId(it.getString(2)), it.getBoolean(3))
                }
            }
        }
        val perArbeidsgiver = rader.groupBy { it.first }
        return kontekst.arbeidsgiverIder.mapNotNull { arbeidsgiver ->
            val egne = perArbeidsgiver[arbeidsgiver] ?: return@mapNotNull null
            ArbeidsgiverIntervjufordeling(
                arbeidsgiverTreffId = arbeidsgiver,
                inkludertePersonTreffIder = egne.filter { it.third }.map { it.second },
                ekskludertePersonTreffIder = egne.filterNot { it.third }.map { it.second },
            )
        }
    }

    fun settInteresse(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long, interessert: Boolean): Boolean {
        val sql = if (interessert) {
            "INSERT INTO interesse (jobbsoker_id, arbeidsgiver_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
        } else {
            "DELETE FROM interesse WHERE jobbsoker_id = ? AND arbeidsgiver_id = ?"
        }
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.executeUpdate() > 0
        }
    }

    fun erstattIntervjufordelinger(
        connection: Connection,
        fordelinger: List<ArbeidsgiverIntervjufordeling>,
        kontekst: Treffkontekst,
    ) {
        val arbeidsgiverIder = fordelinger.mapNotNull { kontekst.arbeidsgiverId(it.arbeidsgiverTreffId) }
        if (arbeidsgiverIder.isEmpty()) return

        connection.prepareStatement("DELETE FROM intervju_fordeling WHERE arbeidsgiver_id = ?").use { stmt ->
            arbeidsgiverIder.forEach { stmt.setLong(1, it); stmt.addBatch() }
            stmt.executeBatch()
        }

        val sql = """
            INSERT INTO intervju_fordeling (jobbsoker_id, arbeidsgiver_id, plassering, inkludert)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            fordelinger.forEach { fordeling ->
                val arbeidsgiverId = kontekst.arbeidsgiverId(fordeling.arbeidsgiverTreffId) ?: return@forEach
                fordeling.inkludertePersonTreffIder.forEachIndexed { plassering, person ->
                    stmt.leggTilFordeling(kontekst, person, arbeidsgiverId, plassering, inkludert = true)
                }
                fordeling.ekskludertePersonTreffIder.forEachIndexed { plassering, person ->
                    stmt.leggTilFordeling(kontekst, person, arbeidsgiverId, plassering, inkludert = false)
                }
            }
            stmt.executeBatch()
        }
    }

    private fun PreparedStatement.leggTilFordeling(
        kontekst: Treffkontekst,
        person: PersonTreffId,
        arbeidsgiverId: Long,
        plassering: Int,
        inkludert: Boolean,
    ) {
        val jobbsøkerId = kontekst.jobbsøkerId(person) ?: return
        setLong(1, jobbsøkerId)
        setLong(2, arbeidsgiverId)
        setInt(3, plassering)
        setBoolean(4, inkludert)
        addBatch()
    }

    fun tellForJobbsøker(connection: Connection, jobbsøkerId: Long): Pair<Int, Int> {
        val sql = """
            SELECT
                (SELECT COUNT(*) FROM interesse WHERE jobbsoker_id = ?),
                (SELECT COUNT(*) FROM intervju_fordeling WHERE jobbsoker_id = ?)
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            (1..2).forEach { stmt.setLong(it, jobbsøkerId) }
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1) to rs.getInt(2)
            }
        }
    }

    fun slettForJobbsøker(connection: Connection, jobbsøkerId: Long) {
        listOf(
            "DELETE FROM interesse WHERE jobbsoker_id = ?",
            "DELETE FROM intervju_fordeling WHERE jobbsoker_id = ?",
        ).forEach { sql ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, jobbsøkerId)
                stmt.executeUpdate()
            }
        }
    }
}

private fun <T> ResultSet.tilListe(les: (ResultSet) -> T): List<T> =
    generateSequence { if (next()) les(this) else null }.toList()
