package no.nav.toi.jobbsoker.sok

import no.nav.toi.JacksonConfig
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource

class JobbsøkerSokRepository(private val dataSource: DataSource) {

    companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
    }

    fun sok(treffId: TreffId, request: JobbsøkerSøkRequest): JobbsøkerSøkRespons {
        return dataSource.executeInTransaction { conn ->
            val treffDbId = conn.treffDbId(treffId)
            val (where, params) = byggWhere(treffDbId, request)
            val totalt = hentTotalt(conn, where, params)
            val gyldigSide = beregnGyldigSide(request.side, request.antallPerSide, totalt)
            val tellinger = hentTellinger(conn, treffDbId)
            val treff = if (totalt == 0L) {
                emptyList()
            } else {
                hentTreff(
                    conn,
                    where,
                    params,
                    request.sorteringsfelt,
                    request.sorteringsretning,
                    gyldigSide,
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
                side = gyldigSide,
                antallPerSide = request.antallPerSide,
                jobbsøkere = jobbsøkereMedHendelser,
            )
        }
    }

    private fun beregnGyldigSide(ønsketSide: Int, antallPerSide: Int, totalt: Long): Int {
        if (totalt <= 0L) return 1

        val sisteSide = ((totalt - 1) / antallPerSide) + 1
        return ønsketSide.coerceIn(1, sisteSide.toInt())
    }

    fun søkMedFødselsnummer(treffId: TreffId, fodselsnummer: String): JobbsøkerSøkTreff? {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT j.id::text as person_treff_id, j.fodselsnummer,
                       j.fornavn, j.etternavn, j.navkontor,
                       j.veileder_navn, j.veileder_navident,
                       j.status, j.lagt_til_dato, j.lagt_til_av
                FROM jobbsoker j
                JOIN rekrutteringstreff rt ON j.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ? AND j.fodselsnummer = ?
                  AND j.er_synlig = true AND j.status != 'SLETTET'
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.setString(2, fodselsnummer)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.tilTreff() else null
                }
            }
        }
    }

    private fun hentTellinger(conn: Connection, treffDbId: Long): Pair<Int, Int> {
        val sql = """
            SELECT
                COUNT(*) FILTER (WHERE status != 'SLETTET' AND er_synlig = FALSE) AS antall_skjulte,
                COUNT(*) FILTER (WHERE status = 'SLETTET') AS antall_slettede
            FROM jobbsoker
            WHERE rekrutteringstreff_id = ?
        """.trimIndent()
        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) Pair(rs.getInt("antall_skjulte"), rs.getInt("antall_slettede"))
                else Pair(0, 0)
            }
        }
    }

    private fun hentTotalt(conn: Connection, where: String, params: List<Any>): Long {
        val sql = "SELECT count(*) FROM jobbsoker j $where"
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
            SELECT j.id::text as person_treff_id, j.fodselsnummer,
                   j.fornavn, j.etternavn, j.navkontor,
                   j.veileder_navn, j.veileder_navident,
                   j.status, j.lagt_til_dato, j.lagt_til_av
            FROM jobbsoker j
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

    private fun byggWhere(treffDbId: Long, request: JobbsøkerSøkRequest): Pair<String, List<Any>> {
        val conditions = mutableListOf("j.rekrutteringstreff_id = ?")
        val params = mutableListOf<Any>(treffDbId)

        conditions.add("j.er_synlig = true")
        conditions.add("j.status != 'SLETTET'")

        request.fritekst?.takeIf { it.isNotBlank() }?.let {
            if (it.trim().matches(Regex("\\d{11}"))) {
                conditions.add("j.fodselsnummer = ?")
                params.add(it.trim())
            } else {
                val escaped = it.lowercase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_")
                conditions.add("(LOWER(j.fornavn) LIKE ? ESCAPE '\\' OR LOWER(j.etternavn) LIKE ? ESCAPE '\\')")
                params.add("${escaped}%")
                params.add("${escaped}%")
            }
        }

        request.status?.takeIf { it.isNotEmpty() }?.let { statuser ->
            val placeholders = statuser.indices.joinToString(",") { "?" }
            conditions.add("j.status IN ($placeholders)")
            statuser.forEach { params.add(it.name) }
        }

        val whereClause = "WHERE " + conditions.joinToString(" AND ")
        return Pair(whereClause, params)
    }

    private fun settParam(stmt: java.sql.PreparedStatement, index: Int, value: Any) {
        when (value) {
            is Long -> stmt.setLong(index, value)
            is String -> stmt.setString(index, value)
            is Int -> stmt.setInt(index, value)
            is Boolean -> stmt.setBoolean(index, value)
            else -> stmt.setObject(index, value)
        }
    }

    private fun Connection.treffDbId(treff: TreffId): Long =
        prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?")
            .apply { setObject(1, treff.somUuid) }
            .executeQuery().let {
                if (it.next()) it.getLong(1)
                else error("Treff ${treff.somUuid} finnes ikke")
            }

    private fun ResultSet.tilTreff() = JobbsøkerSøkTreff(
        personTreffId = getString("person_treff_id"),
        fodselsnummer = getString("fodselsnummer"),
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
