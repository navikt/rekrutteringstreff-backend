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
        aktøridentifikasjon: String
    ) {
        connection.prepareStatement(
            """
            INSERT INTO arbeidsgiver_hendelse (
                id, arbeidsgiver_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon
            ) VALUES (?, (SELECT arbeidsgiver_id FROM arbeidsgiver WHERE id = ?), ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setObject(2, arbeidsgiverTreffId.somUuid)
            stmt.setTimestamp(3, Timestamp.from(Instant.now()))
            stmt.setString(4, hendelsestype.toString())
            stmt.setString(5, opprettetAvAktørType.toString())
            stmt.setString(6, aktøridentifikasjon)
            stmt.executeUpdate()
        }
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

    fun markerSlettet(connection: Connection, arbeidsgiverId: UUID, opprettetAv: String): Boolean {
        val arbeidsgiverTreffId = ArbeidsgiverTreffId(arbeidsgiverId)
        if (!finnesArbeidsgiver(connection, arbeidsgiverTreffId)) return false
        leggTilHendelse(connection, arbeidsgiverTreffId, ArbeidsgiverHendelsestype.SLETTET, AktørType.ARRANGØR, opprettetAv)
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
