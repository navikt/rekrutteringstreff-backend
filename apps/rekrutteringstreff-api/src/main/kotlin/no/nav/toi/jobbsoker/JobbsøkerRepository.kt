package no.nav.toi.jobbsoker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerData
import no.nav.toi.log
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

    fun leggTil(jobsøkere: List<LeggTilJobbsøker>, treff: TreffId, opprettetAv: String) {
        dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                val treffId = c.treffDbId(treff)
                val jsIds = c.batchInsertJobbsøkere(treffId, jobsøkere)
                c.batchInsertHendelser(JobbsøkerHendelsestype.OPPRETTET, jsIds, opprettetAv)
                c.commit()
            } catch (e: Exception) {
                c.rollback(); throw e
            } finally {
                c.autoCommit = true
            }
        }
    }

    private fun Connection.batchInsertJobbsøkere(
        treffDbId: Long,
        data: List<LeggTilJobbsøker>,
        size: Int = 500
    ): List<Long> {
        val sql = """
            insert into jobbsoker
              (id, rekrutteringstreff_id,fodselsnummer,kandidatnummer,fornavn,etternavn,
               navkontor,veileder_navn,veileder_navident)
            values (?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        val ids = mutableListOf<Long>()
        prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            var n = 0
            data.forEach {
                stmt.setObject(1, UUID.randomUUID())
                stmt.setLong(2, treffDbId)
                stmt.setString(3, it.fødselsnummer.asString)
                stmt.setString(4, it.kandidatnummer?.asString)
                stmt.setString(5, it.fornavn.asString)
                stmt.setString(6, it.etternavn.asString)
                stmt.setString(7, it.navkontor?.asString)
                stmt.setString(8, it.veilederNavn?.asString)
                stmt.setString(9, it.veilederNavIdent?.asString)
                stmt.addBatch(); if (++n == size) {
                ids += stmt.execBatchReturnIds(); n = 0
            }
            }
            if (n > 0) ids += stmt.execBatchReturnIds()
        }
        return ids
    }

    @Deprecated("Bruk heller PersonTreffId enn db_id")
    private fun Connection.batchInsertHendelser(
        hendelsestype: JobbsøkerHendelsestype,
        jobbsøkerIds: List<Long>,
        opprettetAv: String,
        arrangørtype: AktørType = AktørType.ARRANGØR,
        size: Int = 500
    ) {
        val sql = """
            insert into jobbsoker_hendelse
              (id,jobbsoker_id,tidspunkt,hendelsestype,opprettet_av_aktortype,aktøridentifikasjon)
            values (?,?,?,?,?,?)
        """.trimIndent()
        prepareStatement(sql).use { stmt ->
            var n = 0
            jobbsøkerIds.forEach { id ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setLong(2, id)
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

    fun leggTilHendelserForJobbsøkere(
        c: Connection,
        hendelsestype: JobbsøkerHendelsestype,
        personTreffIds: List<PersonTreffId>,
        opprettetAv: String,
        arrangørtype: AktørType = AktørType.ARRANGØR,
        size: Int = 500
    ) {
        val sql = """
            INSERT INTO jobbsoker_hendelse
              (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
            VALUES (?, (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?), ?, ?, ?, ?)
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
                stmt.addBatch()
                if (++n == size) {
                    stmt.executeBatch()
                    n = 0
                }
            }
            if (n > 0) stmt.executeBatch()
        }
    }

    private fun Connection.batchInsertHendelserFraPersonTreffIder(
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
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                    SELECT
                        js.id,
                        js.jobbsoker_id,
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
                                ) ORDER BY jh.tidspunkt
                            ) FILTER (WHERE jh.id IS NOT NULL),
                            '[]'
                        ) AS hendelser
                    FROM jobbsoker js
                    JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
                    LEFT JOIN jobbsoker_hendelse jh ON js.jobbsoker_id = jh.jobbsoker_id
                    WHERE rt.id = ?
                    GROUP BY js.id, js.jobbsoker_id, js.fodselsnummer, js.kandidatnummer, js.fornavn, js.etternavn,
                             js.navkontor, js.veileder_navn, js.veileder_navident, rt.id
                    ORDER BY js.jobbsoker_id;
                """
            ).use { ps ->
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
                    WHERE rt.id = ?
                """
            ).use { ps ->
                ps.setObject(1, treff.somUuid)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt("antall_jobbsøkere") else 0
                }
            }
        }

    fun inviter(personTreffIder: List<PersonTreffId>, treff: TreffId, opprettetAv: String) {
        dataSource.connection.use { c ->
            try {
                c.batchInsertHendelserFraPersonTreffIder(JobbsøkerHendelsestype.INVITERT, personTreffIder, opprettetAv)
            } catch (e: Exception) {
                throw e
            }
        }
    }


    fun svarJaTilInvitasjon(fødselsnummer: Fødselsnummer, treff: TreffId, opprettetAv: String) {
        dataSource.connection.use { c ->
            try {
                val treffDbId = c.treffDbId(treff)
                val jobbsøkerDbId =
                    c.hentJobbsøkerDbIderFraFødselsnummer(treffDbId = treffDbId, fødselsnumre = listOf(fødselsnummer))
                        .firstOrNull()
                        ?: throw IllegalStateException("Jobbsøker finnes ikke for dette treffet.")
                c.batchInsertHendelser(
                    JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
                    listOf(jobbsøkerDbId),
                    opprettetAv,
                    AktørType.JOBBSØKER
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun svarNeiTilInvitasjon(fødselsnummer: Fødselsnummer, treff: TreffId, opprettetAv: String) {
        dataSource.connection.use { c ->
            try {
                val treffDbId = c.treffDbId(treff)
                val jobbsøkerDbId =
                    c.hentJobbsøkerDbIderFraFødselsnummer(treffDbId, listOf(fødselsnummer)).firstOrNull()
                        ?: throw IllegalStateException("Jobbsøker finnes ikke for dette treffet.")
                c.batchInsertHendelser(
                    JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON,
                    listOf(jobbsøkerDbId),
                    opprettetAv,
                    AktørType.JOBBSØKER
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }


    private fun Connection.hentJobbsøkerDbIderFraFødselsnummer(
        treffDbId: Long,
        fødselsnumre: List<Fødselsnummer>
    ): List<Long> {
        val sql = "SELECT jobbsoker_id FROM jobbsoker WHERE rekrutteringstreff_id = ? AND fodselsnummer = ANY(?)"
        return prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setArray(2, createArrayOf("varchar", fødselsnumre.map { it.asString }.toTypedArray()))
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getLong(1) else null }.toList()
            }
        }
    }

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

    private fun Connection.hentJobbsøkerDbIder(treffDbId: Long, personTreffIder: List<PersonTreffId>): List<Long> {
        val sql = "SELECT jobbsoker_id FROM jobbsoker WHERE rekrutteringstreff_id = ? AND id = ANY(?)"
        return prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setArray(2, createArrayOf("uuid", personTreffIder.map { it.somString }.toTypedArray()))
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getLong(1) else null }.toList()
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
        val typeRef = object : TypeReference<List<Map<String, String>>>() {}
        val hendelserRaw: List<Map<String, String>> = mapper.readValue(json, typeRef)
        return hendelserRaw.map { h ->
            JobbsøkerHendelse(
                id = UUID.fromString(h["id"]),
                tidspunkt = ZonedDateTime.parse(h["tidspunkt"]),
                hendelsestype = JobbsøkerHendelsestype.valueOf(h["hendelsestype"]!!),
                opprettetAvAktørType = AktørType.valueOf(h["opprettetAvAktortype"]!!),
                aktørIdentifikasjon = h["aktøridentifikasjon"]
            )
        }
    }

    private fun ResultSet.toJobbsøker() = Jobbsøker(
        personTreffId = PersonTreffId(UUID.fromString(getString("id"))),
        treffId = TreffId(getString("treff_id")),
        fødselsnummer = Fødselsnummer(getString("fodselsnummer")),
        kandidatnummer = getString("kandidatnummer")?.let(::Kandidatnummer),
        fornavn = Fornavn(getString("fornavn")),
        etternavn = Etternavn(getString("etternavn")),
        navkontor = getString("navkontor")?.let(::Navkontor),
        veilederNavn = getString("veileder_navn")?.let(::VeilederNavn),
        veilederNavIdent = getString("veileder_navident")?.let(::VeilederNavIdent),
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
                    js.fodselsnummer,
                    js.kandidatnummer,
                    js.fornavn,
                    js.etternavn,
                    js.id as person_treff_id
                FROM jobbsoker_hendelse jh
                JOIN jobbsoker js ON jh.jobbsoker_id = js.jobbsoker_id
                JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
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
                                hendelsestype = JobbsøkerHendelsestype.valueOf(rs.getString("hendelsestype")),
                                opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                                aktørIdentifikasjon = rs.getString("aktøridentifikasjon"),
                                fødselsnummer = Fødselsnummer(rs.getString("fodselsnummer")),
                                kandidatnummer = rs.getString("kandidatnummer")?.let { Kandidatnummer(it) },
                                fornavn = Fornavn(rs.getString("fornavn")),
                                etternavn = Etternavn(rs.getString("etternavn")),
                                personTreffId = PersonTreffId(
                                    UUID.fromString(rs.getString("person_treff_id"))
                                )
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
                JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
                LEFT JOIN jobbsoker_hendelse jh ON js.jobbsoker_id = jh.jobbsoker_id
                WHERE rt.id = ? AND js.fodselsnummer = ?
                GROUP BY js.id, js.jobbsoker_id, js.fodselsnummer, js.kandidatnummer, js.fornavn, js.etternavn,
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

    fun registrerAktivitetskortOpprettelseFeilet(fødselsnummer: Fødselsnummer, treff: TreffId, endretAv: String) {
        dataSource.connection.use { c ->
            try {
                log.info("Skal oppdatere hendelse for aktiviteskortfeil for Treffid: ${treff}")
                secure(log).error("Henter jobbsøker persontreffid for treff: ${treff.somString} og fødselsnummer: ${fødselsnummer.asString}")
                val personTreffIds =
                    c.hentPersonTreffIderFraFødselsnummer(treffId = treff, fødselsnumre = listOf(fødselsnummer))
                        .firstOrNull()

                if(personTreffIds == null) {
                    log.error("Fant ingen jobbsøker med treffId: ${treff.somString} og fødselsnummer: (se securelog)")
                    secure(log).error("Fant ingen jobbsøker med treffId: ${treff.somString} og fødselsnummer: ${fødselsnummer.asString}")
                    return
                }

                log.info("Skal oppdatere feil fra aktivitetsplanen for  jobbsøkerdbId: $personTreffIds")
                c.batchInsertHendelserFraPersonTreffIder(
                    JobbsøkerHendelsestype.AKTIVITETSKORT_OPPRETTELSE_FEIL,
                    listOf(personTreffIds),
                    endretAv,
                    AktørType.ARRANGØR
                )
                log.info("Registrerte hendelse om at opprettelse av aktivitetskort feilet for rekrutteringstreffId: ${treff.somString}")
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun hentJobbsøkereMedAktivtSvarJa(connection: Connection, treff: TreffId): List<PersonTreffId> {
        val sql = """
            SELECT DISTINCT js.id
            FROM jobbsoker js
            JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
            JOIN jobbsoker_hendelse jh ON js.jobbsoker_id = jh.jobbsoker_id
            WHERE rt.id = ?
              AND jh.hendelsestype = ?
              AND NOT EXISTS (
                  SELECT 1 FROM jobbsoker_hendelse jh2
                  WHERE jh2.jobbsoker_id = js.jobbsoker_id
                    AND jh2.hendelsestype = ?
                    AND jh2.tidspunkt > jh.tidspunkt
              )
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.setString(2, JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON.name)
            stmt.setString(3, JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON.name)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<PersonTreffId>()
                while (rs.next()) {
                    result.add(PersonTreffId(UUID.fromString(rs.getString("id"))))
                }
                return result
            }
        }
    }
}