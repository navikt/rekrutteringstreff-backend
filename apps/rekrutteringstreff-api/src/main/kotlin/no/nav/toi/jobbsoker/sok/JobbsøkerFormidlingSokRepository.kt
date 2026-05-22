package no.nav.toi.jobbsoker.sok

import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource

class JobbsøkerFormidlingSokRepository(private val dataSource: DataSource) {

    companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
    }

    fun hentAlleForFormidling(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
    ): JobbsøkerFormidlingRespons {
        val (where, params) = byggJobbsøkerWhereForAlle(treffId, request)
        return hentJobbsøkereMedWhere(where, params, request)
    }

    fun hentEgneForFormidling(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): JobbsøkerFormidlingRespons {
        val (where, params) = byggJobbsøkerWhereForEgne(treffId, request, veilederNavIdent, tilknyttedeEnheter)
        return hentJobbsøkereMedWhere(where, params, request)
    }

    private fun hentJobbsøkereMedWhere(
        where: String,
        params: List<Any>,
        request: JobbsøkerFormidlingRequest,
    ): JobbsøkerFormidlingRespons {
        return dataSource.executeInTransaction { conn ->
            val totalt = hentTotaltAntallJobbsøkere(conn, where, params)
            val responsSide = beregnResponsSide(request.side, request.antallPerSide, totalt)
            val jobbsøkere = if (totalt == 0L) {
                emptyList()
            } else {
                hentJobbsøkere(conn, where, params, responsSide, request.antallPerSide)
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
    ): Pair<String, List<Any>> {
        val (conditions, params) = byggJobbsøkerBasisFilter(treffId, request)
        return tilWhereClause(conditions, params)
    }

    private fun byggJobbsøkerBasisFilter(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
    ): Pair<MutableList<String>, MutableList<Any>> {
        val conditions = mutableListOf(
            "rt.id = ?",
            "j.status != 'SLETTET'",
        )
        val params = mutableListOf<Any>(treffId.somUuid)

        request.fritekst?.takeIf { it.isNotBlank() }?.let {
            val trimmed = it.trim()
            if (trimmed.matches(Regex("\\d{11}"))) {
                conditions += "j.fodselsnummer = ?"
                params += trimmed
            } else {
                conditions += "(LOWER(j.fornavn) LIKE ? OR LOWER(j.etternavn) LIKE ?)"
                params += "${trimmed.lowercase()}%"
                params += "${trimmed.lowercase()}%"
            }
        }

        return conditions to params
    }

    private fun byggJobbsøkerWhereForEgne(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): Pair<String, List<Any>> {
        val (conditions, params) = byggJobbsøkerBasisFilter(treffId, request)
        val rensedeEnheter = tilknyttedeEnheter.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()

        if (rensedeEnheter.isEmpty()) {
            conditions += "UPPER(j.veileder_navident) = ?"
        } else {
            val placeholders = rensedeEnheter.joinToString(",") { "?" }
            conditions += "(UPPER(j.veileder_navident) = ? OR j.kontornummer IN ($placeholders))"
        }

        params += veilederNavIdent.trim().uppercase()
        rensedeEnheter.forEach { params += it }

        return tilWhereClause(conditions, params)
    }

    private fun tilWhereClause(conditions: List<String>, params: List<Any>): Pair<String, List<Any>> {
        return "WHERE " + conditions.joinToString(" AND ") to params
    }

    private fun hentTotaltAntallJobbsøkere(conn: Connection, where: String, params: List<Any>): Long {
        val sql = """
            SELECT count(*)
            FROM jobbsoker j
            JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
            $where
        """.trimIndent()
        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            params.forEachIndexed { i, p -> settParam(stmt, i + 1, p) }
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    private fun hentJobbsøkere(
        conn: Connection,
        where: String,
        params: List<Any>,
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
            $where
            ORDER BY LOWER(j.etternavn), LOWER(j.fornavn), j.id
            LIMIT ? OFFSET ?
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            params.forEachIndexed { i, p -> settParam(stmt, i + 1, p) }
            val pagIdx = params.size
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

    private fun settParam(stmt: PreparedStatement, index: Int, value: Any) {
        when (value) {
            is java.util.UUID -> stmt.setObject(index, value)
            is String -> stmt.setString(index, value)
            is Int -> stmt.setInt(index, value)
            is Long -> stmt.setLong(index, value)
            is Boolean -> stmt.setBoolean(index, value)
            else -> stmt.setObject(index, value)
        }
    }
}
