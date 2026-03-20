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
        publisertApen: Boolean?,
        publisertFristUtgatt: Boolean?,
        kontorer: List<String>?,
        visning: Visning,
        sortering: Sortering = Sortering.SIST_OPPDATERTE,
        side: Int,
        antallPerSide: Int,
    ): SokMedAggregeringResultat {
        val (baseWhere, baseParams) = byggWhere(navIdent, kontorId, statuser = null, publisertApen = null, publisertFristUtgatt = null, kontorer = kontorer, visning = visning)
        val (fullWhere, fullParams) = byggWhere(navIdent, kontorId, statuser, publisertApen, publisertFristUtgatt, kontorer, visning)

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
                SELECT id, tittel, beskrivelse, status, frist_utgatt, fra_tid, til_tid, svarfrist,
                       gateadresse, postnummer, poststed,
                       opprettet_av_person_navident, opprettet_av_tidspunkt, sist_endret,
                       eiere, kontorer,
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
                SELECT
                    CASE
                        WHEN status = 'PUBLISERT' AND frist_utgatt = false THEN 'publisert_apen'
                        WHEN status = 'PUBLISERT' AND frist_utgatt = true THEN 'publisert_frist_utgatt'
                        WHEN status = 'UTKAST' THEN 'utkast'
                        WHEN status = 'FULLFØRT' THEN 'fullfort'
                        WHEN status = 'AVLYST' THEN 'avlyst'
                        ELSE lower(status)
                    END AS aggregert_status,
                    count(*) AS antall
                FROM rekrutteringstreff_sok_view
                $baseWhere
                GROUP BY aggregert_status
                ORDER BY aggregert_status
            """.trimIndent()

            val statusaggregering = conn.prepareStatement(aggSql).use { s ->
                s.queryTimeout = QUERY_TIMEOUT_SECONDS
                baseParams.forEachIndexed { i, p -> settParam(s, i + 1, p) }
                s.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(FilterValg(
                                verdi = rs.getString("aggregert_status"),
                                antall = rs.getLong("antall"),
                            ))
                        }
                    }
                }
            }

            SokMedAggregeringResultat(
                treff = treff,
                antallTotalt = antallTotalt,
                statusaggregering = statusaggregering,
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
        publisertApen: Boolean?,
        publisertFristUtgatt: Boolean?,
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

            val statusConditions = mutableListOf<String>()
            val statusParams = mutableListOf<SqlParam>()

            val domeneStatuser = mutableListOf<SokStatus>()
            var includePublisertApen = publisertApen == true
            var includePublisertFristUtgatt = publisertFristUtgatt == true

            if (!statuser.isNullOrEmpty()) {
                statuser.forEach { status ->
                    when (status) {
                        SokStatus.PUBLISERT_APEN -> includePublisertApen = true
                        SokStatus.PUBLISERT_FRIST_UTGATT -> includePublisertFristUtgatt = true
                        else -> domeneStatuser.add(status)
                    }
                }
            }
            if (domeneStatuser.isNotEmpty()) {
                statusConditions.add("status = ANY(?)")
                statusParams.add(SqlParam(domeneStatuser, ParamType.STATUS_ARRAY))
            }
            if (includePublisertApen) {
                statusConditions.add("(status = 'PUBLISERT' AND frist_utgatt = false)")
            }
            if (includePublisertFristUtgatt) {
                statusConditions.add("(status = 'PUBLISERT' AND frist_utgatt = true)")
            }

            if (statusConditions.isNotEmpty()) {
                add(
                    Condition(
                        clause = "(${statusConditions.joinToString(" OR ")})",
                        params = statusParams,
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
            status = SokStatus.fraDbVerdiMedFrist(rs.getString("status"), rs.getBoolean("frist_utgatt")),
            fraTid = rs.getTimestamp("fra_tid")?.toInstant(),
            tilTid = rs.getTimestamp("til_tid")?.toInstant(),
            svarfrist = rs.getTimestamp("svarfrist")?.toInstant(),
            gateadresse = rs.getString("gateadresse"),
            postnummer = rs.getString("postnummer"),
            poststed = rs.getString("poststed"),
            opprettetAv = rs.getString("opprettet_av_person_navident"),
            opprettetAvTidspunkt = rs.getTimestamp("opprettet_av_tidspunkt").toInstant(),
            sistEndret = rs.getTimestamp("sist_endret").toInstant(),
            eiere = eiereArr?.map { it.toString() } ?: emptyList(),
            kontorer = kontorerArr?.map { it.toString() } ?: emptyList(),
            antallArbeidsgivere = rs.getLong("antall_arbeidsgivere"),
            antallJobbsokere = rs.getLong("antall_jobbsokere"),
        )
    }
}
