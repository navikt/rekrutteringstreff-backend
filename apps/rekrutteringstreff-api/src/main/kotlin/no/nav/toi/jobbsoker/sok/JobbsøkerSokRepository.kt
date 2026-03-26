package no.nav.toi.jobbsoker.sok

import no.nav.toi.JacksonConfig
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.sql.DataSource

class JobbsøkerSokRepository(private val dataSource: DataSource) {

    companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
    }

    fun opprettSokRad(
        connection: Connection,
        jobbsøkerId: Long,
        treffDbId: Long,
        jobbsøker: LeggTilJobbsøker,
    ) {
        val sql = """
            INSERT INTO jobbsoker_sok (
                jobbsoker_id, rekrutteringstreff_id, status, er_synlig,
                fornavn, etternavn, fylke, kommune, poststed,
                navkontor, veileder_navident, veileder_navn, innsatsgruppe
            ) VALUES (?, ?, 'LAGT_TIL', TRUE, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, treffDbId)
            stmt.setString(3, jobbsøker.fornavn.asString)
            stmt.setString(4, jobbsøker.etternavn.asString)
            stmt.setString(5, jobbsøker.fylke)
            stmt.setString(6, jobbsøker.kommune)
            stmt.setString(7, jobbsøker.poststed)
            stmt.setString(8, jobbsøker.navkontor?.asString)
            stmt.setString(9, jobbsøker.veilederNavIdent?.asString)
            stmt.setString(10, jobbsøker.veilederNavn?.asString)
            stmt.setString(11, jobbsøker.innsatsgruppe)
            stmt.executeUpdate()
        }
    }

    fun opprettSokRader(
        connection: Connection,
        jobbsøkerIder: List<Long>,
        treffDbId: Long,
        jobbsøkere: List<LeggTilJobbsøker>,
    ) {
        val sql = """
            INSERT INTO jobbsoker_sok (
                jobbsoker_id, rekrutteringstreff_id, status, er_synlig,
                fornavn, etternavn, fylke, kommune, poststed,
                navkontor, veileder_navident, veileder_navn, innsatsgruppe
            ) VALUES (?, ?, 'LAGT_TIL', TRUE, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            jobbsøkerIder.zip(jobbsøkere).forEach { (id, js) ->
                stmt.setLong(1, id)
                stmt.setLong(2, treffDbId)
                stmt.setString(3, js.fornavn.asString)
                stmt.setString(4, js.etternavn.asString)
                stmt.setString(5, js.fylke)
                stmt.setString(6, js.kommune)
                stmt.setString(7, js.poststed)
                stmt.setString(8, js.navkontor?.asString)
                stmt.setString(9, js.veilederNavIdent?.asString)
                stmt.setString(10, js.veilederNavn?.asString)
                stmt.setString(11, js.innsatsgruppe)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun oppdaterStatus(connection: Connection, personTreffId: PersonTreffId, status: JobbsøkerStatus) {
        val sql = """
            UPDATE jobbsoker_sok
            SET status = ?
            WHERE jobbsoker_id = (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, status.name)
            stmt.setObject(2, personTreffId.somUuid)
            stmt.executeUpdate()
        }
    }

    fun oppdaterStatusOgInvitertDato(connection: Connection, personTreffId: PersonTreffId) {
        val sql = """
            UPDATE jobbsoker_sok
            SET status = 'INVITERT', invitert_dato = ?
            WHERE jobbsoker_id = (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setTimestamp(1, Timestamp.from(Instant.now()))
            stmt.setObject(2, personTreffId.somUuid)
            stmt.executeUpdate()
        }
    }

    fun oppdaterStatusBatch(connection: Connection, personTreffIder: List<PersonTreffId>, status: JobbsøkerStatus) {
        val sql = """
            UPDATE jobbsoker_sok
            SET status = ?
            WHERE jobbsoker_id = (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            personTreffIder.forEach { id ->
                stmt.setString(1, status.name)
                stmt.setObject(2, id.somUuid)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun oppdaterSynlighet(fodselsnummer: String, erSynlig: Boolean) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE jobbsoker_sok
                SET er_synlig = ?
                WHERE jobbsoker_id IN (SELECT jobbsoker_id FROM jobbsoker WHERE fodselsnummer = ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setBoolean(1, erSynlig)
                stmt.setString(2, fodselsnummer)
                stmt.executeUpdate()
            }
        }
    }

    fun sok(treffId: TreffId, request: JobbsøkerSøkRequest): JobbsøkerSøkRespons {
        return dataSource.connection.use { conn ->
            val treffDbId = conn.treffDbId(treffId)
            val (where, params) = byggWhere(treffDbId, request)
            val totalt = hentTotalt(conn, where, params)
            val treff = hentTreff(conn, where, params, request.sortering, request.side, request.antallPerSide)
            val minsideHendelser = hentMinsideHendelser(conn, treff.map { it.personTreffId })
            val jobbsøkereMedHendelser = treff.map { t ->
                t.copy(minsideHendelser = minsideHendelser[t.personTreffId] ?: emptyList())
            }
            JobbsøkerSøkRespons(
                totalt = totalt,
                side = request.side,
                antallPerSide = request.antallPerSide,
                jobbsøkere = jobbsøkereMedHendelser,
            )
        }
    }

    fun søkMedFødselsnummer(treffId: TreffId, fodselsnummer: String): JobbsøkerSøkTreff? {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT js_sok.*, j.id as person_treff_id
                FROM jobbsoker_sok js_sok
                JOIN jobbsoker j ON js_sok.jobbsoker_id = j.jobbsoker_id
                JOIN rekrutteringstreff rt ON js_sok.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ? AND j.fodselsnummer = ?
                  AND js_sok.er_synlig = true AND js_sok.status != 'SLETTET'
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

    fun hentFilterverdier(treffId: TreffId): JobbsøkerFilterverdierRespons {
        return dataSource.connection.use { conn ->
            val treffDbId = conn.treffDbId(treffId)
            JobbsøkerFilterverdierRespons(
                navkontor = hentTekstFilterverdier(conn, treffDbId, "navkontor"),
                innsatsgrupper = hentTekstFilterverdier(conn, treffDbId, "innsatsgruppe"),
                steder = hentStedFilterverdier(conn, treffDbId),
            )
        }
    }

    private fun hentTotalt(conn: Connection, where: String, params: List<Any>): Long {
        val sql = "SELECT count(*) FROM jobbsoker_sok js_sok JOIN jobbsoker j ON js_sok.jobbsoker_id = j.jobbsoker_id $where"
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
        sortering: JobbsøkerSortering,
        side: Int,
        antallPerSide: Int,
    ): List<JobbsøkerSøkTreff> {
        val sql = """
            SELECT js_sok.*, j.id as person_treff_id
            FROM jobbsoker_sok js_sok
            JOIN jobbsoker j ON js_sok.jobbsoker_id = j.jobbsoker_id
            $where
            ORDER BY ${sortering.sql}
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
        val conditions = mutableListOf("js_sok.rekrutteringstreff_id = ?")
        val params = mutableListOf<Any>(treffDbId)

        conditions.add("js_sok.er_synlig = true")
        conditions.add("js_sok.status != 'SLETTET'")

        request.fritekst?.takeIf { it.isNotBlank() }?.let {
            conditions.add("js_sok.sok_tekst ILIKE ?")
            params.add("%${it.lowercase()}%")
        }

        request.status?.takeIf { it.isNotEmpty() }?.let { statuser ->
            val placeholders = statuser.indices.joinToString(",") { "?" }
            conditions.add("js_sok.status IN ($placeholders)")
            statuser.forEach { params.add(it.name) }
        }

        request.innsatsgruppe?.takeIf { it.isNotEmpty() }?.let { grupper ->
            val placeholders = grupper.indices.joinToString(",") { "?" }
            conditions.add("js_sok.innsatsgruppe IN ($placeholders)")
            grupper.forEach { params.add(it) }
        }

        request.fylke?.let {
            conditions.add("js_sok.fylke = ?")
            params.add(it)
        }

        request.kommune?.let {
            conditions.add("js_sok.kommune = ?")
            params.add(it)
        }

        request.navkontor?.let {
            conditions.add("js_sok.navkontor = ?")
            params.add(it)
        }

        request.veileder?.let {
            conditions.add("js_sok.veileder_navident = ?")
            params.add(it)
        }

        val whereClause = "WHERE " + conditions.joinToString(" AND ")
        return Pair(whereClause, params)
    }

    private fun hentTekstFilterverdier(conn: Connection, treffDbId: Long, kolonne: String): List<String> {
        val sql = """
            SELECT DISTINCT $kolonne
            FROM jobbsoker_sok
            WHERE rekrutteringstreff_id = ?
              AND er_synlig = true
              AND status != 'SLETTET'
              AND $kolonne IS NOT NULL
              AND $kolonne != ''
            ORDER BY $kolonne
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
        }
    }

    private fun hentStedFilterverdier(conn: Connection, treffDbId: Long): List<JobbsøkerFilterverdiSted> {
        val sql = """
            SELECT navn, type
            FROM (
                SELECT DISTINCT kommune AS navn, 'kommune' AS type
                FROM jobbsoker_sok
                WHERE rekrutteringstreff_id = ?
                  AND er_synlig = true
                  AND status != 'SLETTET'
                  AND kommune IS NOT NULL
                  AND kommune != ''

                UNION

                SELECT DISTINCT fylke AS navn, 'fylke' AS type
                FROM jobbsoker_sok
                WHERE rekrutteringstreff_id = ?
                  AND er_synlig = true
                  AND status != 'SLETTET'
                  AND fylke IS NOT NULL
                  AND fylke != ''
            ) steder
            ORDER BY type, navn
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
            stmt.setLong(1, treffDbId)
            stmt.setLong(2, treffDbId)
            stmt.executeQuery().use { rs ->
                generateSequence {
                    if (!rs.next()) return@generateSequence null
                    JobbsøkerFilterverdiSted(
                        navn = rs.getString("navn"),
                        type = when (rs.getString("type")) {
                            "kommune" -> Stedstype.KOMMUNE
                            "fylke" -> Stedstype.FYLKE
                            else -> error("Ukjent stedstype")
                        },
                    )
                }.toList()
            }
        }
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
        fornavn = getString("fornavn"),
        etternavn = getString("etternavn"),
        innsatsgruppe = getString("innsatsgruppe"),
        fylke = getString("fylke"),
        kommune = getString("kommune"),
        poststed = getString("poststed"),
        navkontor = getString("navkontor"),
        veilederNavn = getString("veileder_navn"),
        veilederNavident = getString("veileder_navident"),
        status = JobbsøkerStatus.valueOf(getString("status")),
        invitertDato = getTimestamp("invitert_dato")?.toInstant(),
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
