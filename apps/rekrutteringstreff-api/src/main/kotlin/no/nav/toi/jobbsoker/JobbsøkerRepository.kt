package no.nav.toi.jobbsoker

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerData
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.*
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource

class JobbsøkerRepository(private val dataSource: DataSource, private val mapper: ObjectMapper) {

    private fun PreparedStatement.execBatchReturnIds(): List<Long> =
        executeBatch().let {
            generatedKeys.use { keys ->
                generateSequence { if (keys.next()) keys.getLong(1) else null }.toList()
            }
        }

    fun leggTil(connection: Connection, jobbsøkere: List<LeggTilJobbsøker>, treff: TreffId): List<PersonTreffId> {
        val treffDbId = connection.treffDbId(treff)
        return connection.batchInsertJobbsøkere(treffDbId, jobbsøkere)
    }

    fun leggTilOpprettetHendelser(
        connection: Connection,
        personTreffIder: List<PersonTreffId>,
        opprettetAv: String
    ) {
        connection.batchInsertHendelser(JobbsøkerHendelsestype.OPPRETTET, personTreffIder, opprettetAv)
    }

    private fun Connection.batchInsertJobbsøkere(
        treffDbId: Long,
        data: List<LeggTilJobbsøker>,
        size: Int = 500
    ): List<PersonTreffId> {
        val sql = """
            insert into jobbsoker
              (id, rekrutteringstreff_id,fodselsnummer,fornavn,etternavn,
               navkontor,veileder_navn,veileder_navident,status)
            values (?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        val personTreffIder = mutableListOf<PersonTreffId>()
        prepareStatement(sql).use { stmt ->
            var n = 0
            data.forEach {
                val personTreffId = PersonTreffId(UUID.randomUUID())
                personTreffIder += personTreffId
                stmt.setObject(1, personTreffId.somUuid)
                stmt.setLong(2, treffDbId)
                stmt.setString(3, it.fødselsnummer.asString)
                stmt.setString(4, it.fornavn.asString)
                stmt.setString(5, it.etternavn.asString)
                stmt.setString(6, it.navkontor?.asString)
                stmt.setString(7, it.veilederNavn?.asString)
                stmt.setString(8, it.veilederNavIdent?.asString)
                stmt.setString(9, JobbsøkerStatus.LAGT_TIL.name)
                stmt.addBatch(); if (++n == size) {
                stmt.executeBatch(); n = 0
            }
            }
            if (n > 0) stmt.executeBatch()
        }
        return personTreffIder
    }

    fun leggTilHendelserForJobbsøkere(
        c: Connection,
        hendelsestype: JobbsøkerHendelsestype,
        personTreffIds: List<PersonTreffId>,
        opprettetAv: String,
        arrangørtype: AktørType = AktørType.ARRANGØR,
        hendelseData: String? = null,
        size: Int = 500
    ) {
        val sql = """
            INSERT INTO jobbsoker_hendelse
              (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon, hendelse_data)
            VALUES (?, (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?), ?, ?, ?, ?, ?::jsonb)
        """.trimIndent()
        c.prepareStatement(sql).use { stmt ->
            var n = 0
            personTreffIds.forEach { id ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, id.somUuid)
                stmt.setTimestamp(3, Timestamp.from(Instant.now()))
                stmt.setString(4, hendelsestype.name)
                stmt.setString(5, arrangørtype.name)
                stmt.setString(6, opprettetAv)
                stmt.setString(7, hendelseData)
                stmt.addBatch()
                if (++n == size) {
                    stmt.executeBatch()
                    n = 0
                }
            }
            if (n > 0) stmt.executeBatch()
        }
    }

    fun leggTilHendelse(
        c: Connection,
        personTreffId: PersonTreffId,
        hendelsestype: JobbsøkerHendelsestype,
        aktørType: AktørType,
        opprettetAv: String,
        hendelseData: String? = null
    ) {
        val sql = """
            INSERT INTO jobbsoker_hendelse
              (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon, hendelse_data)
            VALUES (?, (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?), ?, ?, ?, ?, ?::jsonb)
        """.trimIndent()
        c.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, personTreffId.somUuid)
            stmt.setTimestamp(3, Timestamp.from(Instant.now()))
            stmt.setString(4, hendelsestype.name)
            stmt.setString(5, aktørType.name)
            stmt.setString(6, opprettetAv)
            stmt.setString(7, hendelseData)
            stmt.executeUpdate()
        }
    }

    private fun Connection.batchInsertHendelser(
        hendelsestype: JobbsøkerHendelsestype,
        personTreffIds: List<PersonTreffId>,
        opprettetAv: String,
        arrangørtype: AktørType = AktørType.ARRANGØR,
        size: Int = 500
    ) {
        val sql = """
            insert into jobbsoker_hendelse
              (id,jobbsoker_id,tidspunkt,hendelsestype,opprettet_av_aktortype,aktøridentifikasjon)
            values (?,(select jobbsoker_id from jobbsoker where id = ?),?,?,?,?)
        """.trimIndent()
        prepareStatement(sql).use { stmt ->
            var n = 0
            personTreffIds.forEach { id ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, id.somUuid)
                stmt.setTimestamp(3, Timestamp.from(Instant.now()))
                stmt.setString(4, hendelsestype.name)
                stmt.setString(5, arrangørtype.name)
                stmt.setString(6, opprettetAv)
                stmt.addBatch(); if (++n == size) {
                stmt.executeBatch(); n = 0
            }
            }
            if (n > 0) stmt.executeBatch()
        }
    }

    private fun Connection.treffDbId(treff: TreffId): Long =
        prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?")
            .apply { setObject(1, treff.somUuid) }
            .executeQuery().let {
                if (it.next()) it.getLong(1)
                else error("Treff ${treff.somUuid} finnes ikke")
            }

    fun hentJobbsøkere(treff: TreffId): List<Jobbsøker> =
        dataSource.connection.use { c -> hentJobbsøkere(c, treff) }

    fun hentJobbsøkere(connection: Connection, treff: TreffId): List<Jobbsøker> {
        val sql = """
            SELECT
                js.id,
                js.jobbsoker_id,
                js.fodselsnummer,
                js.fornavn,
                js.etternavn,
                js.navkontor,
                js.veileder_navn,
                js.veileder_navident,
                js.status,
                rt.id as treff_id,
                COALESCE(
                    json_agg(
                        json_build_object(
                            'id', jh.id,
                            'tidspunkt', to_char(jh.tidspunkt, 'YYYY-MM-DD"T"HH24:MI:SS.MSOF'),
                            'hendelsestype', jh.hendelsestype,
                            'opprettetAvAktortype', jh.opprettet_av_aktortype,
                            'aktøridentifikasjon', jh.aktøridentifikasjon,
                            'hendelseData', jh.hendelse_data
                        ) ORDER BY jh.tidspunkt DESC, jh.jobbsoker_hendelse_id DESC
                    ) FILTER (WHERE jh.id IS NOT NULL),
                    '[]'
                ) AS hendelser
            FROM jobbsoker js
            JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
            LEFT JOIN jobbsoker_hendelse jh ON js.jobbsoker_id = jh.jobbsoker_id
            WHERE rt.id = ? AND js.status != 'SLETTET' AND js.er_synlig = TRUE
            GROUP BY js.id, js.jobbsoker_id, js.fodselsnummer, js.fornavn, js.etternavn,
                     js.navkontor, js.veileder_navn, js.veileder_navident, rt.id
            ORDER BY js.jobbsoker_id;
        """.trimIndent()

        return connection.prepareStatement(sql).use { ps ->
            ps.setObject(1, treff.somUuid)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.toJobbsøker() else null }.toList()
            }
        }
    }

    fun hentAntallJobbsøkere(treff: TreffId): Int =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                    SELECT
                        COUNT(1) AS antall_jobbsøkere
                    FROM jobbsoker js
                    JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
                    WHERE rt.id = ? AND js.status != 'SLETTET' AND js.er_synlig = TRUE
                """
            ).use { ps ->
                ps.setObject(1, treff.somUuid)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt("antall_jobbsøkere") else 0
                }
            }
        }

    fun hentPersonTreffId(treffId: TreffId, fødselsnummer: Fødselsnummer): PersonTreffId? =
        dataSource.connection.use { c ->
            hentPersonTreffId(c, treffId, fødselsnummer)
        }

    fun hentPersonTreffId(connection: Connection, treffId: TreffId, fødselsnummer: Fødselsnummer): PersonTreffId? =
        connection.hentPersonTreffIderFraFødselsnummer(treffId, listOf(fødselsnummer)).firstOrNull()

    private fun Connection.hentPersonTreffIderFraFødselsnummer(
        treffId: TreffId,
        fødselsnumre: List<Fødselsnummer>
    ): List<PersonTreffId> {

        val sql = "SELECT j.id " +
                "FROM jobbsoker j  " +
                    "JOIN rekrutteringstreff rt ON j.rekrutteringstreff_id = rt.rekrutteringstreff_id " +
                "WHERE rt.id = ? AND j.fodselsnummer = ANY(?)"
        return prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setArray(2, createArrayOf("varchar", fødselsnumre.map { it.asString }.toTypedArray()))
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) PersonTreffId(UUID.fromString(rs.getString("id"))) else null }.toList()
            }
        }
    }

    fun hentFødselsnummer(personTreffId: PersonTreffId): Fødselsnummer? =
        dataSource.connection.use { c ->
            c.prepareStatement("SELECT fodselsnummer FROM jobbsoker WHERE id = ?").use { ps ->
                ps.setObject(1, personTreffId.somUuid)
                ps.executeQuery().use { rs ->
                    if (rs.next()) Fødselsnummer(rs.getString("fodselsnummer")) else null
                }
            }
        }

    private fun parseHendelser(json: String): List<JobbsøkerHendelse> {
        val hendelserRaw = mapper.readTree(json)
        return hendelserRaw.map { h ->
            JobbsøkerHendelse(
                id = UUID.fromString(h["id"].asText()),
                tidspunkt = ZonedDateTime.parse(h["tidspunkt"].asText()),
                hendelsestype = JobbsøkerHendelsestype.valueOf(h["hendelsestype"].asText()),
                opprettetAvAktørType = AktørType.valueOf(h["opprettetAvAktortype"].asText()),
                aktørIdentifikasjon = h["aktøridentifikasjon"]?.takeIf { !it.isNull }?.asText(),
                hendelseData = h["hendelseData"]?.takeIf { !it.isNull }
            )
        }
    }

    private fun ResultSet.toJobbsøker() = Jobbsøker(
        personTreffId = PersonTreffId(UUID.fromString(getString("id"))),
        treffId = TreffId(getString("treff_id")),
        fødselsnummer = Fødselsnummer(getString("fodselsnummer")),
        fornavn = Fornavn(getString("fornavn")),
        etternavn = Etternavn(getString("etternavn")),
        navkontor = getString("navkontor")?.let(::Navkontor),
        veilederNavn = getString("veileder_navn")?.let(::VeilederNavn),
        veilederNavIdent = getString("veileder_navident")?.let(::VeilederNavIdent),
        status = JobbsøkerStatus.valueOf(getString("status")),
        hendelser = parseHendelser(getString("hendelser"))
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
                    jh.hendelse_data,
                    js.fodselsnummer,
                    js.fornavn,
                    js.etternavn,
                    js.id as person_treff_id
                FROM jobbsoker_hendelse jh
                JOIN jobbsoker js ON jh.jobbsoker_id = js.jobbsoker_id
                JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ? AND js.er_synlig = TRUE
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
                                hendelsestype = JobbsøkerHendelsestype.valueOf(rs.getString("hendelsestype")),
                                opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                                aktørIdentifikasjon = rs.getString("aktøridentifikasjon"),
                                fødselsnummer = Fødselsnummer(rs.getString("fodselsnummer")),
                                fornavn = Fornavn(rs.getString("fornavn")),
                                etternavn = Etternavn(rs.getString("etternavn")),
                                personTreffId = PersonTreffId(
                                    UUID.fromString(rs.getString("person_treff_id"))
                                ),
                                hendelseData = rs.getString("hendelse_data")?.let {
                                    mapper.readTree(it)
                                }
                            )
                        )
                    }
                    return result
                }
            }
        }
    }

    fun hentJobbsøker(treff: TreffId, fødselsnummer: Fødselsnummer): Jobbsøker? =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                SELECT
                    js.id,
                    js.jobbsoker_id,
                    js.fodselsnummer,
                    js.fornavn,
                    js.etternavn,
                    js.navkontor,
                    js.veileder_navn,
                    js.veileder_navident,
                    js.status,
                    rt.id as treff_id,
                    COALESCE(
                        json_agg(
                            json_build_object(
                                'id', jh.id,
                                'tidspunkt', to_char(jh.tidspunkt, 'YYYY-MM-DD"T"HH24:MI:SS.MSOF'),
                                'hendelsestype', jh.hendelsestype,
                                'opprettetAvAktortype', jh.opprettet_av_aktortype,
                                'aktøridentifikasjon', jh.aktøridentifikasjon,
                                'hendelseData', jh.hendelse_data
                            ) ORDER BY jh.tidspunkt DESC, jh.jobbsoker_hendelse_id DESC
                        ) FILTER (WHERE jh.id IS NOT NULL),
                        '[]'
                    ) AS hendelser
                FROM jobbsoker js
                JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
                LEFT JOIN jobbsoker_hendelse jh ON js.jobbsoker_id = jh.jobbsoker_id
                WHERE rt.id = ? AND js.fodselsnummer = ? AND js.status != 'SLETTET' AND js.er_synlig = TRUE
                GROUP BY js.id, js.jobbsoker_id, js.fodselsnummer, js.fornavn, js.etternavn,
                         js.navkontor, js.veileder_navn, js.veileder_navident, rt.id
            """
            ).use { ps ->
                ps.setObject(1, treff.somUuid)
                ps.setString(2, fødselsnummer.asString)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.toJobbsøker() else null
                }
            }
        }


    fun endreStatus(connection: Connection, personTreffId: PersonTreffId, jobbsøkerStatus: JobbsøkerStatus) {
        connection.prepareStatement(
            """
            UPDATE jobbsoker
            SET status=?
            WHERE id=?
            """
        ).apply {
            var i = 0
            setString(++i, jobbsøkerStatus.name)
            setObject(++i, personTreffId.somUuid)
        }.executeUpdate()
    }

    fun oppdaterSynlighet(
        fodselsnummer: String,
        erSynlig: Boolean,
        meldingTidspunkt: Instant
    ): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE jobbsoker
            SET er_synlig = ?,
                synlighet_sist_oppdatert = ?
            WHERE fodselsnummer = ?
              AND (synlighet_sist_oppdatert IS NULL OR synlighet_sist_oppdatert < ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setBoolean(1, erSynlig)
            stmt.setTimestamp(2, Timestamp.from(meldingTidspunkt))
            stmt.setString(3, fodselsnummer)
            stmt.setTimestamp(4, Timestamp.from(meldingTidspunkt))
            stmt.executeUpdate()
        }
    }

    /*
    Trenger en ekstra metode som aldri overskriver data, som kun setter synlighet hvis den ikke er oppdatert fra før.
     */
    fun oppdaterSynlighetHvisIkkeSatt(
        fodselsnummer: String,
        erSynlig: Boolean,
        meldingTidspunkt: Instant
    ): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE jobbsoker
            SET er_synlig = ?,
                synlighet_sist_oppdatert = ?
            WHERE fodselsnummer = ?
              AND synlighet_sist_oppdatert IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setBoolean(1, erSynlig)
            stmt.setTimestamp(2, Timestamp.from(meldingTidspunkt))
            stmt.setString(3, fodselsnummer)
            stmt.executeUpdate()
        }
    }
}
