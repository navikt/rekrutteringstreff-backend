package no.nav.toi.arbeidsgiver

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource

data class ArbeidsgiverHendelse(
    val id: UUID,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: ArbeidsgiverHendelsestype,
    val opprettetAvAktørType: AktørType,
    val aktøridentifikasjon: String?
)

data class ArbeidsgiverHendelseMedArbeidsgiverData(
    val id: UUID,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: ArbeidsgiverHendelsestype,
    val opprettetAvAktørType: AktørType,
    val aktøridentifikasjon: String?,
    val orgnr: Orgnr,
    val orgnavn: Orgnavn
)

class ArbeidsgiverRepository(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) {


    private fun finnesIDb(connection: Connection, treff: TreffId): Boolean {
        connection.prepareStatement("SELECT 1 FROM rekrutteringstreff WHERE id = ?").use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    fun opprettArbeidsgiver(connection: Connection, arbeidsgiver: LeggTilArbeidsgiver, treff: TreffId): ArbeidsgiverTreffId {
        val arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID())
        connection.prepareStatement(
            """
            INSERT INTO arbeidsgiver (id, rekrutteringstreff_id, orgnr, orgnavn, status, gateadresse, postnummer, poststed) 
            VALUES (?, (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?), ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, arbeidsgiverTreffId.somUuid)
            stmt.setObject(2, treff.somUuid)
            stmt.setString(3, arbeidsgiver.orgnr.asString)
            stmt.setString(4, arbeidsgiver.orgnavn.asString)
            stmt.setString(5, ArbeidsgiverStatus.AKTIV.name)
            stmt.setString(6, arbeidsgiver.gateadresse)
            stmt.setString(7, arbeidsgiver.postnummer)
            stmt.setString(8, arbeidsgiver.poststed)
            val rowsAffected = stmt.executeUpdate()
            if (rowsAffected == 0) {
                throw IllegalArgumentException("Kan ikke legge til arbeidsgiver fordi treff med id ${treff.somUuid} ikke finnes.")
            }
        }
        return arbeidsgiverTreffId
    }

    fun leggTilNaringskoder(connection: Connection, arbeidsgiverTreffId: ArbeidsgiverTreffId, koder: List<Næringskode>) {
        if (koder.isEmpty()) return
        connection.prepareStatement(
            "INSERT INTO naringskode (arbeidsgiver_id, kode, beskrivelse) VALUES ((SELECT arbeidsgiver_id FROM arbeidsgiver WHERE id = ?), ?, ?)"
        ).use { stmt ->
            for (nk in koder) {
                stmt.setObject(1, arbeidsgiverTreffId.somUuid)
                stmt.setString(2, nk.kode)
                stmt.setString(3, nk.beskrivelse)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun leggTilHendelse(
        connection: Connection,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        hendelsestype: ArbeidsgiverHendelsestype,
        opprettetAvAktørType: AktørType,
        aktøridentifikasjon: String,
        hendelseData: String? = null,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO arbeidsgiver_hendelse (
                id, arbeidsgiver_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon, hendelse_data
            ) VALUES (?, (SELECT arbeidsgiver_id FROM arbeidsgiver WHERE id = ?), ?, ?, ?, ?, ?::jsonb)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, arbeidsgiverTreffId.somUuid)
            stmt.setTimestamp(3, Timestamp.from(Instant.now()))
            stmt.setString(4, hendelsestype.toString())
            stmt.setString(5, opprettetAvAktørType.toString())
            stmt.setString(6, aktøridentifikasjon)
            stmt.setString(7, hendelseData)
            stmt.executeUpdate()
        }
    }

    fun reaktiverArbeidsgiver(connection: Connection, treff: TreffId, arbeidsgiver: LeggTilArbeidsgiver): ArbeidsgiverTreffId? {
        val sql = """
            SELECT ag.id
            FROM arbeidsgiver ag
            JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
            WHERE rt.id = ? AND ag.orgnr = ? AND ag.status = ?
            ORDER BY ag.arbeidsgiver_id DESC
            LIMIT 1
        """.trimIndent()
        val eksisterende: UUID = connection.prepareStatement(sql).use { ps ->
            ps.setObject(1, treff.somUuid)
            ps.setString(2, arbeidsgiver.orgnr.asString)
            ps.setString(3, ArbeidsgiverStatus.SLETTET.name)
            ps.executeQuery().use { rs ->
                if (rs.next()) UUID.fromString(rs.getString("id")) else null
            }
        } ?: return null
        connection.prepareStatement(
            """
            UPDATE arbeidsgiver
            SET status = ?, orgnavn = ?, gateadresse = ?, postnummer = ?, poststed = ?
            WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, ArbeidsgiverStatus.AKTIV.name)
            ps.setString(2, arbeidsgiver.orgnavn.asString)
            ps.setString(3, arbeidsgiver.gateadresse)
            ps.setString(4, arbeidsgiver.postnummer)
            ps.setString(5, arbeidsgiver.poststed)
            ps.setObject(6, eksisterende)
            ps.executeUpdate()
        }
        return ArbeidsgiverTreffId(eksisterende)
    }


    fun upsertBehov(
        connection: Connection,
        treffId: TreffId,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        behov: ArbeidsgiversBehov,
    ): Boolean {
        val samledeJson = objectMapper.writeValueAsString(behov.samledeKvalifikasjoner)
        val personligeJson = objectMapper.writeValueAsString(behov.personligeEgenskaper)
        val arbeidssprakArray = connection.createArrayOf("text", behov.arbeidssprak.toTypedArray())
        val ansettelsesformerArr = connection.createArrayOf("text", behov.ansettelsesformer.map { it.name }.toTypedArray())

        val sql = """
            INSERT INTO arbeidsgivers_behov
                (arbeidsgiver_id, arbeidssprak, antall, samlede_kvalifikasjoner, ansettelsesformer, personlige_egenskaper, oppdatert)
            SELECT
                ag.arbeidsgiver_id,
                ?, ?, ?::jsonb, ?, ?::jsonb, now()
            FROM arbeidsgiver ag
            JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
            WHERE rt.id = ? AND ag.id = ? AND ag.status <> 'SLETTET'
            ON CONFLICT (arbeidsgiver_id) DO UPDATE SET
                arbeidssprak = EXCLUDED.arbeidssprak,
                antall = EXCLUDED.antall,
                samlede_kvalifikasjoner = EXCLUDED.samlede_kvalifikasjoner,
                ansettelsesformer = EXCLUDED.ansettelsesformer,
                personlige_egenskaper = EXCLUDED.personlige_egenskaper,
                oppdatert = now()
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setArray(1, arbeidssprakArray)
            stmt.setInt(2, behov.antall)
            stmt.setString(3, samledeJson)
            stmt.setArray(4, ansettelsesformerArr)
            stmt.setString(5, personligeJson)
            stmt.setObject(6, treffId.somUuid)
            stmt.setObject(7, arbeidsgiverTreffId.somUuid)
            val rows = stmt.executeUpdate()
            return rows > 0
        }
    }

    fun hentBehov(connection: Connection, arbeidsgiverTreffId: ArbeidsgiverTreffId): ArbeidsgiversBehov? {
        val sql = """
            SELECT ab.arbeidssprak, ab.antall, ab.samlede_kvalifikasjoner, ab.ansettelsesformer, ab.personlige_egenskaper
            FROM arbeidsgivers_behov ab
            JOIN arbeidsgiver ag ON ag.arbeidsgiver_id = ab.arbeidsgiver_id
            WHERE ag.id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setObject(1, arbeidsgiverTreffId.somUuid)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.toBehovDirekte()
            }
        }
    }

    fun hentArbeidsgivereMedBehov(treff: TreffId): List<ArbeidsgiverMedBehov> {
        dataSource.connection.use { connection ->
            if (!finnesIDb(connection, treff))
                throw IllegalArgumentException("Kan ikke hente arbeidsgivere; treff med id $treff finnes ikke.")
            val sql = """
                SELECT
                    ag.id,
                    ag.orgnr,
                    ag.orgnavn,
                    ag.status,
                    ag.gateadresse,
                    ag.postnummer,
                    ag.poststed,
                    rt.id as treff_id,
                    ab.arbeidssprak as b_arbeidssprak,
                    ab.antall as b_antall,
                    ab.samlede_kvalifikasjoner as b_samlede,
                    ab.ansettelsesformer as b_ansettelsesformer,
                    ab.personlige_egenskaper as b_personlige
                FROM arbeidsgiver ag
                JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
                LEFT JOIN arbeidsgivers_behov ab ON ab.arbeidsgiver_id = ag.arbeidsgiver_id
                WHERE rt.id = ? AND ag.status <> 'SLETTET'
                ORDER BY ag.arbeidsgiver_id;
            """.trimIndent()
            connection.prepareStatement(sql).use { ps ->
                ps.setObject(1, treff.somUuid)
                ps.executeQuery().use { rs ->
                    val result = mutableListOf<ArbeidsgiverMedBehov>()
                    while (rs.next()) {
                        val arbeidsgiver = rs.toArbeidsgiver()
                        val antall = rs.getObject("b_antall") as Int?
                        val behov = if (antall == null) null else rs.toBehovFraJoin()
                        result.add(ArbeidsgiverMedBehov(arbeidsgiver, behov))
                    }
                    return result
                }
            }
        }
    }

    private fun java.sql.ResultSet.toBehovDirekte() = ArbeidsgiversBehov(
        samledeKvalifikasjoner = parseTagListe(getString("samlede_kvalifikasjoner")),
        arbeidssprak = Arbeidssprak.validerOgFiltrer((getArray("arbeidssprak").array as Array<*>).map { it.toString() }),
        antall = getInt("antall"),
        ansettelsesformer = (getArray("ansettelsesformer").array as Array<*>).map { Ansettelsesform.valueOf(it.toString()) },
        personligeEgenskaper = parseTagListe(getString("personlige_egenskaper")),
    )

    private fun java.sql.ResultSet.toBehovFraJoin() = ArbeidsgiversBehov(
        samledeKvalifikasjoner = parseTagListe(getString("b_samlede")),
        arbeidssprak = Arbeidssprak.validerOgFiltrer((getArray("b_arbeidssprak").array as Array<*>).map { it.toString() }),
        antall = getInt("b_antall"),
        ansettelsesformer = (getArray("b_ansettelsesformer").array as Array<*>).map { Ansettelsesform.valueOf(it.toString()) },
        personligeEgenskaper = parseTagListe(getString("b_personlige")),
    )

    private fun parseTagListe(json: String?): List<BehovTag> {
        if (json.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(json, object : TypeReference<List<BehovTag>>() {})
    }

    fun hentArbeidsgivere(treff: TreffId): List<Arbeidsgiver> {
        dataSource.connection.use { connection ->
            if (!finnesIDb(connection, treff))
                throw IllegalArgumentException("Kan ikke hente arbeidsgivere; treff med id $treff finnes ikke.")

            val sql = """
                SELECT
                    ag.id,
                    ag.orgnr,
                    ag.orgnavn,
                    ag.status,
                    ag.gateadresse,
                    ag.postnummer,
                    ag.poststed,
                    rt.id as treff_id
                FROM arbeidsgiver ag
                JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ?
                  AND ag.status <> 'SLETTET'
                GROUP BY ag.id, ag.arbeidsgiver_id, ag.orgnr, ag.orgnavn, rt.id
                ORDER BY ag.arbeidsgiver_id;
            """.trimIndent()

            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setObject(1, treff.somUuid)
                preparedStatement.executeQuery().use { resultSet ->
                    return generateSequence { if (resultSet.next()) resultSet.toArbeidsgiver() else null }.toList()
                }
            }
        }
    }

    fun hentArbeidsgiver(treff: TreffId, orgnr: Orgnr): Arbeidsgiver? {
        dataSource.connection.use { connection ->
            if (!finnesIDb(connection, treff))
                throw IllegalArgumentException("Kan ikke hente arbeidsgivere; treff med id $treff finnes ikke.")

            val sql = """
                SELECT
                    ag.id,
                    ag.orgnr,
                    ag.orgnavn,
                    ag.status,
                    ag.gateadresse,
                    ag.postnummer,
                    ag.poststed,
                    rt.id as treff_id
                FROM arbeidsgiver ag
                JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ? and ag.orgnr = ?
                ORDER BY ag.arbeidsgiver_id;
            """.trimIndent()

            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setObject(1, treff.somUuid)
                preparedStatement.setObject(2, orgnr.asString)
                preparedStatement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) resultSet.toArbeidsgiver() else null
                }
            }
        }
    }

    fun hentAntallArbeidsgivere(treff: TreffId): Int {
        dataSource.connection.use { connection ->
            if (!finnesIDb(connection, treff))
                throw IllegalArgumentException("Kan ikke hente arbeidsgivere; treff med id $treff finnes ikke.")
            val sql = """
            SELECT
                COUNT(1) AS antall_arbeidsgivere
            FROM arbeidsgiver ag
            JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
            WHERE rt.id = ? 
        """.trimIndent()
            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setObject(1, treff.somUuid)
                preparedStatement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) resultSet.getInt("antall_arbeidsgivere") else 0
                }
            }
        }
    }

    private fun java.sql.ResultSet.toArbeidsgiver() = Arbeidsgiver(
       arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.fromString(getString("id"))),
        treffId = TreffId(getString("treff_id")),
        orgnr = Orgnr(getString("orgnr")),
        orgnavn = Orgnavn(getString("orgnavn")),
        status = ArbeidsgiverStatus.valueOf(getString("status")),
        gateadresse = getString("gateadresse"),
        postnummer = getString("postnummer"),
        poststed = getString("poststed"),
    )

    fun markerSlettet(connection: Connection, arbeidsgiverId: UUID): Boolean {
        val arbeidsgiverTreffId = ArbeidsgiverTreffId(arbeidsgiverId)
        if (!finnesArbeidsgiver(connection, arbeidsgiverTreffId)) return false
        endreStatus(connection, arbeidsgiverId, ArbeidsgiverStatus.SLETTET)
        return true
    }

    private fun finnesArbeidsgiver(connection: Connection, arbeidsgiverTreffId: ArbeidsgiverTreffId): Boolean {
        return connection.prepareStatement("SELECT 1 FROM arbeidsgiver WHERE id = ?").use { ps ->
            ps.setObject(1, arbeidsgiverTreffId.somUuid)
            ps.executeQuery().use { rs -> rs.next() }
        }
    }

    fun endreStatus(connection: Connection, arbeidsgiverId: UUID, arbeidsgiverStatus: ArbeidsgiverStatus) {
        connection.prepareStatement(
            """
            UPDATE arbeidsgiver
            SET status=?
            WHERE id=?
            """
        ).apply {
            var i = 0
            setString(++i, arbeidsgiverStatus.name)
            setObject(++i, arbeidsgiverId)
        }.executeUpdate()
    }

    fun hentArbeidsgiverHendelser(treff: TreffId): List<ArbeidsgiverHendelseMedArbeidsgiverData> {
        dataSource.connection.use { connection ->
            val sql = """
                SELECT 
                    ah.id as hendelse_id,
                    ah.tidspunkt,
                    ah.hendelsestype,
                    ah.opprettet_av_aktortype,
                    ah.aktøridentifikasjon,
                    ag.orgnr,
                    ag.orgnavn
                FROM arbeidsgiver_hendelse ah
                JOIN arbeidsgiver ag ON ah.arbeidsgiver_id = ag.arbeidsgiver_id
                JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ?
                ORDER BY ah.tidspunkt DESC;
            """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<ArbeidsgiverHendelseMedArbeidsgiverData>()
                    while (rs.next()) {
                        result.add(
                            ArbeidsgiverHendelseMedArbeidsgiverData(
                                id = UUID.fromString(rs.getString("hendelse_id")),
                                tidspunkt = rs.getTimestamp("tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
                                hendelsestype = ArbeidsgiverHendelsestype.valueOf(rs.getString("hendelsestype")),
                                opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                                aktøridentifikasjon = rs.getString("aktøridentifikasjon"),
                                orgnr = Orgnr(rs.getString("orgnr")),
                                orgnavn = Orgnavn(rs.getString("orgnavn"))
                            )
                        )
                    }
                    return result
                }
            }
        }
    }
    private fun parseHendelser(json: String): List<ArbeidsgiverHendelse> {
        data class HendelseJson(
            val id: String,
            val tidspunkt: String,
            val hendelsestype: String,
            val opprettetAvAktortype: String,
            val aktøridentifikasjon: String?
        )
        return objectMapper.readValue(json, object : TypeReference<List<HendelseJson>>() {}).map { h ->
            ArbeidsgiverHendelse(
                id = UUID.fromString(h.id),
                tidspunkt = ZonedDateTime.parse(h.tidspunkt),
                hendelsestype = ArbeidsgiverHendelsestype.valueOf(h.hendelsestype),
                opprettetAvAktørType = AktørType.valueOf(h.opprettetAvAktortype),
                aktøridentifikasjon = h.aktøridentifikasjon
            )
        }
    }
}
