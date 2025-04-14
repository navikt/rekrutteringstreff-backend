package no.nav.toi.jobbsoker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

enum class Hendelsestype {
    LAGT_TIL
}

enum class AktørType {
    ARRANGØR
}

data class JobbsøkerHendelse(
    val id: UUID,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: Hendelsestype,
    val opprettetAvAktørType: AktørType,
    val aktørIdentifikasjon: String?
)

class JobbsøkerRepository(
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

    private fun finnesIDb(connection: Connection, treffId: TreffId): Boolean =
        hentTreffDbId(connection, treffId) != null

    fun leggTil(jobbsøker: LeggTilJobbsøker, treff: TreffId, opprettetAv: String) {
        dataSource.connection.use { connection ->
            val treffDbId: Long = hentTreffDbId(connection, treff)
                ?: throw IllegalArgumentException("Kan ikke legge til jobbsøker på treffet fordi det ikke finnes noe treff med id ${treff.somUuid}. jobbsøker=$jobbsøker")
            val jobbsøkerDbId = leggTilJobbsøker(connection, jobbsøker, treffDbId)
            leggTilHendelse(connection, jobbsøkerDbId, Hendelsestype.LAGT_TIL, AktørType.ARRANGØR, opprettetAv)
        }
    }

    private fun leggTilJobbsøker(connection: Connection, jobbsøker: LeggTilJobbsøker, treffDbId: Long): Long {
        connection.prepareStatement(
            """
            INSERT INTO jobbsoker (
                treff_db_id, fodselsnummer, kandidatnummer, fornavn, etternavn, 
                navkontor, veileder_navn, veileder_navident
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            Statement.RETURN_GENERATED_KEYS
        ).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setString(2, jobbsøker.fødselsnummer.asString)
            stmt.setString(3, jobbsøker.kandidatnummer?.asString)
            stmt.setString(4, jobbsøker.fornavn.asString)
            stmt.setString(5, jobbsøker.etternavn.asString)
            stmt.setString(6, jobbsøker.navkontor?.asString)
            stmt.setString(7, jobbsøker.veilederNavn?.asString)
            stmt.setString(8, jobbsøker.veilederNavIdent?.asString)
            stmt.executeUpdate()
            stmt.generatedKeys.use { keys ->
                if (keys.next()) return keys.getLong(1)
                else throw SQLException("Kunne ikke hente generert nøkkel for jobbsøker")
            }
        }
    }

    private fun leggTilHendelse(
        connection: Connection,
        jobbsøkerDbId: Long,
        hendelsestype: Hendelsestype,
        opprettetAvAktørType: AktørType,
        aktørIdentifikasjon: String
    ) {
        connection.prepareStatement(
            """
            INSERT INTO jobbsoker_hendelse (
                id, jobbsoker_db_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setLong(2, jobbsøkerDbId)
            stmt.setTimestamp(3, Timestamp.from(Instant.now()))
            stmt.setString(4, hendelsestype.toString())
            stmt.setString(5, opprettetAvAktørType.toString())
            stmt.setString(6, aktørIdentifikasjon)
            stmt.executeUpdate()
        }
    }

    fun hentJobbsøkere(treff: TreffId): List<Jobbsøker> {
        dataSource.connection.use { connection ->
            if (!finnesIDb(connection, treff))
                throw IllegalArgumentException("Kan ikke hente jobbsøkere fordi det ikke finnes noe rekrutteringstreff med id $treff.")

            val sql = """
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
                                'aktorIdentifikasjon', jh.aktøridentifikasjon
                            )
                        ) FILTER (WHERE jh.id IS NOT NULL),
                        '[]'
                    ) as hendelser
                FROM jobbsoker js
                JOIN rekrutteringstreff rt ON js.treff_db_id = rt.db_id
                LEFT JOIN jobbsoker_hendelse jh ON js.db_id = jh.jobbsoker_db_id
                WHERE rt.id = ?
                GROUP BY js.db_id, js.fodselsnummer, js.kandidatnummer, js.fornavn, js.etternavn, 
                         js.navkontor, js.veileder_navn, js.veileder_navident, rt.id
                ORDER BY js.db_id ASC;
            """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Jobbsøker>()
                    while (rs.next()) {
                        val jobbsøker = Jobbsøker(
                            treffId = TreffId(rs.getString("treff_id")),
                            fødselsnummer = Fødselsnummer(rs.getString("fodselsnummer")),
                            kandidatnummer = rs.getString("kandidatnummer")?.let { Kandidatnummer(it) },
                            fornavn = Fornavn(rs.getString("fornavn")),
                            etternavn = Etternavn(rs.getString("etternavn")),
                            navkontor = rs.getString("navkontor")?.let { Navkontor(it) },
                            veilederNavn = rs.getString("veileder_navn")?.let { VeilederNavn(it) },
                            veilederNavIdent = rs.getString("veileder_navident")?.let { VeilederNavIdent(it) },
                            hendelser = parseHendelser(rs.getString("hendelser"))
                        )
                        result.add(jobbsøker)
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
            val aktorIdentifikasjon: String?
        )
        return objectMapper.readValue(json, object : TypeReference<List<HendelseJson>>() {}).map { h ->
            JobbsøkerHendelse(
                id = UUID.fromString(h.id),
                tidspunkt = ZonedDateTime.parse(h.tidspunkt),
                hendelsestype = Hendelsestype.valueOf(h.hendelsestype),
                opprettetAvAktørType = AktørType.valueOf(h.opprettetAvAktortype),
                aktørIdentifikasjon = h.aktorIdentifikasjon
            )
        }
    }
}
