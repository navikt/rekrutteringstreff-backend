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
        const val COL_ID             = "id"
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
        const val COL_XMAX           = "xmax"
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

    // Upsert: used for both create and update
    fun oppdater(innleggId: UUID, treffId: TreffId, dto: OpprettInnleggRequestDto): Pair<Innlegg, Boolean> {
        return dataSource.connection.use { c ->
            val treffDbId = c.treffDbId(treffId)
            val sql = """
                INSERT INTO ${T.TABLE} (
                    ${T.COL_ID}, ${T.COL_TREFF_DB_ID}, ${T.COL_TITTEL}, ${T.COL_NAVIDENT},
                    ${T.COL_NAVN}, ${T.COL_BESKRIVELSE}, ${T.COL_SENDES}, ${T.COL_HTML}
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (${T.COL_ID}) DO UPDATE
                SET ${T.COL_TITTEL}         = EXCLUDED.${T.COL_TITTEL},
                    ${T.COL_NAVIDENT}       = EXCLUDED.${T.COL_NAVIDENT},
                    ${T.COL_NAVN}           = EXCLUDED.${T.COL_NAVN},
                    ${T.COL_BESKRIVELSE}    = EXCLUDED.${T.COL_BESKRIVELSE},
                    ${T.COL_SENDES}         = EXCLUDED.${T.COL_SENDES},
                    ${T.COL_HTML}           = EXCLUDED.${T.COL_HTML},
                    ${T.COL_SIST_OPPDATERT} = NOW()
                RETURNING ${T.COL_ID},
                          (SELECT r.id FROM rekrutteringstreff r WHERE r.db_id = ${T.TABLE}.${T.COL_TREFF_DB_ID}) AS ${T.COL_TREFF_UUID},
                          ${T.COL_TITTEL}, ${T.COL_NAVIDENT}, ${T.COL_NAVN},
                          ${T.COL_BESKRIVELSE}, ${T.COL_SENDES},
                          ${T.COL_HTML}, ${T.COL_OPPRETTET}, ${T.COL_SIST_OPPDATERT},
                          ${T.COL_XMAX}
            """.trimIndent()

            c.prepareStatement(sql).use { ps ->
                ps.setObject(1, innleggId)
                ps.setLong(2, treffDbId)
                ps.setString(3, dto.tittel)
                ps.setString(4, dto.opprettetAvPersonNavident)
                ps.setString(5, dto.opprettetAvPersonNavn)
                ps.setString(6, dto.opprettetAvPersonBeskrivelse)
                dto.sendesTilJobbsokerTidspunkt?.let {
                    ps.setTimestamp(7, Timestamp.from(it.toInstant()))
                } ?: ps.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE)
                ps.setString(8, dto.htmlContent)

                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val innlegg = rs.toInnlegg()
                        val xmax = rs.getObject(T.COL_XMAX)
                        Pair(innlegg, xmax.toString() == "0")
                    } else {
                        error("Upsert operation for innleggId $innleggId did not return a row, or treffId $treffId became invalid.")
                    }
                }
            }
        }
    }

    // For POST: generates a new id and calls upsert
    fun opprett(treffId: TreffId, dto: OpprettInnleggRequestDto): Innlegg {
        val innleggId = UUID.randomUUID()
        return oppdater(innleggId, treffId, dto).first
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