package no.nav.toi.rekrutteringstreff.sok

import java.sql.Connection
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
        val (whereForStatusaggregering, paramsForStatusaggregering) = byggWhere(
            navIdent = navIdent,
            kontorId = kontorId,
            statuser = null,
            publisertApen = null,
            publisertFristUtgatt = null,
            kontorer = kontorer,
            visning = visning,
        )
        val (whereForTreffliste, paramsForTreffliste) = byggWhere(
            navIdent = navIdent,
            kontorId = kontorId,
            statuser = statuser,
            publisertApen = publisertApen,
            publisertFristUtgatt = publisertFristUtgatt,
            kontorer = kontorer,
            visning = visning,
        )

        return dataSource.connection.use { conn ->
            SokMedAggregeringResultat(
                treff = hentTreff(conn, whereForTreffliste, paramsForTreffliste, sortering, side, antallPerSide),
                antallTotalt = hentAntallTotalt(conn, whereForTreffliste, paramsForTreffliste),
                statusaggregering = hentStatusaggregering(conn, whereForStatusaggregering, paramsForStatusaggregering),
            )
        }
    }

    private fun hentAntallTotalt(conn: Connection, where: String, params: List<SqlParam>): Long {
        val sql = "SELECT count(*) FROM rekrutteringstreff_sok_view $where"
        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            settWhereParametere(stmt, params)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    private fun hentTreff(
        conn: Connection,
        where: String,
        params: List<SqlParam>,
        sortering: Sortering,
        side: Int,
        antallPerSide: Int,
    ): List<RekrutteringstreffSokTreff> {
        val sql = """
            SELECT id, tittel, beskrivelse, status, frist_utgatt, fra_tid, til_tid, svarfrist,
                   gateadresse, postnummer, poststed,
                   opprettet_av_person_navident, opprettet_av_tidspunkt, sist_endret,
                   eiere, kontorer,
                   antall_arbeidsgivere, antall_jobbsokere
            FROM rekrutteringstreff_sok_view
            $where
            ORDER BY ${sortering.sql}
            LIMIT ? OFFSET ?
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            settWhereParametere(stmt, params)
            settPagineringParametere(stmt, antallWhereParametere = params.size, side = side, antallPerSide = antallPerSide)
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) tilTreff(rs) else null }.toList()
            }
        }
    }

    private fun hentStatusaggregering(conn: Connection, where: String, params: List<SqlParam>): List<FilterValg> {
        val sql = """
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
            $where
            GROUP BY aggregert_status
            ORDER BY aggregert_status
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            settWhereParametere(stmt, params)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(FilterValg(
                            verdi = SokStatus.fraJsonVerdi(rs.getString("aggregert_status")),
                            antall = rs.getLong("antall"),
                        ))
                    }
                }
            }
        }
    }

    private fun settWhereParametere(stmt: java.sql.PreparedStatement, params: List<SqlParam>) {
        params.forEachIndexed { index, param ->
            settParam(stmt, index + 1, param)
        }
    }

    private fun settPagineringParametere(
        stmt: java.sql.PreparedStatement,
        antallWhereParametere: Int,
        side: Int,
        antallPerSide: Int,
    ) {
        val limitParameterIndex = antallWhereParametere + 1
        val offsetParameterIndex = antallWhereParametere + 2
        val offset = beregnOffset(side, antallPerSide)

        stmt.setInt(limitParameterIndex, antallPerSide)
        stmt.setLong(offsetParameterIndex, offset)
    }

    private fun beregnOffset(side: Int, antallPerSide: Int): Long {
        return (side - 1).toLong() * antallPerSide.toLong()
    }

    private data class SqlParam(val value: Any, val type: ParamType)
    private data class Condition(val clause: String, val params: List<SqlParam>)
    private data class Statusfilter(
        val eksplisitteStatuser: List<SokStatus>,
        val taMedPublisertApen: Boolean,
        val taMedPublisertFristUtgatt: Boolean,
    )
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
        val conditions = listOfNotNull(
            byggVisningsCondition(visning, navIdent, kontorId),
            byggStatusCondition(statuser, publisertApen, publisertFristUtgatt),
            byggKontorCondition(kontorer),
        )

        val whereClause = conditions
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "WHERE ", separator = " AND ") { it.clause }
            ?: ""
        val params = conditions.flatMap { it.params }

        return Pair(whereClause, params)
    }

    private fun byggVisningsCondition(
        visning: Visning,
        navIdent: String?,
        kontorId: String?,
    ): Condition? {
        return when (visning) {
            Visning.MINE -> Condition(
                clause = "? = ANY(eiere)",
                params = listOf(SqlParam(navIdent ?: "", ParamType.STRING)),
            )
            Visning.MITT_KONTOR -> Condition(
                clause = "kontorer && ?::text[]",
                params = listOf(SqlParam(listOf(kontorId ?: ""), ParamType.STRING_ARRAY)),
            )
            Visning.ALLE, Visning.VALGTE_KONTORER -> null
        }
    }

    private fun byggStatusCondition(
        statuser: List<SokStatus>?,
        publisertApen: Boolean?,
        publisertFristUtgatt: Boolean?,
    ): Condition? {
        val statusfilter = byggStatusfilter(statuser, publisertApen, publisertFristUtgatt)
        val statusClauses = buildList {
            if (statusfilter.eksplisitteStatuser.isNotEmpty()) {
                add("status = ANY(?)")
            }
            if (statusfilter.taMedPublisertApen) {
                add("(status = 'PUBLISERT' AND frist_utgatt = false)")
            }
            if (statusfilter.taMedPublisertFristUtgatt) {
                add("(status = 'PUBLISERT' AND frist_utgatt = true)")
            }
        }

        if (statusClauses.isEmpty()) {
            return null
        }

        return Condition(
            clause = "(${statusClauses.joinToString(" OR ")})",
            params = byggStatusParametere(statusfilter),
        )
    }

    private fun byggStatusfilter(
        statuser: List<SokStatus>?,
        publisertApen: Boolean?,
        publisertFristUtgatt: Boolean?,
    ): Statusfilter {
        val eksplisitteStatuser = statuser.orEmpty().filterNot {
            it == SokStatus.PUBLISERT_APEN || it == SokStatus.PUBLISERT_FRIST_UTGATT
        }

        return Statusfilter(
            eksplisitteStatuser = eksplisitteStatuser,
            taMedPublisertApen = publisertApen == true || statuser.orEmpty().contains(SokStatus.PUBLISERT_APEN),
            taMedPublisertFristUtgatt = publisertFristUtgatt == true || statuser.orEmpty().contains(SokStatus.PUBLISERT_FRIST_UTGATT),
        )
    }

    private fun byggStatusParametere(statusfilter: Statusfilter): List<SqlParam> {
        if (statusfilter.eksplisitteStatuser.isEmpty()) {
            return emptyList()
        }

        return listOf(SqlParam(statusfilter.eksplisitteStatuser, ParamType.STATUS_ARRAY))
    }

    private fun byggKontorCondition(kontorer: List<String>?): Condition? {
        if (kontorer.isNullOrEmpty()) {
            return null
        }

        return Condition(
            clause = "kontorer && ?::text[]",
            params = listOf(SqlParam(kontorer, ParamType.STRING_ARRAY)),
        )
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
