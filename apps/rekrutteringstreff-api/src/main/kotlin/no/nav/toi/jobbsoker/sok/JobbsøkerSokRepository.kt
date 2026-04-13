package no.nav.toi.jobbsoker.sok

import no.nav.toi.JacksonConfig
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource

class JobbsøkerSokRepository(private val dataSource: DataSource) {

    companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
    }

    fun sok(treffId: TreffId, request: JobbsøkerSøkRequest): JobbsøkerSøkRespons {
        return dataSource.executeInTransaction { conn ->
            val (where, params) = byggWhere(treffId, request)
            val totalt = hentTotalt(conn, where, params)
            val responsSide = beregnResponsSide(request.side, request.antallPerSide, totalt)
            val tellinger = hentTellinger(conn, treffId)
            val antallPerStatus = hentAntallPerStatus(conn, treffId, request)
            val treff = if (totalt == 0L) {
                emptyList()
            } else {
                hentTreff(
                    conn,
                    where,
                    params,
                    request.sorteringsfelt,
                    request.sorteringsretning,
                    responsSide,
                    request.antallPerSide,
                )
            }
            val minsideHendelser = hentMinsideHendelser(conn, treff.map { it.personTreffId })
            val jobbsøkereMedHendelser = treff.map { t ->
                t.copy(minsideHendelser = minsideHendelser[t.personTreffId] ?: emptyList())
            }
            JobbsøkerSøkRespons(
                totalt = totalt,
                antallSkjulte = tellinger.first,
                antallSlettede = tellinger.second,
                side = responsSide,
                jobbsøkere = jobbsøkereMedHendelser,
                antallPerStatus = antallPerStatus,
            )
        }
    }

    private fun beregnResponsSide(forespurtSide: Int, antallPerSide: Int, totalt: Long): Int {
        if (forespurtSide <= 1 || totalt <= 0L) return 1

        val sisteTilgjengeligeSide = (((totalt - 1) / antallPerSide) + 1).toInt()
        return minOf(forespurtSide, sisteTilgjengeligeSide)
    }

    private fun hentTellinger(conn: Connection, treffId: TreffId): Pair<Int, Int> {
        val sql = """
            SELECT
                COUNT(*) FILTER (WHERE j.status != 'SLETTET' AND j.er_synlig = FALSE) AS antall_skjulte,
                COUNT(*) FILTER (WHERE j.status = 'SLETTET') AS antall_slettede
            FROM jobbsoker j
            JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
            WHERE rt.id = ?
        """.trimIndent()
        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            stmt.setObject(1, treffId.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) Pair(rs.getInt("antall_skjulte"), rs.getInt("antall_slettede"))
                else Pair(0, 0)
            }
        }
    }

    private fun hentAntallPerStatus(conn: Connection, treffId: TreffId, request: JobbsøkerSøkRequest): Map<JobbsøkerStatus, Int> {
        val (where, params) = byggWhere(treffId, request.copy(status = null))
        val sql = """
            SELECT v.status, COUNT(*) AS antall
            FROM jobbsoker_sok_view v
            $where
            GROUP BY v.status
        """.trimIndent()
        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            params.forEachIndexed { index, param -> settParam(stmt, index + 1, param) }
            stmt.executeQuery().use { rs ->
                val result = mutableMapOf<JobbsøkerStatus, Int>()
                while (rs.next()) {
                    result[JobbsøkerStatus.valueOf(rs.getString("status"))] = rs.getInt("antall")
                }
                result
            }
        }
    }

    private fun hentTotalt(conn: Connection, where: String, params: List<Any>): Long {
        val sql = "SELECT count(*) FROM jobbsoker_sok_view v $where"
        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            params.forEachIndexed { index, param -> settParam(stmt, index + 1, param) }
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
        sorteringsfelt: JobbsøkerSorteringsfelt,
        sorteringsretning: JobbsøkerSorteringsretning,
        side: Int,
        antallPerSide: Int,
    ): List<JobbsøkerSøkTreff> {
        val sql = """
            SELECT v.person_treff_id::text, v.fodselsnummer,
                   v.fornavn, v.etternavn, v.navkontor,
                   v.veileder_navn, v.veileder_navident,
                   v.status, v.lagt_til_dato, v.lagt_til_av
            FROM jobbsoker_sok_view v
            $where
            ORDER BY ${sorteringsfelt.sql(sorteringsretning)}
            LIMIT ? OFFSET ?
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            params.forEachIndexed { index, param -> settParam(stmt, index + 1, param) }
            val pagIdx = params.size
            stmt.setInt(pagIdx + 1, antallPerSide)
            stmt.setLong(pagIdx + 2, (side - 1).toLong() * antallPerSide.toLong())
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.tilTreff() else null }.toList()
            }
        }
    }

    private fun byggWhere(treffId: TreffId, request: JobbsøkerSøkRequest): Pair<String, List<Any>> {
        val conditions = mutableListOf("v.treff_id = ?")
        val params = mutableListOf<Any>(treffId.somUuid)

        request.fritekst?.takeIf { it.isNotBlank() }?.let {
            if (it.trim().matches(Regex("\\d{11}"))) {
                conditions.add("v.fodselsnummer = ?")
                params.add(it.trim())
            } else {
                val escaped = it.lowercase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_")
                conditions.add("(LOWER(v.fornavn) LIKE ? ESCAPE '\\' OR LOWER(v.etternavn) LIKE ? ESCAPE '\\')")
                params.add("${escaped}%")
                params.add("${escaped}%")
            }
        }

        request.status?.takeIf { it.isNotEmpty() }?.let { statuser ->
            val placeholders = statuser.indices.joinToString(",") { "?" }
            conditions.add("v.status IN ($placeholders)")
            statuser.forEach { params.add(it.name) }
        }

        val whereClause = "WHERE " + conditions.joinToString(" AND ")
        return Pair(whereClause, params)
    }

    private fun settParam(stmt: PreparedStatement, index: Int, value: Any) {
        when (value) {
            is UUID -> stmt.setObject(index, value)
            is Long -> stmt.setLong(index, value)
            is String -> stmt.setString(index, value)
            is Int -> stmt.setInt(index, value)
            is Boolean -> stmt.setBoolean(index, value)
            else -> stmt.setObject(index, value)
        }
    }

    private fun ResultSet.tilTreff() = JobbsøkerSøkTreff(
        personTreffId = getString("person_treff_id"),
        fødselsnummer = getString("fodselsnummer"),
        fornavn = getString("fornavn"),
        etternavn = getString("etternavn"),
        navkontor = getString("navkontor"),
        veilederNavn = getString("veileder_navn"),
        veilederNavident = getString("veileder_navident"),
        status = JobbsøkerStatus.valueOf(getString("status")),
        lagtTilDato = getTimestamp("lagt_til_dato")?.toInstant(),
        lagtTilAv = getString("lagt_til_av"),
    )

    private fun hentMinsideHendelser(
        conn: Connection,
        personTreffIds: List<String>,
    ): Map<String, List<MinsideHendelseSøkDto>> {
        if (personTreffIds.isEmpty()) return emptyMap()

        val placeholders = personTreffIds.joinToString(",") { "?" }
        val sql = """
            SELECT j.id::text as person_treff_id, jh.id::text as hendelse_id, jh.tidspunkt,
                   jh.hendelsestype, jh.opprettet_av_aktortype, jh.aktøridentifikasjon, jh.hendelse_data
            FROM jobbsoker_hendelse jh
            JOIN jobbsoker j ON jh.jobbsoker_id = j.jobbsoker_id
            WHERE j.id IN ($placeholders)
            AND jh.hendelsestype = 'MOTTATT_SVAR_FRA_MINSIDE'
            ORDER BY jh.tidspunkt
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            personTreffIds.forEachIndexed { index, id ->
                stmt.setObject(index + 1, UUID.fromString(id))
            }
            stmt.executeQuery().use { rs ->
                val result = mutableMapOf<String, MutableList<MinsideHendelseSøkDto>>()
                while (rs.next()) {
                    val personTreffId = rs.getString("person_treff_id")
                    val rawData = rs.getString("hendelse_data")
                    val hendelse = MinsideHendelseSøkDto(
                        id = rs.getString("hendelse_id"),
                        tidspunkt = rs.getTimestamp("tidspunkt").toInstant().toString(),
                        hendelsestype = rs.getString("hendelsestype"),
                        opprettetAvAktørType = rs.getString("opprettet_av_aktortype"),
                        aktørIdentifikasjon = rs.getString("aktøridentifikasjon"),
                        hendelseData = rawData?.let { JacksonConfig.mapper.readTree(it) },
                    )
                    result.getOrPut(personTreffId) { mutableListOf() }.add(hendelse)
                }
                result
            }
        }
    }
}
