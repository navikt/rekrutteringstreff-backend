package no.nav.toi.arbeidsgiver

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
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

    private fun hentTreffDbId(connection: Connection, treff: TreffId): Long? {
        connection.prepareStatement("SELECT db_id FROM rekrutteringstreff WHERE id = ?").use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong("db_id") else null
            }
        }
    }

    private fun finnesIDb(connection: Connection, treff: TreffId): Boolean =
        hentTreffDbId(connection, treff) != null

    fun leggTil(arbeidsgiver: LeggTilArbeidsgiver, treff: TreffId, opprettetAv: String) {
        dataSource.connection.use { connection ->
            val treffDbId: Long = hentTreffDbId(connection, treff)
                ?: throw IllegalArgumentException("Kan ikke legge til arbeidsgiver fordi treff med id ${treff.somUuid} ikke finnes.")
            val arbeidsgiverDbId = leggTilArbeidsgiver(connection, arbeidsgiver, treffDbId)
            leggTilHendelse(connection, arbeidsgiverDbId, ArbeidsgiverHendelsestype.OPPRETT, AktørType.ARRANGØR, opprettetAv)
        }
    }

    private fun leggTilArbeidsgiver(connection: Connection, arbeidsgiver: LeggTilArbeidsgiver, treffDbId: Long): Long {
        connection.prepareStatement(
            "INSERT INTO arbeidsgiver (treff_db_id, orgnr, orgnavn) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        ).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setString(2, arbeidsgiver.orgnr.toString())
            stmt.setString(3, arbeidsgiver.orgnavn.toString())
            stmt.executeUpdate()
            stmt.generatedKeys.use { keys ->
                if (keys.next()) return keys.getLong(1)
                else throw SQLException("Kunne ikke hente generert nøkkel for arbeidsgiver")
            }
        }
    }

    private fun leggTilHendelse(
        connection: Connection,
        arbeidsgiverDbId: Long,
        hendelsestype: ArbeidsgiverHendelsestype,
        opprettetAvAktørType: AktørType,
        aktøridentifikasjon: String
    ) {
        connection.prepareStatement(
            """
            INSERT INTO arbeidsgiver_hendelse (
                id, arbeidsgiver_db_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setLong(2, arbeidsgiverDbId)
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
                    ag.db_id,
                    ag.orgnr,
                    ag.orgnavn,
                    rt.id as treff_id,
                    COALESCE(
                        json_agg(
                            json_build_object(
                                'id', ah.id,
                                'tidspunkt', to_char(ah.tidspunkt, 'YYYY-MM-DD"T"HH24:MI:SSOF'),
                                'hendelsestype', ah.hendelsestype,
                                'opprettetAvAktortype', ah.opprettet_av_aktortype,
                                'aktøridentifikasjon', ah.aktøridentifikasjon
                            )
                        ) FILTER (WHERE ah.id IS NOT NULL),
                        '[]'
                    ) as hendelser
                FROM arbeidsgiver ag
                JOIN rekrutteringstreff rt ON ag.treff_db_id = rt.db_id
                LEFT JOIN arbeidsgiver_hendelse ah ON ag.db_id = ah.arbeidsgiver_db_id
                WHERE rt.id = ?
                GROUP BY ag.db_id, ag.orgnr, ag.orgnavn, rt.id
                ORDER BY ag.db_id ASC;
            """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Arbeidsgiver>()
                    while (rs.next()) {
                        val arbeidsgiver = Arbeidsgiver(
                            treffId = TreffId(rs.getString("treff_id")),
                            orgnr = Orgnr(rs.getString("orgnr")),
                            orgnavn = Orgnavn(rs.getString("orgnavn")),
                            hendelser = parseHendelser(rs.getString("hendelser"))
                        )
                        result.add(arbeidsgiver)
                    }
                    return result
                }
            }
        }
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
                JOIN arbeidsgiver ag ON ah.arbeidsgiver_db_id = ag.db_id
                JOIN rekrutteringstreff rt ON ag.treff_db_id = rt.db_id
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
