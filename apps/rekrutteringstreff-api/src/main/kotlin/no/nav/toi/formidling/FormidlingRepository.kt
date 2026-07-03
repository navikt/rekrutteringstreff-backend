package no.nav.toi.formidling

import no.nav.toi.AktørType
import no.nav.toi.FormidlingHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.executeInTransaction
import no.nav.toi.formidling.dto.FormidlingDto
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource

class FormidlingRepository(private val dataSource: DataSource) {

    fun opprett(
        connection: Connection,
        treffId: TreffId,
        personTreffId: PersonTreffId,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        stillingId: UUID,
        kandidatlisteId: UUID? = null,
        utfallSendtTidspunkt: ZonedDateTime? = null,
        yrkestittel: String? = null,
        janzzKonseptId: String? = null,
        kontornummer: String? = null,
        kontornavn: String? = null,
        opprettetAvNavn: String? = null,
        opprettetAvNavIdent: String? = null,
    ): Long {
        val sql = """
            INSERT INTO formidling (rekrutteringstreff_id, jobbsoker_id, arbeidsgiver_id, stilling_id, kandidatliste_id, utfall_sendt_tidspunkt, yrkestittel, janzz_konsept_id, kontornummer, kontornavn, opprettet_av_veileder_navn, opprettet_av_veileder_navident)
            VALUES (
                (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?),
                (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?),
                (SELECT arbeidsgiver_id FROM arbeidsgiver WHERE id = ? AND rekrutteringstreff_id = (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?) AND status = 'AKTIV'),
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
        """.trimIndent()

        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setObject(2, personTreffId.somUuid)
            stmt.setObject(3, arbeidsgiverTreffId.somUuid)
            stmt.setObject(4, treffId.somUuid)
            stmt.setObject(5, stillingId)
            stmt.setNullableUuid(6, kandidatlisteId)
            stmt.setNullableTimestampWithTimezone(7, utfallSendtTidspunkt)
            stmt.setString(8, yrkestittel)
            stmt.setString(9, janzzKonseptId)
            stmt.setString(10, kontornummer)
            stmt.setString(11, kontornavn)
            stmt.setString(12, opprettetAvNavn)
            stmt.setString(13, opprettetAvNavIdent)
            stmt.executeUpdate()
            stmt.generatedKeys.use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    fun hent(formidlingId: Long): Formidling? = dataSource.connection.use { conn ->
        val sql = "$HENT_FORMIDLING_BASE WHERE f.formidling_id = ? AND f.slettet_tidspunkt IS NULL"

        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, formidlingId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toFormidling() else null
            }
        }
    }

    fun hent(treffId: TreffId, personTreffId: PersonTreffId, arbeidsgiverTreffId: ArbeidsgiverTreffId): Formidling? = dataSource.connection.use { conn ->
        val sql = "$HENT_FORMIDLING_BASE WHERE rt.id = ? AND js.id = ? AND ag.id = ? AND f.slettet_tidspunkt IS NULL"

        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setObject(2, personTreffId.somUuid)
            stmt.setObject(3, arbeidsgiverTreffId.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toFormidling() else null
            }
        }
    }

    fun hent(treffId: TreffId, id: UUID): Formidling? = dataSource.connection.use { conn ->
        val sql = "$HENT_FORMIDLING_BASE WHERE rt.id = ? AND f.id = ? AND f.slettet_tidspunkt IS NULL"

        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setObject(2, id)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toFormidling() else null
            }
        }
    }


    /** Sjekker om en formidling finnes på et gitt treff, uavhengig av om den er markert slettet. */
    fun finnesPåTreff(treffId: TreffId, id: UUID): Boolean = dataSource.connection.use { conn ->
        val sql = """
            SELECT 1
            FROM formidling f
            JOIN rekrutteringstreff rt ON f.rekrutteringstreff_id = rt.rekrutteringstreff_id
            WHERE rt.id = ? AND f.id = ?
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.setObject(2, id)
            stmt.executeQuery().use { it.next() }
        }
    }

    fun markerSlettet(connection: Connection, formidlingId: Long): Boolean {
        val sql = """
            UPDATE formidling
            SET slettet_tidspunkt = now()
            WHERE formidling_id = ? AND slettet_tidspunkt IS NULL
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, formidlingId)
            stmt.executeUpdate() > 0
        }
    }

    fun oppdaterUtfallSendtTidspunkt(connection: Connection, formidlingId: Long): Boolean {
        val sql = """
            UPDATE formidling
            SET utfall_sendt_tidspunkt = now()
            WHERE formidling_id = ? AND slettet_tidspunkt IS NULL AND utfall_sendt_tidspunkt IS NULL
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, formidlingId)
            stmt.executeUpdate() > 0
        }
    }

    fun leggTilHendelseForFormidling(
        connection: Connection,
        formidlingId: Long,
        hendelsestype: FormidlingHendelsestype,
        opprettetAvAktørType: AktørType,
        aktøridentifikasjon: String?,
        hendelseData: String? = null,
    ) {
        val sql = """
            INSERT INTO formidling_hendelse (
                id, formidling_id, hendelsestype, tidspunkt, opprettet_av_aktortype, aktøridentifikasjon, hendelse_data
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, UUID.randomUUID())
            stmt.setLong(2, formidlingId)
            stmt.setString(3, hendelsestype.name)
            stmt.setTimestamp(4, Timestamp.from(Instant.now()))
            stmt.setString(5, opprettetAvAktørType.name)
            stmt.setString(6, aktøridentifikasjon)
            stmt.setString(7, hendelseData)
            stmt.executeUpdate()
        }
    }

    fun hentAlleForTreff(
        treffId: TreffId,
        sortering: FormidlingSortering = FormidlingSortering.TIDSPUNKT,
        retning: FormidlingSorteringsretning? = null,
        arbeidsgivere: List<String> = emptyList(),
    ): List<FormidlingDto> {
        val where = tilWhereClause(byggBasisFilter(treffId) + byggArbeidsgiverFilter(arbeidsgivere))
        return hentMedWhere(where, sortering, retning)
    }

    fun hentEgneForTreff(
        treffId: TreffId,
        veilederNavIdent: String,
        sortering: FormidlingSortering = FormidlingSortering.TIDSPUNKT,
        retning: FormidlingSorteringsretning? = null,
        arbeidsgivere: List<String> = emptyList(),
    ): List<FormidlingDto> {
        val where = tilWhereClause(
            byggBasisFilter(treffId)
                + byggOpprettetFormidlingFilter(veilederNavIdent)
                + byggArbeidsgiverFilter(arbeidsgivere)
        )
        return hentMedWhere(where, sortering, retning)
    }

    private fun hentMedWhere(
        where: WhereClause,
        sortering: FormidlingSortering,
        retning: FormidlingSorteringsretning?,
    ): List<FormidlingDto> =
        dataSource.executeInTransaction { conn ->
            val sql = """
                SELECT
                    f.id,
                    f.opprettet_tidspunkt,
                    f.stilling_id,
                    f.yrkestittel,
                    f.opprettet_av_veileder_navn,
                    f.opprettet_av_veileder_navident,
                    CASE WHEN j.sperret THEN NULL WHEN j.er_synlig THEN j.fodselsnummer ELSE NULL END AS fodselsnummer,
                    CASE WHEN j.sperret THEN NULL ELSE j.fornavn END AS fornavn,
                    CASE WHEN j.sperret THEN NULL ELSE j.etternavn END AS etternavn,
                    j.sperret,
                    ag.orgnr,
                    ag.orgnavn
                FROM formidling f
                JOIN rekrutteringstreff rt ON f.rekrutteringstreff_id = rt.rekrutteringstreff_id
                JOIN jobbsoker j ON f.jobbsoker_id = j.jobbsoker_id
                JOIN arbeidsgiver ag ON f.arbeidsgiver_id = ag.arbeidsgiver_id
                ${where.sql}
                ${sortering.orderByClause(retning)}
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
                settWhereParametere(stmt, where.params)
                stmt.executeQuery().use { rs ->
                    val resultater = mutableListOf<FormidlingDto>()
                    while (rs.next()) {
                        resultater += rs.toFormidlingDto()
                    }
                    resultater
                }
            }
        }

    private fun settWhereParametere(stmt: PreparedStatement, params: List<SqlParam>) {
        params.forEachIndexed { index, param ->
            settParam(stmt, index + 1, param)
        }
    }

    private fun settParam(stmt: PreparedStatement, index: Int, param: SqlParam) {
        when (param) {
            is SqlParam.Uuid -> stmt.setObject(index, param.value)
            is SqlParam.Text -> stmt.setString(index, param.value)
            is SqlParam.TextArray ->
                stmt.setArray(index, stmt.connection.createArrayOf("text", param.value.toTypedArray()))
        }
    }

    private fun ResultSet.toFormidlingDto() = FormidlingDto(
        id = UUID.fromString(getString("id")),
        opprettetTidspunkt = getTimestamp("opprettet_tidspunkt").toInstant().atZone(OSLO),
        fødselsnummer = getString("fodselsnummer"),
        fornavn = getString("fornavn"),
        etternavn = getString("etternavn"),
        orgnr = getString("orgnr"),
        orgnavn = getString("orgnavn"),
        stillingId = UUID.fromString(getString("stilling_id")),
        yrkestittel = getString("yrkestittel"),
        sperret = getBoolean("sperret"),
        opprettetAvNavn = getString("opprettet_av_veileder_navn"),
        opprettetAvNavIdent = getString("opprettet_av_veileder_navident"),
    )

    private data class WhereClause(
        val sql: String,
        val params: List<SqlParam>,
    )

    private data class Condition(
        val sql: String,
        val params: List<SqlParam> = emptyList(),
    ) {
        constructor(sql: String, vararg params: SqlParam) : this(sql, params.toList())
    }

    private sealed interface SqlParam {
        data class Uuid(val value: UUID) : SqlParam
        data class Text(val value: String) : SqlParam
        data class TextArray(val value: List<String>) : SqlParam
    }

    private fun byggBasisFilter(treffId: TreffId): List<Condition> = listOf(
        Condition("rt.id = ?", SqlParam.Uuid(treffId.somUuid)),
        Condition("f.slettet_tidspunkt IS NULL"),
        Condition("j.status != 'SLETTET'"),
    )

    private fun byggVeilederEllerEnhetFilter(
        veilederNavIdent: String,
        tilknyttedeEnheter: List<String>,
    ): Condition {
        val unikeEnheter = tilknyttedeEnheter.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        return Condition(
            "(UPPER(j.veileder_navident) = ? OR j.kontornummer = ANY (?::text[]))",
            SqlParam.Text(veilederNavIdent.trim().uppercase()),
            SqlParam.TextArray(unikeEnheter),
        )
    }

    private fun byggOpprettetFormidlingFilter(
        veilederNavIdent: String,
    ): Condition {
        return Condition(
            "UPPER(f.opprettet_av_veileder_navident) = ?",
            SqlParam.Text(veilederNavIdent.trim().uppercase()),
        )
    }

    private fun byggArbeidsgiverFilter(arbeidsgivere: List<String>): List<Condition> {
        val orgnr = arbeidsgivere.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        if (orgnr.isEmpty()) return emptyList()
        return listOf(Condition("ag.orgnr = ANY (?::text[])", SqlParam.TextArray(orgnr)))
    }

    private fun tilWhereClause(conditions: List<Condition>): WhereClause = WhereClause(
        sql = "WHERE " + conditions.joinToString(" AND ") { it.sql },
        params = conditions.flatMap { it.params },
    )

    private fun ResultSet.toFormidling() = Formidling(
        formidlingId = getLong("formidling_id"),
        id = UUID.fromString(getString("id")),
        treffId = TreffId(getString("treff_id")),
        jobbsøkerPersonTreffId = PersonTreffId(UUID.fromString(getString("jobbsoker_treff_id"))),
        arbeidsgiverTreffId = ArbeidsgiverTreffId(getString("arbeidsgiver_treff_id")),
        stillingId = UUID.fromString(getString("stilling_id")),
        kandidatlisteId = getObject("kandidatliste_id", UUID::class.java),
        utfallSendtTidspunkt = getTimestamp("utfall_sendt_tidspunkt")?.toInstant()?.atZone(ZoneId.of("Europe/Oslo")),
        opprettetTidspunkt = getTimestamp("opprettet_tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
    )

    private fun PreparedStatement.setNullableUuid(index: Int, value: UUID?) {
        if (value == null) {
            setNull(index, Types.OTHER)
        } else {
            setObject(index, value)
        }
    }

    private fun PreparedStatement.setNullableTimestampWithTimezone(index: Int, value: ZonedDateTime?) {
        if (value == null) {
            setNull(index, Types.TIMESTAMP_WITH_TIMEZONE)
        } else {
            setObject(index, value.toOffsetDateTime())
        }
    }

    private companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10
        private val OSLO = ZoneId.of("Europe/Oslo")

        private val HENT_FORMIDLING_BASE = """
            SELECT 
                f.formidling_id,
                f.id,
                rt.id as treff_id,
                js.id as jobbsoker_treff_id,
                ag.id as arbeidsgiver_treff_id,
                f.stilling_id,
                f.kandidatliste_id,
                f.utfall_sendt_tidspunkt,
                f.opprettet_tidspunkt,
                f.opprettet_av_veileder_navn,
                f.opprettet_av_veileder_navident
            FROM formidling f
            JOIN rekrutteringstreff rt ON f.rekrutteringstreff_id = rt.rekrutteringstreff_id
            JOIN jobbsoker js ON f.jobbsoker_id = js.jobbsoker_id
            JOIN arbeidsgiver ag ON f.arbeidsgiver_id = ag.arbeidsgiver_id
        """.trimIndent()
    }
}

/**
 * Sorteringsretning. SQL-verdien er fast definert (ikke bygget fra brukerinput) for å unngå SQL-injeksjon.
 */
enum class FormidlingSorteringsretning(internal val sql: String) {
    STIGENDE("ASC"),
    SYNKENDE("DESC");

    companion object {
        fun fraQueryParam(verdi: String?): FormidlingSorteringsretning? =
            when (verdi?.trim()?.lowercase()) {
                "asc" -> STIGENDE
                "desc" -> SYNKENDE
                else -> null
            }
    }
}

/**
 * Sorteringsfelt for formidlingslisten. ORDER BY-klausulen bygges av faste fragmenter
 * (felt + retning fra enum-er, ikke fra rå brukerinput) for å unngå SQL-injeksjon.
 */
enum class FormidlingSortering {
    TIDSPUNKT,
    ARBEIDSGIVER,
    JOBBSOKER;

    private val standardRetning: FormidlingSorteringsretning
        get() = when (this) {
            TIDSPUNKT -> FormidlingSorteringsretning.SYNKENDE
            ARBEIDSGIVER, JOBBSOKER -> FormidlingSorteringsretning.STIGENDE
        }

    internal fun orderByClause(retning: FormidlingSorteringsretning? = null): String {
        val retning = (retning ?: standardRetning).sql
        return when (this) {
            TIDSPUNKT ->
                "ORDER BY f.opprettet_tidspunkt $retning, f.formidling_id $retning"
            ARBEIDSGIVER ->
                "ORDER BY lower(ag.orgnavn) $retning NULLS LAST, f.opprettet_tidspunkt DESC, f.formidling_id DESC"
            JOBBSOKER ->
                "ORDER BY lower(j.etternavn) $retning NULLS LAST, lower(j.fornavn) $retning NULLS LAST, f.opprettet_tidspunkt DESC, f.formidling_id DESC"
        }
    }

    companion object {
        fun fraQueryParam(verdi: String?): FormidlingSortering =
            when (verdi?.trim()?.lowercase()) {
                "arbeidsgiver" -> ARBEIDSGIVER
                "jobbsoker", "jobbsøker" -> JOBBSOKER
                else -> TIDSPUNKT
            }
    }
}
