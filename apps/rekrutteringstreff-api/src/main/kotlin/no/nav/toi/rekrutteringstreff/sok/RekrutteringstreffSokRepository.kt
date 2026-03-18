package no.nav.toi.rekrutteringstreff.sok

import java.sql.ResultSet
import javax.sql.DataSource

class RekrutteringstreffSokRepository(private val dataSource: DataSource) {

    fun sok(
        navIdent: String?,
        kontorId: String?,
        statuser: List<Visningsstatus>?,
        kontorer: List<String>?,
        visning: Visning,
        sortering: Sortering = Sortering.SIST_OPPDATERTE,
        side: Int,
        antallPerSide: Int,
    ): Pair<List<RekrutteringstreffSokTreff>, Long> {
        val (whereClause, params) = byggWhere(navIdent, kontorId, statuser, kontorer, visning)

        val countSql = "SELECT count(*) FROM rekrutteringstreff_sok_view $whereClause"
        val antallTotalt = dataSource.connection.use { c ->
            c.prepareStatement(countSql).use { s ->
                params.forEachIndexed { i, p -> settParam(s, i + 1, p) }
                s.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

        val sql = """
            SELECT id, tittel, beskrivelse, visningsstatus, fra_tid, til_tid, svarfrist,
                   gateadresse, postnummer, poststed, kommune, fylke,
                   opprettet_av_tidspunkt, sist_endret, eiere, kontorer
            FROM rekrutteringstreff_sok_view
            $whereClause
            ORDER BY ${sortering.sql}
            LIMIT ? OFFSET ?
        """.trimIndent()

        val treff = dataSource.connection.use { c ->
            c.prepareStatement(sql).use { s ->
                params.forEachIndexed { i, p -> settParam(s, i + 1, p) }
                s.setInt(params.size + 1, antallPerSide)
                s.setInt(params.size + 2, (side - 1) * antallPerSide)
                s.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) tilTreff(rs) else null }.toList()
                }
            }
        }

        return Pair(treff, antallTotalt)
    }

    fun statusaggregering(
        navIdent: String?,
        kontorId: String?,
        kontorer: List<String>?,
        visning: Visning,
    ): List<FilterValg> {
        val (whereClause, params) = byggWhere(navIdent, kontorId, statuser = null, kontorer = kontorer, visning = visning)

        val sql = """
            SELECT visningsstatus, count(*) AS antall
            FROM rekrutteringstreff_sok_view
            $whereClause
            GROUP BY visningsstatus
            ORDER BY visningsstatus
        """.trimIndent()

        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { s ->
                params.forEachIndexed { i, p -> settParam(s, i + 1, p) }
                s.executeQuery().use { rs ->
                    generateSequence {
                        if (rs.next()) FilterValg(
                            verdi = rs.getString("visningsstatus"),
                            antall = rs.getLong("antall"),
                        ) else null
                    }.toList()
                }
            }
        }
    }

    private data class SqlParam(val value: Any, val type: ParamType)
    private data class Condition(val clause: String, val params: List<SqlParam>)
    private enum class ParamType { STRING, STRING_ARRAY, VISNINGSSTATUS_ARRAY }

    /**
     * Bygger SQL-fragmentet for `WHERE` og tilhørende params for søk mot `rekrutteringstreff_sok_view`.
     *
     * Resultatet er enten tom streng når ingen filtre er satt, eller en `WHERE`-clause
     * sammensatt av vilkår for visning, visningsstatus og kontorer.
     */
    private fun byggWhere(
        navIdent: String?,
        kontorId: String?,
        statuser: List<Visningsstatus>?,
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

            if (!statuser.isNullOrEmpty()) {
                add(
                    Condition(
                        clause = "visningsstatus = ANY(?)",
                        params = listOf(SqlParam(statuser, ParamType.VISNINGSSTATUS_ARRAY)),
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
            ParamType.VISNINGSSTATUS_ARRAY -> {
                @Suppress("UNCHECKED_CAST")
                val arr = (param.value as List<Visningsstatus>).map { it.name }
                s.setArray(index, s.connection.createArrayOf("text", arr.toTypedArray()))
            }
        }
    }

    private fun tilTreff(rs: ResultSet): RekrutteringstreffSokTreff {
        val eiereArr = rs.getArray("eiere")?.array as? Array<*>
        val kontorerArr = rs.getArray("kontorer")?.array as? Array<*>
        return RekrutteringstreffSokTreff(
            id = rs.getString("id"),
            tittel = rs.getString("tittel"),
            beskrivelse = rs.getString("beskrivelse"),
            visningsstatus = Visningsstatus.valueOf(rs.getString("visningsstatus")),
            fraTid = rs.getTimestamp("fra_tid")?.toInstant()?.toString(),
            tilTid = rs.getTimestamp("til_tid")?.toInstant()?.toString(),
            svarfrist = rs.getTimestamp("svarfrist")?.toInstant()?.toString(),
            gateadresse = rs.getString("gateadresse"),
            postnummer = rs.getString("postnummer"),
            poststed = rs.getString("poststed"),
            kommune = rs.getString("kommune"),
            fylke = rs.getString("fylke"),
            opprettetAvTidspunkt = rs.getTimestamp("opprettet_av_tidspunkt").toInstant().toString(),
            sistEndret = rs.getTimestamp("sist_endret").toInstant().toString(),
            eiere = eiereArr?.map { it.toString() } ?: emptyList(),
            kontorer = kontorerArr?.map { it.toString() } ?: emptyList(),
        )
    }
}
