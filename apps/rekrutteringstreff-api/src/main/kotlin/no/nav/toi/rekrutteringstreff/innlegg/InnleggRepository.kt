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
        const val TABLE              = "innlegg"
        const val COL_DB_ID          = "db_id"
        const val COL_ID             = "id"            // uuid
        const val COL_TREFF_DB_ID    = "treff_db_id"
        const val COL_TITTEL         = "tittel"
        const val COL_NAVIDENT       = "opprettet_av_person_navident"
        const val COL_NAVN           = "opprettet_av_person_navn"
        const val COL_BESKRIVELSE    = "opprettet_av_person_beskrivelse"
        const val COL_SENDES         = "sendes_til_jobbsoker_tidspunkt"
        const val COL_HTML           = "html_content"
        const val COL_OPPRETTET      = "opprettet_tidspunkt"
        const val COL_SIST_OPPDATERT = "sist_oppdatert_tidspunkt"
        const val COL_TREFF_UUID     = "treffId"
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
                VALUES (?,?,?,?,?,?,?,NOW())
                RETURNING ${T.COL_ID},
                          (SELECT id FROM rekrutteringstreff r WHERE r.db_id = $1) AS ${T.COL_TREFF_UUID},
                          ${T.COL_TITTEL}, ${T.COL_NAVIDENT}, ${T.COL_NAVN},
                          ${T.COL_BESKRIVELSE}, ${T.COL_SENDES},
                          ${T.COL_HTML}, ${T.COL_OPPRETTET}, ${T.COL_SIST_OPPDATERT}
            """

            c.prepareStatement(sql).use { ps ->
                ps.setLong (1, treffDbId)
                ps.setString(2, dto.tittel)
                ps.setString(3, dto.opprettetAvPersonNavident)
                ps.setString(4, dto.opprettetAvPersonNavn)
                ps.setString(5, dto.opprettetAvPersonBeskrivelse)
                dto.sendesTilJobbsokerTidspunkt?.let {
                    ps.setTimestamp(6, Timestamp.from(it.toInstant()))
                } ?: ps.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE)
                ps.setString(7, dto.htmlContent)

                ps.executeQuery().next()
                return@use ps.resultSet.toInnlegg()
            }
        }

    fun hentForTreff(treffId: TreffId): List<Innlegg> {
        val sql = """
            SELECT i.*, rt.id AS ${T.COL_TREFF_UUID}
              FROM ${T.TABLE} i
              JOIN rekrutteringstreff rt ON i.${T.COL_TREFF_DB_ID} = rt.db_id
             WHERE rt.id = ?
             ORDER BY i.${T.COL_OPPRETTET}
        """
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { ps ->
                ps.setObject(1, treffId.somUuid)
                ps.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs.toInnlegg() else null }.toList()
                }
            }
        }
    }

    fun hentById(innleggId: UUID): Innlegg? {
        val sql = """
            SELECT i.*, rt.id AS ${T.COL_TREFF_UUID}
              FROM ${T.TABLE} i
              JOIN rekrutteringstreff rt ON i.${T.COL_TREFF_DB_ID} = rt.db_id
             WHERE i.${T.COL_ID} = ?
        """
        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { ps ->
                ps.setObject(1, innleggId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toInnlegg() else null }
            }
        }
    }

    fun oppdater(innleggId: UUID, dto: OpprettInnleggRequestDto): Innlegg? {
        val sql = """
        UPDATE ${T.TABLE} AS i
           SET ${T.COL_TITTEL}         = ?,
               ${T.COL_NAVIDENT}       = ?,
               ${T.COL_NAVN}           = ?,
               ${T.COL_BESKRIVELSE}    = ?,
               ${T.COL_SENDES}         = ?,
               ${T.COL_HTML}           = ?,
               ${T.COL_SIST_OPPDATERT} = NOW()
         WHERE i.${T.COL_ID} = ?
     RETURNING i.${T.COL_ID},
              (SELECT r.id FROM rekrutteringstreff r WHERE r.db_id = i.${T.COL_TREFF_DB_ID}) AS ${T.COL_TREFF_UUID},
              i.${T.COL_TITTEL}, i.${T.COL_NAVIDENT}, i.${T.COL_NAVN},
              i.${T.COL_BESKRIVELSE}, i.${T.COL_SENDES},
              i.${T.COL_HTML}, i.${T.COL_OPPRETTET}, i.${T.COL_SIST_OPPDATERT};
    """.trimIndent()

        return dataSource.connection.use { c ->
            c.prepareStatement(sql).use { ps ->
                ps.setString(1, dto.tittel)
                ps.setString(2, dto.opprettetAvPersonNavident)
                ps.setString(3, dto.opprettetAvPersonNavn)
                ps.setString(4, dto.opprettetAvPersonBeskrivelse)
                dto.sendesTilJobbsokerTidspunkt?.let {
                    ps.setTimestamp(5, Timestamp.from(it.toInstant()))
                } ?: ps.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE)
                ps.setString(6, dto.htmlContent)
                ps.setObject(7, innleggId)

                ps.executeQuery().use { rs -> if (rs.next()) rs.toInnlegg() else null }
            }
        }
    }

    fun slett(innleggId: UUID): Boolean =
        dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM ${T.TABLE} WHERE ${T.COL_ID} = ?").use { ps ->
                ps.setObject(1, innleggId)
                ps.executeUpdate() > 0
            }
        }

    private fun java.sql.Connection.treffDbId(treff: TreffId): Long =
        prepareStatement("SELECT db_id FROM rekrutteringstreff WHERE id = ?").use { ps ->
            ps.setObject(1, treff.somUuid)
            ps.executeQuery().let { rs ->
                if (rs.next()) rs.getLong(1) else error("Treff $treff finnes ikke")
            }
        }

    private fun ResultSet.toInnlegg(): Innlegg =
        Innlegg(
            id                           = getObject(T.COL_ID, UUID::class.java),
            treffId                      = getObject(T.COL_TREFF_UUID, UUID::class.java),
            tittel                       = getString(T.COL_TITTEL),
            opprettetAvPersonNavident    = getString(T.COL_NAVIDENT),
            opprettetAvPersonNavn        = getString(T.COL_NAVN),
            opprettetAvPersonBeskrivelse = getString(T.COL_BESKRIVELSE),
            sendesTilJobbsokerTidspunkt  = getTimestamp(T.COL_SENDES)?.toInstant()?.atOslo(),
            htmlContent                  = getString(T.COL_HTML),
            opprettetTidspunkt           = getTimestamp(T.COL_OPPRETTET).toInstant().atOslo(),
            sistOppdatertTidspunkt       = getTimestamp(T.COL_SIST_OPPDATERT).toInstant().atOslo()
        )
}
