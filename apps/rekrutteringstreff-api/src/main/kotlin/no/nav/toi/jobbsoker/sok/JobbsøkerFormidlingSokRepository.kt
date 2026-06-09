package no.nav.toi.jobbsoker.sok

import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.UUID
import javax.sql.DataSource

class JobbsøkerFormidlingSokRepository(private val dataSource: DataSource) {

    companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
    }

    fun hentAlleForFormidling(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
    ): JobbsøkerFormidlingRespons {
        val where = byggJobbsøkerWhereForAlle(treffId, request)
        return hentJobbsøkereMedWhere(where, request)
    }

    fun hentEgneForFormidling(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): JobbsøkerFormidlingRespons {
        val where = byggJobbsøkerWhereForEgne(treffId, request, veilederNavIdent, tilknyttedeEnheter)
        return hentJobbsøkereMedWhere(where, request)
    }

    private fun hentJobbsøkereMedWhere(
        where: WhereClause,
        request: JobbsøkerFormidlingRequest,
    ): JobbsøkerFormidlingRespons {
        return dataSource.executeInTransaction { conn ->
            val totalt = hentTotaltAntallJobbsøkere(conn, where)
            val responsSide = beregnResponsSide(request.side, request.antallPerSide, totalt)
            val jobbsøkere = if (totalt == 0L) {
                emptyList()
            } else {
                hentJobbsøkere(conn, where, responsSide, request.antallPerSide)
            }
            JobbsøkerFormidlingRespons(
                totalt = totalt,
                side = responsSide,
                jobbsøkere = jobbsøkere,
            )
        }
    }

    private fun beregnResponsSide(forespurtSide: Int, antallPerSide: Int, totalt: Long): Int {
        if (forespurtSide <= 1 || totalt <= 0L) return 1
        val sisteTilgjengeligeSide = (((totalt - 1) / antallPerSide) + 1).toInt()
        return minOf(forespurtSide, sisteTilgjengeligeSide)
    }

    private fun byggJobbsøkerWhereForAlle(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
    ): WhereClause = tilWhereClause(byggJobbsøkerBasisFilter(treffId, request))

    private fun byggJobbsøkerWhereForEgne(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): WhereClause {
        val conditions = byggJobbsøkerBasisFilter(treffId, request) +
            byggVeilederEllerEnhetFilter(veilederNavIdent, tilknyttedeEnheter)

        return tilWhereClause(conditions)
    }

    private fun byggJobbsøkerBasisFilter(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
    ): List<Condition> = buildList {
        add(Condition("rt.id = ?", SqlParam.Uuid(treffId.somUuid)))
        add(Condition("j.status != 'SLETTET'"))
        byggFritekstFilter(request.fritekst)?.let(::add)
    }

    private fun byggFritekstFilter(fritekst: String?): Condition? {
        val trimmed = fritekst?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (trimmed.matches(Regex("\\d{1,11}"))) {
            Condition("j.fodselsnummer LIKE ?", SqlParam.Text("$trimmed%"))
        } else {
            val søkeord = "${trimmed.lowercase()}%"
            Condition(
                "(LOWER(j.fornavn) LIKE ? OR LOWER(j.etternavn) LIKE ?)",
                SqlParam.Text(søkeord),
                SqlParam.Text(søkeord),
            )
        }
    }

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

    private fun tilWhereClause(conditions: List<Condition>): WhereClause {
        return WhereClause(
            sql = "WHERE " + conditions.joinToString(" AND ") { it.sql },
            params = conditions.flatMap { it.params },
        )
    }

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

    private fun hentTotaltAntallJobbsøkere(conn: Connection, where: WhereClause): Long {
        val sql = """
            SELECT count(*)
            FROM jobbsoker j
            JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
            ${where.sql}
        """.trimIndent()
        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            settWhereParametere(stmt, where.params)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    private fun hentJobbsøkere(
        conn: Connection,
        where: WhereClause,
        side: Int,
        antallPerSide: Int,
    ): List<JobbsøkerFormidlingTreff> {
        val sql = """
            SELECT j.id::text AS person_treff_id,
                   j.fodselsnummer,
                   j.fornavn,
                   j.etternavn
            FROM jobbsoker j
            JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
            ${where.sql}
            ORDER BY LOWER(j.etternavn), LOWER(j.fornavn), j.id
            LIMIT ? OFFSET ?
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            settWhereParametere(stmt, where.params)
            val pagIdx = where.params.size
            stmt.setInt(pagIdx + 1, antallPerSide)
            stmt.setLong(pagIdx + 2, (side - 1).toLong() * antallPerSide.toLong())
            stmt.executeQuery().use { rs ->
                val resultater = mutableListOf<JobbsøkerFormidlingTreff>()
                while (rs.next()) {
                    resultater += JobbsøkerFormidlingTreff(
                        personTreffId = rs.getString("person_treff_id"),
                        fødselsnummer = rs.getString("fodselsnummer"),
                        fornavn = rs.getString("fornavn"),
                        etternavn = rs.getString("etternavn"),
                    )
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
            is SqlParam.TextArray -> {
                stmt.setArray(index, stmt.connection.createArrayOf("text", param.value.toTypedArray()))
            }
        }
    }
}
