package no.nav.toi.rekrutteringstreff.innlegg

import no.nav.toi.atOslo
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class InnleggRepository(private val dataSource: DataSource) {

    private object T {
        const val TABLE                = "innlegg"
        const val COL_DB_ID            = "db_id"
        const val COL_TREFF_DB_ID      = "treff_db_id"
        const val COL_TITTEL           = "tittel"
        const val COL_NAVIDENT         = "opprettet_av_person_navident"
        const val COL_NAVN             = "opprettet_av_person_navn"
        const val COL_BESKRIVELSE      = "opprettet_av_person_beskrivelse"
        const val COL_SENDES           = "sendes_til_jobbsoker_tidspunkt"
        const val COL_HTML             = "html_content"
        const val COL_OPPRETTET        = "opprettet_tidspunkt"
        const val COL_SIST_OPPDATERT   = "sist_oppdatert_tidspunkt"
        const val COL_TREFF_UUID_ALIAS = "treff_uuid"
    }

    fun opprett(treffId: TreffId, dto: OpprettInnleggRequestDto): Innlegg =
        dataSource.connection.use { c ->
            val treffDbId = c.treffDbId(treffId)

            val sql = """
                INSERT INTO ${T.TABLE} (
                    ${T.COL_TREFF_DB_ID}, ${T.COL_TITTEL}, ${T.COL_NAVIDENT},
                    ${T.COL_NAVN}, ${T.COL_BESKRIVELSE},
                    ${T.COL_SENDES}, ${T.COL_HTML}, ${T.COL_SIST_OPPDATERT}
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                RETURNING *;
            """.trimIndent()

            c.prepareStatement(sql).use { stmt ->
                stmt.setLong (1, treffDbId)
                stmt.setString(2, dto.tittel)
                stmt.setString(3, dto.opprettetAvPersonNavident)
                stmt.setString(4, dto.opprettetAvPersonNavn)
                stmt.setString(5, dto.opprettetAvPersonBeskrivelse)
                dto.sendesTilJobbsokerTidspunkt?.let { stmt.setTimestamp(6, Timestamp.from(it.toInstant())) }
                    ?: stmt.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE)
                stmt.setString(7, dto.htmlContent)

                stmt.executeQuery().next()
                return@use rowToInnlegg(stmt.resultSet, treffId.somUuid)
            }
        }

    fun hentForTreff(treffId: TreffId): List<Innlegg> {
        val sql = """
            SELECT i.*, rt.id AS ${T.COL_TREFF_UUID_ALIAS}
              FROM ${T.TABLE} i
              JOIN rekrutteringstreff rt ON i.${T.COL_TREFF_DB_ID} = rt.db_id
             WHERE rt.id = ?
             ORDER BY i.${T.COL_OPPRETTET} ASC
        """
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs.toInnlegg() else null }.toList()
                }
            }
        }
    }

    fun hentById(innleggId: Long): Innlegg? {
        val sql = """
            SELECT i.*, rt.id AS ${T.COL_TREFF_UUID_ALIAS}
              FROM ${T.TABLE} i
              JOIN rekrutteringstreff rt ON i.${T.COL_TREFF_DB_ID} = rt.db_id
             WHERE i.${T.COL_DB_ID} = ?
        """
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, innleggId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.toInnlegg() else null }
            }
        }
    }

    fun oppdater(innleggId: Long, dto: OpprettInnleggRequestDto): Innlegg? {
        val sql = """
            UPDATE ${T.TABLE} SET
                ${T.COL_TITTEL}        = ?,
                ${T.COL_NAVIDENT}      = ?,
                ${T.COL_NAVN}          = ?,
                ${T.COL_BESKRIVELSE}   = ?,
                ${T.COL_SENDES}        = ?,
                ${T.COL_HTML}          = ?,
                ${T.COL_SIST_OPPDATERT}= NOW()
            WHERE ${T.COL_DB_ID} = ?
            RETURNING *, (SELECT id
                            FROM rekrutteringstreff r
                           WHERE r.db_id = ${T.TABLE}.${T.COL_TREFF_DB_ID})
                     AS ${T.COL_TREFF_UUID_ALIAS};
        """.trimIndent()

        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setString(1, dto.tittel)
                stmt.setString(2, dto.opprettetAvPersonNavident)
                stmt.setString(3, dto.opprettetAvPersonNavn)
                stmt.setString(4, dto.opprettetAvPersonBeskrivelse)
                dto.sendesTilJobbsokerTidspunkt?.let { stmt.setTimestamp(5, Timestamp.from(it.toInstant())) }
                    ?: stmt.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE)
                stmt.setString(6, dto.htmlContent)
                stmt.setLong  (7, innleggId)

                stmt.executeQuery().use { rs -> if (rs.next()) rs.toInnlegg() else null }
            }
        }
    }

    fun slett(innleggId: Long): Boolean =
        dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM ${T.TABLE} WHERE ${T.COL_DB_ID} = ?").use { stmt ->
                stmt.setLong(1, innleggId)
                stmt.executeUpdate() > 0
            }
        }

    private fun java.sql.Connection.treffDbId(treff: TreffId): Long =
        prepareStatement("SELECT db_id FROM rekrutteringstreff WHERE id = ?").use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().let { rs ->
                if (rs.next()) rs.getLong(1)
                else error("Treff ${treff.somUuid} finnes ikke")
            }
        }

    private fun rowToInnlegg(rs: ResultSet, treffUuid: UUID): Innlegg =
        Innlegg(
            id                           = rs.getLong(T.COL_DB_ID),
            rekrutteringstreffId         = treffUuid,
            tittel                       = rs.getString(T.COL_TITTEL),
            opprettetAvPersonNavident    = rs.getString(T.COL_NAVIDENT),
            opprettetAvPersonNavn        = rs.getString(T.COL_NAVN),
            opprettetAvPersonBeskrivelse = rs.getString(T.COL_BESKRIVELSE),
            sendesTilJobbsokerTidspunkt  = rs.getTimestamp(T.COL_SENDES)?.toInstant()?.atOslo(),
            htmlContent                  = rs.getString(T.COL_HTML),
            opprettetTidspunkt           = rs.getTimestamp(T.COL_OPPRETTET).toInstant().atOslo(),
            sistOppdatertTidspunkt       = rs.getTimestamp(T.COL_SIST_OPPDATERT).toInstant().atOslo()
        )

    private fun ResultSet.toInnlegg(): Innlegg =
        rowToInnlegg(this, getObject(T.COL_TREFF_UUID_ALIAS, UUID::class.java))
}
