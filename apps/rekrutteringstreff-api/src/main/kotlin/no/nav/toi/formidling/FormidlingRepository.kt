package no.nav.toi.formidling

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.executeInTransaction
import no.nav.toi.formidling.dto.FormidlingDto
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource

class FormidlingRepository(private val dataSource: DataSource) {

    fun opprett(
        connection: Connection,
        treffId: TreffId,
        personTreffId: PersonTreffId,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        stillingId: UUID,
        kandidatlisteId: UUID? = null,
        utfallSendtTidspunkt: ZonedDateTime? = null,
    ): Long {
        val sql = """
            INSERT INTO formidling (rekrutteringstreff_id, jobbsoker_id, arbeidsgiver_id, stilling_id, kandidatliste_id, utfall_sendt_tidspunkt)
            VALUES (
                (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?),
                (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?),
                (SELECT arbeidsgiver_id FROM arbeidsgiver WHERE id = ? AND rekrutteringstreff_id = (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?) AND status = 'AKTIV'),
                ?,
                ?,
                ?
            )
        """.trimIndent()

        return connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setObject(2, personTreffId.somUuid)
            stmt.setObject(3, arbeidsgiverTreffId.somUuid)
            stmt.setObject(4, treffId.somUuid)
            stmt.setObject(5, stillingId)
            stmt.setNullableUuid(6, kandidatlisteId)
            stmt.setNullableTimestampWithTimezone(7, utfallSendtTidspunkt)
            stmt.executeUpdate()
            stmt.generatedKeys.use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    fun hent(formidlingId: Long): Formidling? = dataSource.connection.use { conn ->
        val sql = "$HENT_FORMIDLING_BASE WHERE f.formidling_id = ? AND f.slettet_tidspunkt IS NULL"

        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, formidlingId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toFormidling() else null
            }
        }
    }

    fun hent(treffId: TreffId, personTreffId: PersonTreffId, arbeidsgiverTreffId: ArbeidsgiverTreffId): Formidling? = dataSource.connection.use { conn ->
        val sql = "$HENT_FORMIDLING_BASE WHERE rt.id = ? AND js.id = ? AND ag.id = ? AND f.slettet_tidspunkt IS NULL"

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

    fun oppdaterUtfallSendtTidspunkt(connection: Connection, formidlingId: Long): Boolean {
        val sql = """
            UPDATE formidling
            SET utfall_sendt_tidspunkt = now()
            WHERE formidling_id = ? AND slettet_tidspunkt IS NULL AND utfall_sendt_tidspunkt IS NULL
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, formidlingId)
            stmt.executeUpdate() > 0
        }
    }

    fun hentAlleForTreff(treffId: TreffId): List<FormidlingDto> {
        val where = tilWhereClause(byggBasisFilter(treffId))
        return hentMedWhere(where)
    }

    fun hentEgneForTreff(
        treffId: TreffId,
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): List<FormidlingDto> {
        val where = tilWhereClause(
            byggBasisFilter(treffId) + byggVeilederEllerEnhetFilter(veilederNavIdent, tilknyttedeEnheter)
        )
        return hentMedWhere(where)
    }

    private fun hentMedWhere(where: WhereClause): List<FormidlingDto> =
        dataSource.executeInTransaction { conn ->
            val sql = """
                SELECT
                    f.id,
                    f.opprettet_tidspunkt,
                    f.stilling_id,
                    j.fodselsnummer,
                    j.fornavn,
                    j.etternavn,
                    ag.orgnr,
                    ag.orgnavn
                FROM formidling f
                JOIN rekrutteringstreff rt ON f.rekrutteringstreff_id = rt.rekrutteringstreff_id
                JOIN jobbsoker j ON f.jobbsoker_id = j.jobbsoker_id
                JOIN arbeidsgiver ag ON f.arbeidsgiver_id = ag.arbeidsgiver_id
                ${where.sql}
                ORDER BY f.opprettet_tidspunkt DESC, f.formidling_id DESC
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
                settWhereParametere(stmt, where.params)
                stmt.executeQuery().use { rs ->
                    val resultater = mutableListOf<FormidlingDto>()
                    while (rs.next()) {
                        resultater += rs.toFormidlingDto()
                    }
                    resultater
                }
            }
        }

    private fun settWhereParametere(stmt: PreparedStatement, params: List<SqlParam>) {
        params.forEachIndexed { index, param ->
            settParam(stmt, index + 1, param)
        }
    }

    private fun settParam(stmt: PreparedStatement, index: Int, param: SqlParam) {
        when (param) {
            is SqlParam.Uuid -> stmt.setObject(index, param.value)
            is SqlParam.Text -> stmt.setString(index, param.value)
            is SqlParam.TextArray ->
                stmt.setArray(index, stmt.connection.createArrayOf("text", param.value.toTypedArray()))
        }
    }

    private fun ResultSet.toFormidlingDto() = FormidlingDto(
        id = UUID.fromString(getString("id")),
        opprettetTidspunkt = getTimestamp("opprettet_tidspunkt").toInstant().atZone(OSLO),
        fødselsnummer = getString("fodselsnummer"),
        fornavn = getString("fornavn"),
        etternavn = getString("etternavn"),
        orgnr = getString("orgnr"),
        orgnavn = getString("orgnavn"),
        stillingId = UUID.fromString(getString("stilling_id")),
    )

    private data class WhereClause(
        val sql: String,
        val params: List<SqlParam>,
    )

    private data class Condition(
        val sql: String,
        val params: List<SqlParam> = emptyList(),
    ) {
        constructor(sql: String, vararg params: SqlParam) : this(sql, params.toList())
    }

    private sealed interface SqlParam {
        data class Uuid(val value: UUID) : SqlParam
        data class Text(val value: String) : SqlParam
        data class TextArray(val value: List<String>) : SqlParam
    }

    private fun byggBasisFilter(treffId: TreffId): List<Condition> = listOf(
        Condition("rt.id = ?", SqlParam.Uuid(treffId.somUuid)),
        Condition("f.slettet_tidspunkt IS NULL"),
        Condition("j.status != 'SLETTET'"),
    )

    private fun byggVeilederEllerEnhetFilter(
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): Condition {
        val unikeEnheter = tilknyttedeEnheter.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        return Condition(
            "(UPPER(j.veileder_navident) = ? OR j.kontornummer = ANY (?::text[]))",
            SqlParam.Text(veilederNavIdent.trim().uppercase()),
            SqlParam.TextArray(unikeEnheter),
        )
    }

    private fun tilWhereClause(conditions: List<Condition>): WhereClause = WhereClause(
        sql = "WHERE " + conditions.joinToString(" AND ") { it.sql },
        params = conditions.flatMap { it.params },
    )

    private fun ResultSet.toFormidling() = Formidling(
        formidlingId = getLong("formidling_id"),
        id = UUID.fromString(getString("id")),
        treffId = TreffId(getString("treff_id")),
        jobbsøkerPersonTreffId = PersonTreffId(UUID.fromString(getString("jobbsoker_treff_id"))),
        arbeidsgiverTreffId = ArbeidsgiverTreffId(getString("arbeidsgiver_treff_id")),
        stillingId = UUID.fromString(getString("stilling_id")),
        kandidatlisteId = getObject("kandidatliste_id", UUID::class.java),
        utfallSendtTidspunkt = getTimestamp("utfall_sendt_tidspunkt")?.toInstant()?.atZone(ZoneId.of("Europe/Oslo")),
        opprettetTidspunkt = getTimestamp("opprettet_tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
    )

    private fun java.sql.PreparedStatement.setNullableUuid(index: Int, value: UUID?) {
        if (value == null) {
            setNull(index, Types.OTHER)
        } else {
            setObject(index, value)
        }
    }

    private fun java.sql.PreparedStatement.setNullableTimestampWithTimezone(index: Int, value: ZonedDateTime?) {
        if (value == null) {
            setNull(index, Types.TIMESTAMP_WITH_TIMEZONE)
        } else {
            setObject(index, value.toOffsetDateTime())
        }
    }

    private companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
        private val OSLO = ZoneId.of("Europe/Oslo")

        private val HENT_FORMIDLING_BASE = """
            SELECT 
                f.formidling_id,
                f.id,
                rt.id as treff_id,
                js.id as jobbsoker_treff_id,
                ag.id as arbeidsgiver_treff_id,
                f.stilling_id,
                f.kandidatliste_id,
                f.utfall_sendt_tidspunkt,
                f.opprettet_tidspunkt
            FROM formidling f
            JOIN rekrutteringstreff rt ON f.rekrutteringstreff_id = rt.rekrutteringstreff_id
            JOIN jobbsoker js ON f.jobbsoker_id = js.jobbsoker_id
            JOIN arbeidsgiver ag ON f.arbeidsgiver_id = ag.arbeidsgiver_id
        """.trimIndent()
    }
}
