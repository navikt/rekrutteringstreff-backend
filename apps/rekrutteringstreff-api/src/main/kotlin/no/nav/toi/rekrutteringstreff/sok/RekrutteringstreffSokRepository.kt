package no.nav.toi.rekrutteringstreff.sok

import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
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
        publisertStatuser: List<PublisertStatus>?,
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
            publisertStatuser = null,
            kontorer = kontorer,
            visning = visning,
        )
        val (whereForTreffliste, paramsForTreffliste) = byggWhere(
            navIdent = navIdent,
            kontorId = kontorId,
            statuser = statuser,
            publisertStatuser = publisertStatuser,
            kontorer = kontorer,
            visning = visning,
        )

        return dataSource.connection.use { conn ->
            SokMedAggregeringResultat(
                treff = hentTreff(conn, whereForTreffliste, paramsForTreffliste, sortering, side, antallPerSide),
                antallTotalt = hentAntallTotalt(conn, whereForTreffliste, paramsForTreffliste),
                statusaggregering = hentStatusaggregering(conn, whereForStatusaggregering, paramsForStatusaggregering),
                publisertstatusaggregering = hentPublisertStatusaggregering(conn, whereForStatusaggregering, paramsForStatusaggregering),
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
        val sqlStatuser = """
            SELECT
                status AS aggregert_status,
                count(*) AS antall
            FROM rekrutteringstreff_sok_view
            $where
            GROUP BY aggregert_status
            ORDER BY aggregert_status
        """.trimIndent()
        val statusResultat = conn.prepareStatement(sqlStatuser).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            settWhereParametere(stmt, params)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            FilterValg(
                                verdi = rs.getString("aggregert_status"),
                                antall = rs.getLong("antall"),
                            )
                        )
                    }
                }
            }
        }
        return statusResultat
    }

    private fun hentPublisertStatusaggregering(conn: Connection, where: String, params: List<SqlParam>): List<FilterValg> {
        val whereSeparator = if (where.isNotEmpty()) " and " else " WHERE "
        val sqlPubliserteStatuser = """
            SELECT
                CASE
                    WHEN frist_utgatt = false THEN 'SØKNADSFRIST_PASSERT'
                    WHEN frist_utgatt = true THEN 'ÅPEN_FOR_SØKERE'
                END AS aggregert_status,
                count(*) AS antall
            FROM rekrutteringstreff_sok_view
            $where $whereSeparator status = 'PUBLISERT'
            GROUP BY aggregert_status
            ORDER BY aggregert_status
        """.trimIndent()
         val publiserteStatusResultat = conn.prepareStatement(sqlPubliserteStatuser).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            settWhereParametere(stmt, params)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            FilterValg(
                                verdi = rs.getString("aggregert_status"),
                                antall = rs.getLong("antall"),
                            )
                        )
                    }
                }
            }
        }
        return publiserteStatusResultat
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
        val statuser: List<SokStatus>,
        val publisertStatuser: List<PublisertStatus>?,
    )
    private enum class ParamType { STRING, STRING_ARRAY, STATUS_ARRAY, BOOLEAN }

    private fun byggWhere(
        navIdent: String?,
        kontorId: String?,
        statuser: List<SokStatus>?,
        publisertStatuser: List<PublisertStatus>?,
        kontorer: List<String>?,
        visning: Visning,
    ): Pair<String, List<SqlParam>> {
        val conditions = listOfNotNull(
            byggVisningsCondition(visning, navIdent, kontorId),
            byggStatusCondition(statuser, publisertStatuser),
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
        publisertStatuser: List<PublisertStatus>?,
    ): Condition? {
        val erPublishedValgt = statuser?.contains(SokStatus.PUBLISERT) ?: false
        val statuserUtenPublisertStatus = statuser?.filterNot { it == SokStatus.PUBLISERT }

        val statusClauses = buildList {
            if (!statuser.isNullOrEmpty()) {
                if (!statuserUtenPublisertStatus.isNullOrEmpty()) {
                    add("status = ANY(?)")
                }

                if (erPublishedValgt) {
                    if (publisertStatuser.isNullOrEmpty()) {
                        add("status = 'PUBLISERT'")
                    } else {
                        if (publisertStatuser.contains(PublisertStatus.ÅPEN_FOR_SØKERE)) {
                            add("(status = 'PUBLISERT' AND frist_utgatt = false)")
                        }
                        if (publisertStatuser.contains(PublisertStatus.SØKNADSFRIST_PASSERT)) {
                            add("(status = 'PUBLISERT' AND frist_utgatt = true)")
                        }
                    }
                }
            }



        }

        if (statusClauses.isEmpty()) {
            return null
        }

        return Condition(
            clause = "(${statusClauses.joinToString(" OR ")})",
            params = byggStatusParametere(statuserUtenPublisertStatus),
        )
    }

    private fun byggStatusfilter(
        statuser: List<SokStatus>,
        publisertStatuser: List<PublisertStatus>?,
    ): Statusfilter {
        return Statusfilter(
            statuser = statuser,
            publisertStatuser = publisertStatuser,
        )
    }

    private fun byggStatusParametere(statuser: List<SokStatus>?): List<SqlParam> {
        if (statuser.isNullOrEmpty()) {
            return emptyList()
        }
        return listOf(SqlParam(statuser, ParamType.STATUS_ARRAY))
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
        val status = RekrutteringstreffStatus.valueOf(rs.getString("status"))
        return RekrutteringstreffSokTreff(
            id = rs.getString("id"),
            tittel = rs.getString("tittel"),
            beskrivelse = rs.getString("beskrivelse"),
            status = status,
            publisertStatus = PublisertStatus.fraDbVerdiMedFrist(status, rs.getBoolean("frist_utgatt")),
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
