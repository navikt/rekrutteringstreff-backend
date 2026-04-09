package no.nav.toi.jobbsoker

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerData
import no.nav.toi.jobbsoker.dto.parseHendelseData
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

    data class OpprettetJobbsøker(val personTreffId: PersonTreffId, val jobbsøkerId: Long)

    private data class JobbsøkerBatchRad(
        val personTreffId: PersonTreffId,
        val jobbsøker: LeggTilJobbsøker,
    )

    fun leggTil(connection: Connection, jobbsøkere: List<LeggTilJobbsøker>, treff: TreffId, navIdent: String, tidspunkt: Instant): List<OpprettetJobbsøker> {
        val treffDbId = connection.treffDbId(treff)
        return connection.batchInsertJobbsøkere(treffDbId, jobbsøkere)
    }

    fun leggTilOpprettetHendelser(
        connection: Connection,
        personTreffIder: List<PersonTreffId>,
        opprettetAv: String,
        tidspunkt: Instant,
    ) {
        leggTilHendelserForJobbsøkere(connection, JobbsøkerHendelsestype.OPPRETTET, personTreffIder, opprettetAv, tidspunkt = tidspunkt)
    }

    private fun Connection.batchInsertJobbsøkere(
        treffDbId: Long,
        jobbsøkere: List<LeggTilJobbsøker>,
        maksStørrelsePerBatch: Int = 500
    ): List<OpprettetJobbsøker> {
        val sql = """
            insert into jobbsoker
              (id, rekrutteringstreff_id,fodselsnummer,fornavn,etternavn,
               navkontor,veileder_navn,veileder_navident,status)
            values (?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        val batchRader = jobbsøkere.map { jobbsøker ->
            JobbsøkerBatchRad(
                personTreffId = PersonTreffId(UUID.randomUUID()),
                jobbsøker = jobbsøker,
            )
        }

        return prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            batchRader.chunked(maksStørrelsePerBatch).flatMap { batch ->
                batch.forEach { rad ->
                    stmt.addJobbsøkerTilBatch(
                        personTreffId = rad.personTreffId,
                        treffDbId = treffDbId,
                        jobbsøker = rad.jobbsøker,
                    )
                }

                batch.toOpprettedeJobbsøkere(stmt.execBatchReturnIds())
            }
        }
    }

    private fun PreparedStatement.addJobbsøkerTilBatch(
        personTreffId: PersonTreffId,
        treffDbId: Long,
        jobbsøker: LeggTilJobbsøker,
    ) {
        setObject(1, personTreffId.somUuid)
        setLong(2, treffDbId)
        setString(3, jobbsøker.fødselsnummer.asString)
        setString(4, jobbsøker.fornavn.asString)
        setString(5, jobbsøker.etternavn.asString)
        setString(6, jobbsøker.navkontor?.asString)
        setString(7, jobbsøker.veilederNavn?.asString)
        setString(8, jobbsøker.veilederNavIdent?.asString)
        setString(9, JobbsøkerStatus.LAGT_TIL.name)
        addBatch()
    }

    private fun List<JobbsøkerBatchRad>.toOpprettedeJobbsøkere(generatedIds: List<Long>): List<OpprettetJobbsøker> =
        generatedIds.mapIndexed { index, jobbsøkerId ->
            OpprettetJobbsøker(personTreffId = this[index].personTreffId, jobbsøkerId = jobbsøkerId)
        }

    private fun PreparedStatement.addJobbsøkerHendelseTilBatch(
        personTreffId: PersonTreffId,
        tidspunkt: Instant,
        hendelsestype: JobbsøkerHendelsestype,
        aktørType: AktørType,
        opprettetAv: String,
        hendelseData: String?,
    ) {
        setObject(1, UUID.randomUUID())
        setObject(2, personTreffId.somUuid)
        setTimestamp(3, Timestamp.from(tidspunkt))
        setString(4, hendelsestype.name)
        setString(5, aktørType.name)
        setString(6, opprettetAv)
        setString(7, hendelseData)
        addBatch()
    }

    fun leggTilHendelserForJobbsøkere(
        connection: Connection,
        hendelsestype: JobbsøkerHendelsestype,
        personTreffIds: List<PersonTreffId>,
        opprettetAv: String,
        arrangørtype: AktørType = AktørType.ARRANGØR,
        hendelseData: String? = null,
        tidspunkt: Instant = Instant.now(),
        maksStørrelsePerBatch: Int = 500
    ) {
        val sql = """
            INSERT INTO jobbsoker_hendelse
              (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon, hendelse_data)
            VALUES (?, (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?), ?, ?, ?, ?, ?::jsonb)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            personTreffIds.chunked(maksStørrelsePerBatch).forEach { batch ->
                batch.forEach { id ->
                    stmt.addJobbsøkerHendelseTilBatch(
                        personTreffId = id,
                        tidspunkt = tidspunkt,
                        hendelsestype = hendelsestype,
                        aktørType = arrangørtype,
                        opprettetAv = opprettetAv,
                        hendelseData = hendelseData,
                    )
                }

                stmt.executeBatch()
            }
        }
    }

    fun leggTilHendelse(
        connection: Connection,
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
        connection.prepareStatement(sql).use { stmt ->
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



    private fun Connection.treffDbId(treff: TreffId): Long =
        prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?").use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getLong(1)
                else error("Treff ${treff.somUuid} finnes ikke")
            }
        }

    fun hentJobbsøkere(treff: TreffId): List<Jobbsøker> =
        dataSource.connection.use { conn -> hentJobbsøkere(conn, treff) }

    fun hentSlettedeJobbsøkere(treff: TreffId): List<Jobbsøker> = dataSource.connection.use { conn ->
        conn.prepareStatement(
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
                    rt.id as treff_id
                FROM jobbsoker js
                JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ? AND js.status = 'SLETTET' AND js.er_synlig = TRUE
                GROUP BY js.id, js.jobbsoker_id, js.fodselsnummer, js.fornavn, js.etternavn,
                     js.navkontor, js.veileder_navn, js.veileder_navident, rt.id
            ORDER BY js.jobbsoker_id;
                """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.toJobbsøkerUtenHendelser() else null }.toList()
            }
        }
    }

    fun hentJobbsøkere(conn: Connection, treff: TreffId): List<Jobbsøker> {
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

        return conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.toJobbsøker() else null }.toList()
            }
        }
    }

    fun hentAntallJobbsøkere(treff: TreffId): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
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
        dataSource.connection.use { conn ->
            hentPersonTreffId(conn, treffId, fødselsnummer)
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
                "WHERE rt.id = ? AND j.fodselsnummer = ANY(?) AND j.status != 'SLETTET'"
        return prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setArray(2, createArrayOf("varchar", fødselsnumre.map { it.asString }.toTypedArray()))
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) PersonTreffId(UUID.fromString(rs.getString("id"))) else null }.toList()
            }
        }
    }

    fun hentFødselsnummer(personTreffId: PersonTreffId): Fødselsnummer? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT fodselsnummer FROM jobbsoker WHERE id = ?").use { stmt ->
                stmt.setObject(1, personTreffId.somUuid)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) Fødselsnummer(rs.getString("fodselsnummer")) else null
                }
            }
        }

    private fun parseHendelser(json: String): List<JobbsøkerHendelse> {
        val hendelserRaw = mapper.readTree(json)
        return hendelserRaw.map { h ->
            val hendelsestype = JobbsøkerHendelsestype.valueOf(h["hendelsestype"].asText())
            JobbsøkerHendelse(
                id = UUID.fromString(h["id"].asText()),
                tidspunkt = ZonedDateTime.parse(h["tidspunkt"].asText()),
                hendelsestype = hendelsestype,
                opprettetAvAktørType = AktørType.valueOf(h["opprettetAvAktortype"].asText()),
                aktørIdentifikasjon = h["aktøridentifikasjon"]?.takeIf { !it.isNull }?.asText(),
                hendelseData = parseHendelseData(mapper, hendelsestype, h["hendelseData"]?.takeIf { !it.isNull })
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

    private fun ResultSet.toJobbsøkerUtenHendelser() = Jobbsøker(
        personTreffId = PersonTreffId(UUID.fromString(getString("id"))),
        treffId = TreffId(getString("treff_id")),
        fødselsnummer = Fødselsnummer(getString("fodselsnummer")),
        fornavn = Fornavn(getString("fornavn")),
        etternavn = Etternavn(getString("etternavn")),
        navkontor = getString("navkontor")?.let(::Navkontor),
        veilederNavn = getString("veileder_navn")?.let(::VeilederNavn),
        veilederNavIdent = getString("veileder_navident")?.let(::VeilederNavIdent),
        status = JobbsøkerStatus.valueOf(getString("status")),
    )


    fun hentJobbsøkerHendelser(treff: TreffId): List<JobbsøkerHendelseMedJobbsøkerData> {
        dataSource.connection.use { conn ->
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

            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<JobbsøkerHendelseMedJobbsøkerData>()
                    while (rs.next()) {
                        val hendelsestype = JobbsøkerHendelsestype.valueOf(rs.getString("hendelsestype"))
                        result.add(
                            JobbsøkerHendelseMedJobbsøkerData(
                                id = UUID.fromString(rs.getString("hendelse_id")),
                                tidspunkt = rs.getTimestamp("tidspunkt").toInstant()
                                    .atZone(java.time.ZoneId.of("Europe/Oslo")),
                                hendelsestype = hendelsestype,
                                opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                                aktørIdentifikasjon = rs.getString("aktøridentifikasjon"),
                                fødselsnummer = Fødselsnummer(rs.getString("fodselsnummer")),
                                fornavn = Fornavn(rs.getString("fornavn")),
                                etternavn = Etternavn(rs.getString("etternavn")),
                                personTreffId = PersonTreffId(
                                    UUID.fromString(rs.getString("person_treff_id"))
                                ),
                                hendelseData = parseHendelseData(mapper, hendelsestype, rs.getString("hendelse_data"))
                            )
                        )
                    }
                    return result
                }
            }
        }
    }

    fun hentJobbsøker(treff: TreffId, fødselsnummer: Fødselsnummer): Jobbsøker? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
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
            ).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.setString(2, fødselsnummer.asString)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toJobbsøker() else null
                }
            }
        }

    fun endreStatus(
        connection: Connection,
        personTreffIder: List<PersonTreffId>,
        jobbsøkerStatus: JobbsøkerStatus,
        maksStørrelsePerBatch: Int = 500
    ) {
        val sql = """
            UPDATE jobbsoker
            SET status=?
            WHERE id=?
            """
        connection.prepareStatement(sql).use { stmt ->
            personTreffIder.chunked(maksStørrelsePerBatch).forEach { batch ->
                batch.forEach { personTreffId ->
                    stmt.setString(1, jobbsøkerStatus.name)
                    stmt.setObject(2, personTreffId.somUuid)
                    stmt.addBatch()
                }
                stmt.executeBatch()
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
        ).use { stmt ->
            stmt.setString(1, jobbsøkerStatus.name)
            stmt.setObject(2, personTreffId.somUuid)
            stmt.executeUpdate()
        }
    }

    /**
     * Henter distinkte fødselsnumre for jobbsøkere der synlighet ikke er evaluert ennå.
     * Brukes av SynlighetsBehovScheduler for å trigge need-meldinger for de som mangler synlighetsstatus.
     */
    fun hentFødselsnumreUtenEvaluertSynlighet(): List<String> = dataSource.connection.use { conn ->
        conn.prepareStatement(
            """
            SELECT DISTINCT fodselsnummer
            FROM jobbsoker
            WHERE synlighet_sist_oppdatert IS NULL
              AND status != 'SLETTET'
            """.trimIndent()
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getString("fodselsnummer") else null }.toList()
            }
        }
    }

    /**
     * Oppdaterer synlighet fra event-strømmen.
     *
     * Event har prioritet og overskriver:
     * - Alltid hvis eksisterende kilde er NEED
     * - Kun hvis nyere tidspunkt når eksisterende kilde er EVENT
     */
    fun oppdaterSynlighetFraEvent(
        fodselsnummer: String,
        erSynlig: Boolean,
        tidspunkt: Instant
    ): Int = dataSource.connection.use { conn ->
        oppdaterSynlighetFraEvent(conn, fodselsnummer, erSynlig, tidspunkt)
    }

    fun oppdaterSynlighetFraEvent(
        connection: Connection,
        fodselsnummer: String,
        erSynlig: Boolean,
        tidspunkt: Instant
    ): Int =
        connection.prepareStatement(
            """
            UPDATE jobbsoker
            SET er_synlig = ?,
                synlighet_sist_oppdatert = ?,
                synlighet_kilde = 'EVENT'
            WHERE fodselsnummer = ?
              AND (synlighet_sist_oppdatert IS NULL
                   OR synlighet_kilde = 'NEED'
                   OR synlighet_sist_oppdatert < ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setBoolean(1, erSynlig)
            stmt.setTimestamp(2, Timestamp.from(tidspunkt))
            stmt.setString(3, fodselsnummer)
            stmt.setTimestamp(4, Timestamp.from(tidspunkt))
            stmt.executeUpdate()
        }

    /**
     * Oppdaterer synlighet fra need-svar (scheduler).
     * Skriver KUN hvis synlighet ikke er satt fra før.
     */
    fun oppdaterSynlighetFraNeed(
        fodselsnummer: String,
        erSynlig: Boolean,
        tidspunkt: Instant
    ): Int = dataSource.connection.use { conn ->
        oppdaterSynlighetFraNeed(conn, fodselsnummer, erSynlig, tidspunkt)
    }

    fun oppdaterSynlighetFraNeed(
        connection: Connection,
        fodselsnummer: String,
        erSynlig: Boolean,
        tidspunkt: Instant
    ): Int =
        connection.prepareStatement(
            """
            UPDATE jobbsoker
            SET er_synlig = ?,
                synlighet_sist_oppdatert = ?,
                synlighet_kilde = 'NEED'
            WHERE fodselsnummer = ?
              AND synlighet_sist_oppdatert IS NULL
            """.trimIndent()
        ).use { stmt ->
            stmt.setBoolean(1, erSynlig)
            stmt.setTimestamp(2, Timestamp.from(tidspunkt))
            stmt.setString(3, fodselsnummer)
            stmt.executeUpdate()
        }

    /**
     * Henter status for en jobbsøker basert på personTreffId med radlås.
     * Bruker SELECT FOR UPDATE for å forhindre race conditions ved samtidige operasjoner.
     * Returnerer null hvis jobbsøkeren ikke finnes.
     */
    fun hentStatus(connection: Connection, personTreffId: PersonTreffId): JobbsøkerStatus? =
        connection.prepareStatement(
            """
            SELECT status FROM jobbsoker WHERE id = ? FOR UPDATE
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, personTreffId.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) JobbsøkerStatus.valueOf(rs.getString("status")) else null
            }
        }

    /**
     * Sjekker om en jobbsøker er synlig.
     * Returnerer true hvis synlig, false hvis ikke synlig, null hvis jobbsøkeren ikke finnes.
     */
    fun erSynlig(connection: Connection, personTreffId: PersonTreffId): Boolean? =
        connection.prepareStatement(
            """
            SELECT er_synlig FROM jobbsoker WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, personTreffId.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getBoolean("er_synlig") else null
            }
        }

    /**
     * Henter svarfrist for et rekrutteringstreff.
     * Returnerer null hvis treffet ikke finnes eller ikke har svarfrist.
     */
    fun hentSvarfrist(treffId: TreffId): ZonedDateTime? = dataSource.connection.use { conn ->
        conn.prepareStatement(
            """
            SELECT svarfrist FROM rekrutteringstreff WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getTimestamp("svarfrist")?.toInstant()?.atZone(java.time.ZoneId.of("Europe/Oslo"))
                } else null
            }
        }
    }
}
