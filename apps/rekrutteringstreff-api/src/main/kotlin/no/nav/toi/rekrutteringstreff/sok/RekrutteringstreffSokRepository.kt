package no.nav.toi.rekrutteringstreff.sok

import java.sql.ResultSet
import javax.sql.DataSource

class RekrutteringstreffSokRepository(private val dataSource: DataSource) {

    companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
    }

    fun sokMedAggregering(
        navIdent: String?,
        kontorId: String?,
        statuser: List<SokStatus>?,
        apenForSokere: Boolean?,
        kontorer: List<String>?,
        visning: Visning,
        sortering: Sortering = Sortering.SIST_OPPDATERTE,
        side: Int,
        antallPerSide: Int,
    ): SokMedAggregeringResultat {
        val (baseWhere, baseParams) = byggWhere(navIdent, kontorId, statuser = null, apenForSokere = null, kontorer = kontorer, visning = visning)
        val (fullWhere, fullParams) = byggWhere(navIdent, kontorId, statuser, apenForSokere, kontorer, visning)

        return dataSource.connection.use { conn ->
            val countSql = "SELECT count(*) FROM rekrutteringstreff_sok_view $fullWhere"
            val antallTotalt = conn.prepareStatement(countSql).use { s ->
                s.queryTimeout = QUERY_TIMEOUT_SECONDS
                fullParams.forEachIndexed { i, p -> settParam(s, i + 1, p) }
                s.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }

            val mainSql = """
                SELECT id, tittel, beskrivelse, status, apen_for_sokere, fra_tid, til_tid, svarfrist,
                       gateadresse, postnummer, poststed,
                       opprettet_av_tidspunkt, sist_endret, eiere, kontorer,
                       antall_arbeidsgivere, antall_jobbsokere
                FROM rekrutteringstreff_sok_view
                $fullWhere
                ORDER BY ${sortering.sql}
                LIMIT ? OFFSET ?
            """.trimIndent()

            val treff = conn.prepareStatement(mainSql).use { s ->
                s.queryTimeout = QUERY_TIMEOUT_SECONDS
                fullParams.forEachIndexed { i, p -> settParam(s, i + 1, p) }
                val offset = (side - 1).toLong() * antallPerSide.toLong()
                s.setInt(fullParams.size + 1, antallPerSide)
                s.setLong(fullParams.size + 2, offset)
                s.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) tilTreff(rs) else null }.toList()
                }
            }

            val aggSql = """
                SELECT status, count(*) AS antall,
                       count(*) FILTER (WHERE apen_for_sokere = true) AS antall_apen
                FROM rekrutteringstreff_sok_view
                $baseWhere
                GROUP BY status
                ORDER BY status
            """.trimIndent()

            var antallApenForSokere = 0L
            val statusaggregering = conn.prepareStatement(aggSql).use { s ->
                s.queryTimeout = QUERY_TIMEOUT_SECONDS
                baseParams.forEachIndexed { i, p -> settParam(s, i + 1, p) }
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val dbStatus = rs.getString("status")
                            add(FilterValg(
                                verdi = SokStatus.fraDbVerdi(dbStatus).jsonVerdi,
                                antall = rs.getLong("antall"),
                            ))
                            if (dbStatus == "PUBLISERT") {
                                antallApenForSokere = rs.getLong("antall_apen")
                            }
                        }
                    }
                }
            }

            SokMedAggregeringResultat(
                treff = treff,
                antallTotalt = antallTotalt,
                statusaggregering = statusaggregering,
                antallApenForSokere = antallApenForSokere,
            )
        }
    }

    private data class SqlParam(val value: Any, val type: ParamType)
    private data class Condition(val clause: String, val params: List<SqlParam>)
    private enum class ParamType { STRING, STRING_ARRAY, STATUS_ARRAY, BOOLEAN }

    private fun byggWhere(
        navIdent: String?,
        kontorId: String?,
        statuser: List<SokStatus>?,
        apenForSokere: Boolean?,
        kontorer: List<String>?,
        visning: Visning,
    ): Pair<String, List<SqlParam>> {
        val conditions = buildList {
            when (visning) {
                Visning.MINE -> add(
                    Condition(
                        clause = "? = ANY(eiere)",
                        params = listOf(SqlParam(navIdent ?: "", ParamType.STRING)),
                    )
                )
                Visning.MITT_KONTOR -> add(
                    Condition(
                        clause = "kontorer && ?::text[]",
                        params = listOf(SqlParam(listOf(kontorId ?: ""), ParamType.STRING_ARRAY)),
                    )
                )
                Visning.ALLE, Visning.VALGTE_KONTORER -> Unit
            }

            if (!statuser.isNullOrEmpty() && apenForSokere == true) {
                add(
                    Condition(
                        clause = "(status = ANY(?) OR (status = 'PUBLISERT' AND apen_for_sokere = true))",
                        params = listOf(SqlParam(statuser, ParamType.STATUS_ARRAY)),
                    )
                )
            } else if (!statuser.isNullOrEmpty()) {
                add(
                    Condition(
                        clause = "status = ANY(?)",
                        params = listOf(SqlParam(statuser, ParamType.STATUS_ARRAY)),
                    )
                )
            } else if (apenForSokere == true) {
                add(
                    Condition(
                        clause = "(status = 'PUBLISERT' AND apen_for_sokere = true)",
                        params = emptyList(),
                    )
                )
            }

            if (!kontorer.isNullOrEmpty()) {
                add(
                    Condition(
                        clause = "kontorer && ?::text[]",
                        params = listOf(SqlParam(kontorer, ParamType.STRING_ARRAY)),
                    )
                )
            }
        }

        val whereClause = conditions
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "WHERE ", separator = " AND ") { it.clause }
            ?: ""
        val params = conditions.flatMap { it.params }

        return Pair(whereClause, params)
    }

    private fun settParam(s: java.sql.PreparedStatement, index: Int, param: SqlParam) {
        when (param.type) {
            ParamType.STRING -> s.setString(index, param.value as String)
            ParamType.STRING_ARRAY -> {
                @Suppress("UNCHECKED_CAST")
                val arr = param.value as List<String>
                s.setArray(index, s.connection.createArrayOf("text", arr.toTypedArray()))
            }
            ParamType.STATUS_ARRAY -> {
                @Suppress("UNCHECKED_CAST")
                val arr = (param.value as List<SokStatus>).map { it.name }
                s.setArray(index, s.connection.createArrayOf("text", arr.toTypedArray()))
            }
            ParamType.BOOLEAN -> s.setBoolean(index, param.value as Boolean)
        }
    }

    private fun tilTreff(rs: ResultSet): RekrutteringstreffSokTreff {
        val eiereArr = rs.getArray("eiere")?.array as? Array<*>
        val kontorerArr = rs.getArray("kontorer")?.array as? Array<*>
        return RekrutteringstreffSokTreff(
            id = rs.getString("id"),
            tittel = rs.getString("tittel"),
            beskrivelse = rs.getString("beskrivelse"),
            status = SokStatus.fraDbVerdi(rs.getString("status")),
            apenForSokere = rs.getBoolean("apen_for_sokere"),
            fraTid = rs.getTimestamp("fra_tid")?.toInstant()?.toString(),
            tilTid = rs.getTimestamp("til_tid")?.toInstant()?.toString(),
            svarfrist = rs.getTimestamp("svarfrist")?.toInstant()?.toString(),
            gateadresse = rs.getString("gateadresse"),
            postnummer = rs.getString("postnummer"),
            poststed = rs.getString("poststed"),
            opprettetAvTidspunkt = rs.getTimestamp("opprettet_av_tidspunkt").toInstant().toString(),
            sistEndret = rs.getTimestamp("sist_endret").toInstant().toString(),
            eiere = eiereArr?.map { it.toString() } ?: emptyList(),
            kontorer = kontorerArr?.map { it.toString() } ?: emptyList(),
            antallArbeidsgivere = rs.getLong("antall_arbeidsgivere"),
            antallJobbsokere = rs.getLong("antall_jobbsokere"),
        )
    }
}
