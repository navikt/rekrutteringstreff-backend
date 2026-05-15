package no.nav.toi.jobbsoker.sok

import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource

class JobbsøkerFormidlingRepository(private val dataSource: DataSource) {

    companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
    }

    /**
     * Henter jobbsøkere for formidling-modalen, paginert.
     * Inkluderer skjulte jobbsøkere (er_synlig = false), men ikke slettede.
     *
     * @param kunForVeilederNavIdent hvis satt, returneres bare jobbsøkere der
     * lagret veileder_navident matcher denne identen.
     */
    fun hentForFormidling(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
        kunForVeilederNavIdent: String? = null,
    ): JobbsøkerFormidlingRespons {
        val (where, params) = byggWhere(treffId, request, kunForVeilederNavIdent)
        return dataSource.executeInTransaction { conn ->
            val totalt = hentTotalt(conn, where, params)
            val responsSide = beregnResponsSide(request.side, request.antallPerSide, totalt)
            val jobbsøkere = if (totalt == 0L) {
                emptyList()
            } else {
                hentTreff(conn, where, params, responsSide, request.antallPerSide)
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

    private fun byggWhere(
        treffId: TreffId,
        request: JobbsøkerFormidlingRequest,
        kunForVeilederNavIdent: String?,
    ): Pair<String, List<Any>> {
        val conditions = mutableListOf(
            "rt.id = ?",
            "j.status != 'SLETTET'",
        )
        val params = mutableListOf<Any>(treffId.somUuid)

        request.fritekst?.takeIf { it.isNotBlank() }?.let {
            if (it.trim().matches(Regex("\\d{11}"))) {
                conditions += "j.fodselsnummer = ?"
                params += it.trim()
            } else {
                val escaped = it.lowercase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_")
                conditions += "(LOWER(j.fornavn) LIKE ? ESCAPE '\\' OR LOWER(j.etternavn) LIKE ? ESCAPE '\\')"
                params += "${escaped}%"
                params += "${escaped}%"
            }
        }

        if (kunForVeilederNavIdent != null) {
            conditions += "j.veileder_navident = ?"
            params += kunForVeilederNavIdent
        }

        val whereClause = "WHERE " + conditions.joinToString(" AND ")
        return whereClause to params
    }

    private fun hentTotalt(conn: Connection, where: String, params: List<Any>): Long {
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

    private fun hentTreff(
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
                   j.etternavn,
                   j.status
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
                        status = JobbsøkerStatus.valueOf(rs.getString("status")),
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
