package no.nav.toi.formidling

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.ResultSet
import java.time.ZoneId
import java.util.*
import javax.sql.DataSource

class FormidlingRepository(private val dataSource: DataSource) {

    fun opprett(connection: Connection, treffId: TreffId, personTreffId: PersonTreffId, arbeidsgiverTreffId: ArbeidsgiverTreffId, stillingId: UUID): Long {
        val sql = """
            INSERT INTO formidling (rekrutteringstreff_id, jobbsoker_id, arbeidsgiver_id, stilling_id)
            VALUES (
                (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?),
                (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?),
                (SELECT arbeidsgiver_id FROM arbeidsgiver WHERE id = ? AND rekrutteringstreff_id = (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?) AND status = 'AKTIV'),
                ?
            )
        """.trimIndent()

        return connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setObject(2, personTreffId.somUuid)
            stmt.setObject(3, arbeidsgiverTreffId.somUuid)
            stmt.setObject(4, treffId.somUuid)
            stmt.setObject(5, stillingId)
            stmt.executeUpdate()
            stmt.generatedKeys.use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    fun hent(formidlingId: Long): Formidling? = dataSource.connection.use { conn ->
        val sql = """
            SELECT 
                f.formidling_id,
                f.id,
                rt.id as treff_id,
                js.id as jobbsoker_treff_id,
                ag.id as arbeidsgiver_treff_id,
                f.stilling_id,
                f.opprettet_tidspunkt
            FROM formidling f
            JOIN rekrutteringstreff rt ON f.rekrutteringstreff_id = rt.rekrutteringstreff_id
            JOIN jobbsoker js ON f.jobbsoker_id = js.jobbsoker_id
            JOIN arbeidsgiver ag ON f.arbeidsgiver_id = ag.arbeidsgiver_id
            WHERE f.formidling_id = ? AND f.slettet_tidspunkt IS NULL
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, formidlingId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toFormidling() else null
            }
        }
    }

    fun hent(treffId: TreffId, personTreffId: PersonTreffId, arbeidsgiverTreffId: ArbeidsgiverTreffId): Formidling? = dataSource.connection.use { conn ->
        val sql = """
            SELECT 
                f.formidling_id,
                f.id,
                rt.id as treff_id,
                js.id as jobbsoker_treff_id,
                ag.id as arbeidsgiver_treff_id,
                f.stilling_id,
                f.opprettet_tidspunkt
            FROM formidling f
            JOIN rekrutteringstreff rt ON f.rekrutteringstreff_id = rt.rekrutteringstreff_id
            JOIN jobbsoker js ON f.jobbsoker_id = js.jobbsoker_id
            JOIN arbeidsgiver ag ON f.arbeidsgiver_id = ag.arbeidsgiver_id
            WHERE rt.id = ? AND js.id = ? AND ag.id = ? AND f.slettet_tidspunkt IS NULL
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setObject(2, personTreffId.somUuid)
            stmt.setObject(3, arbeidsgiverTreffId.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toFormidling() else null
            }
        }
    }

    fun markerSlettet(connection: Connection, formidlingId: Long): Boolean {
        val sql = """
            UPDATE formidling
            SET slettet_tidspunkt = now()
            WHERE formidling_id = ? AND slettet_tidspunkt IS NULL
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, formidlingId)
            stmt.executeUpdate() > 0
        }
    }

    private fun ResultSet.toFormidling() = Formidling(
        formidlingId = getLong("formidling_id"),
        id = UUID.fromString(getString("id")),
        treffId = TreffId(getString("treff_id")),
        jobbsøkerPersonTreffId = PersonTreffId(UUID.fromString(getString("jobbsoker_treff_id"))),
        arbeidsgiverTreffId = ArbeidsgiverTreffId(getString("arbeidsgiver_treff_id")),
        stillingId = UUID.fromString(getString("stilling_id")),
        opprettetTidspunkt = getTimestamp("opprettet_tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
    )
}
