package no.nav.toi.jobbsoker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.Hendelsestype
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource
import java.sql.PreparedStatement
import java.sql.ResultSet

data class JobbsøkerHendelse(
    val id: UUID,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: Hendelsestype,
    val opprettetAvAktørType: AktørType,
    val aktørIdentifikasjon: String?
)

data class JobbsøkerHendelseMedJobbsøkerData(
    val id: UUID,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: Hendelsestype,
    val opprettetAvAktørType: AktørType,
    val aktørIdentifikasjon: String?,
    val fødselsnummer: Fødselsnummer,
    val kandidatnummer: Kandidatnummer?,
    val fornavn: Fornavn,
    val etternavn: Etternavn
)

class JobbsøkerRepository(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) {
    private fun PreparedStatement.execBatchReturnIds(): List<Long> =
        executeBatch().let {
            generatedKeys.use { keys ->
                generateSequence { if (keys.next()) keys.getLong(1) else null }.toList()
            }
        }

    fun leggTil(jobsøkere: List<LeggTilJobbsøker>, treff: TreffId, opprettetAv: String) {
        dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                val treffId = c.treffDbId(treff)
                val jsIds = c.batchInsertJobbsøkere(treffId, jobsøkere)
                c.batchInsertHendelser(Hendelsestype.OPPRETT,jsIds, opprettetAv)
                c.commit()
            } catch (e: Exception) { c.rollback(); throw e }
        }
    }

    private fun Connection.batchInsertJobbsøkere(
        treffDbId: Long,
        data: List<LeggTilJobbsøker>,
        size: Int = 500
    ): List<Long> {
        val sql = """
            insert into jobbsoker
              (treff_db_id,fodselsnummer,kandidatnummer,fornavn,etternavn,
               navkontor,veileder_navn,veileder_navident)
            values (?,?,?,?,?,?,?,?)
        """.trimIndent()
        val ids = mutableListOf<Long>()
        prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            var n = 0
            data.forEach {
                stmt.setLong  (1, treffDbId)
                stmt.setString(2, it.fødselsnummer.asString)
                stmt.setString(3, it.kandidatnummer?.asString)
                stmt.setString(4, it.fornavn.asString)
                stmt.setString(5, it.etternavn.asString)
                stmt.setString(6, it.navkontor?.asString)
                stmt.setString(7, it.veilederNavn?.asString)
                stmt.setString(8, it.veilederNavIdent?.asString)
                stmt.addBatch(); if (++n == size) { ids += stmt.execBatchReturnIds(); n = 0 }
            }
            if (n > 0) ids += stmt.execBatchReturnIds()
        }
        return ids
    }

    private fun Connection.batchInsertHendelser(
        hendelsestype: Hendelsestype,
        jobbsøkerIds: List<Long>,
        opprettetAv: String,
        size: Int = 500
    ) {
        val sql = """
            insert into jobbsoker_hendelse
              (id,jobbsoker_db_id,tidspunkt,hendelsestype,opprettet_av_aktortype,aktøridentifikasjon)
            values (?,?,?,?,?,?)
        """.trimIndent()
        prepareStatement(sql).use { stmt ->
            var n = 0
            jobbsøkerIds.forEach { id ->
                stmt.setObject  (1, UUID.randomUUID())
                stmt.setLong    (2, id)
                stmt.setTimestamp(3, Timestamp.from(Instant.now()))
                stmt.setString  (4, hendelsestype.name)
                stmt.setString  (5, AktørType.ARRANGØR.name)
                stmt.setString  (6, opprettetAv)
                stmt.addBatch(); if (++n == size) { stmt.executeBatch(); n = 0 }
            }
            if (n > 0) stmt.executeBatch()
        }
    }

    private fun Connection.treffDbId(treff: TreffId): Long =
        prepareStatement("SELECT db_id FROM rekrutteringstreff WHERE id = ?")
            .apply { setObject(1, treff.somUuid) }
            .executeQuery().let {
                if (it.next()) it.getLong(1)
                else error("Treff ${treff.somUuid} finnes ikke")
            }

    fun hentJobbsøkere(treff: TreffId): List<Jobbsøker> =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                    SELECT 
                        js.db_id,
                        js.fodselsnummer,
                        js.kandidatnummer,
                        js.fornavn,
                        js.etternavn,
                        js.navkontor,
                        js.veileder_navn,
                        js.veileder_navident,
                        rt.id as treff_id,
                        COALESCE(
                            json_agg(
                                json_build_object(
                                    'id', jh.id,
                                    'tidspunkt', to_char(jh.tidspunkt, 'YYYY-MM-DD"T"HH24:MI:SSOF'),
                                    'hendelsestype', jh.hendelsestype,
                                    'opprettetAvAktortype', jh.opprettet_av_aktortype,
                                    'aktøridentifikasjon', jh.aktøridentifikasjon
                                )
                            ) FILTER (WHERE jh.id IS NOT NULL),
                            '[]'
                        ) AS hendelser
                    FROM jobbsoker js
                    JOIN rekrutteringstreff rt ON js.treff_db_id = rt.db_id
                    LEFT JOIN jobbsoker_hendelse jh ON js.db_id = jh.jobbsoker_db_id
                    WHERE rt.id = ?
                    GROUP BY js.db_id, js.fodselsnummer, js.kandidatnummer, js.fornavn, js.etternavn, 
                             js.navkontor, js.veileder_navn, js.veileder_navident, rt.id
                    ORDER BY js.db_id;
                """
            ).use { ps ->
                ps.setObject(1, treff.somUuid)
                ps.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs.toJobbsøker() else null }.toList()
                }
            }
        }

    fun inviter(fødselsnumre: List<Fødselsnummer>, treff: TreffId, opprettetAv: String) {
        dataSource.connection.use { c ->
            try {
                val treffDbId = c.treffDbId(treff)
                val jobbsøkerDbIds = c.hentJobbsøkerDbIder(treffDbId, fødselsnumre)
                c.batchInsertHendelser(Hendelsestype.INVITER,jobbsøkerDbIds, opprettetAv)
            } catch (e: Exception) {
                throw e
            }
        }
    }


    private fun Connection.hentJobbsøkerDbIder(treffDbId: Long, fødselsnumre: List<Fødselsnummer>): List<Long> {
        val sql = "SELECT db_id FROM jobbsoker WHERE treff_db_id = ? AND fodselsnummer = ANY(?)"
        return prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setArray(2, createArrayOf("varchar", fødselsnumre.map { it.asString }.toTypedArray()))
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getLong(1) else null }.toList()
            }
        }
    }

    private fun ResultSet.toJobbsøker() = Jobbsøker(
        treffId        = TreffId(getString("treff_id")),
        fødselsnummer  = Fødselsnummer(getString("fodselsnummer")),
        kandidatnummer = getString("kandidatnummer")?.let(::Kandidatnummer),
        fornavn        = Fornavn(getString("fornavn")),
        etternavn      = Etternavn(getString("etternavn")),
        navkontor      = getString("navkontor")?.let(::Navkontor),
        veilederNavn   = getString("veileder_navn")?.let(::VeilederNavn),
        veilederNavIdent = getString("veileder_navident")?.let(::VeilederNavIdent),
        hendelser      = parseHendelser(getString("hendelser"))
    )

    fun hentJobbsøkerHendelser(treff: TreffId): List<JobbsøkerHendelseMedJobbsøkerData> {
        dataSource.connection.use { connection ->
            val sql = """
                SELECT 
                    jh.id as hendelse_id,
                    jh.tidspunkt,
                    jh.hendelsestype,
                    jh.opprettet_av_aktortype,
                    jh.aktøridentifikasjon,
                    js.fodselsnummer,
                    js.kandidatnummer,
                    js.fornavn,
                    js.etternavn
                FROM jobbsoker_hendelse jh
                JOIN jobbsoker js ON jh.jobbsoker_db_id = js.db_id
                JOIN rekrutteringstreff rt ON js.treff_db_id = rt.db_id
                WHERE rt.id = ?
                ORDER BY jh.tidspunkt DESC;
            """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<JobbsøkerHendelseMedJobbsøkerData>()
                    while (rs.next()) {
                        result.add(
                            JobbsøkerHendelseMedJobbsøkerData(
                                id = UUID.fromString(rs.getString("hendelse_id")),
                                tidspunkt = rs.getTimestamp("tidspunkt").toInstant()
                                    .atZone(java.time.ZoneId.of("Europe/Oslo")),
                                hendelsestype = Hendelsestype.valueOf(rs.getString("hendelsestype")),
                                opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                                aktørIdentifikasjon = rs.getString("aktøridentifikasjon"),
                                fødselsnummer = Fødselsnummer(rs.getString("fodselsnummer")),
                                kandidatnummer = rs.getString("kandidatnummer")?.let { Kandidatnummer(it) },
                                fornavn = Fornavn(rs.getString("fornavn")),
                                etternavn = Etternavn(rs.getString("etternavn"))
                            )
                        )
                    }
                    return result
                }
            }
        }
    }

    private fun parseHendelser(json: String): List<JobbsøkerHendelse> {
        data class HendelseJson(
            val id: String,
            val tidspunkt: String,
            val hendelsestype: String,
            val opprettetAvAktortype: String,
            val aktøridentifikasjon: String?
        )
        return objectMapper.readValue(json, object : TypeReference<List<HendelseJson>>() {}).map { h ->
            JobbsøkerHendelse(
                id = UUID.fromString(h.id),
                tidspunkt = ZonedDateTime.parse(h.tidspunkt),
                hendelsestype = Hendelsestype.valueOf(h.hendelsestype),
                opprettetAvAktørType = AktørType.valueOf(h.opprettetAvAktortype),
                aktørIdentifikasjon = h.aktøridentifikasjon
            )
        }
    }
}
