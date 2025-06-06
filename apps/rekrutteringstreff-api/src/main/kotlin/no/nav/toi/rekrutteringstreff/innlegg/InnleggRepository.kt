package no.nav.toi.rekrutteringstreff.innlegg

import no.nav.toi.atOslo
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource
import kotlin.io.use
import kotlin.toString

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

    fun opprett(treffId: TreffId, dto: OpprettInnleggRequestDto, navIdent: String): Innlegg {
        val innleggId = UUID.randomUUID()
        return dataSource.connection.use { c ->
            val treffDbId = c.treffDbId(treffId)
            val sql = """
                INSERT INTO innlegg (
                    id, treff_db_id, tittel, opprettet_av_person_navident,
                    opprettet_av_person_navn, opprettet_av_person_beskrivelse, html_content
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id, (SELECT r.id FROM rekrutteringstreff r WHERE r.db_id = innlegg.treff_db_id) AS treffId,
                          tittel, opprettet_av_person_navident, opprettet_av_person_navn,
                          opprettet_av_person_beskrivelse, sendes_til_jobbsoker_tidspunkt, -- add this
                          html_content, opprettet_tidspunkt, sist_oppdatert_tidspunkt
            """.trimIndent()
            c.prepareStatement(sql).use { ps ->
                ps.setObject(1, innleggId)
                ps.setLong(2, treffDbId)
                ps.setString(3, dto.tittel)
                ps.setString(4, navIdent)
                ps.setString(5, dto.opprettetAvPersonNavn)
                ps.setString(6, dto.opprettetAvPersonBeskrivelse)
                ps.setString(7, dto.htmlContent)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.toInnlegg() else error("Insert failed")
                }
            }
        }
    }

    fun oppdater(innleggId: UUID, treffId: TreffId, dto: OpprettInnleggRequestDto): Innlegg {
        return dataSource.connection.use { c ->
            val treffDbId = c.treffDbId(treffId)
            val sql = """
            UPDATE innlegg SET
                tittel = ?,
                opprettet_av_person_navn = ?,
                opprettet_av_person_beskrivelse = ?,
                sendes_til_jobbsoker_tidspunkt = ?,
                html_content = ?,
                sist_oppdatert_tidspunkt = NOW()
            WHERE id = ? AND treff_db_id = ?
            RETURNING id, (SELECT r.id FROM rekrutteringstreff r WHERE r.db_id = innlegg.treff_db_id) AS treffId,
                      tittel, opprettet_av_person_navident, opprettet_av_person_navn,
                      opprettet_av_person_beskrivelse, sendes_til_jobbsoker_tidspunkt,
                      html_content, opprettet_tidspunkt, sist_oppdatert_tidspunkt
        """.trimIndent()
            c.prepareStatement(sql).use { ps ->
                ps.setString(1, dto.tittel)
                ps.setString(2, dto.opprettetAvPersonNavn)
                ps.setString(3, dto.opprettetAvPersonBeskrivelse)
                dto.sendesTilJobbsokerTidspunkt?.let {
                    ps.setTimestamp(4, java.sql.Timestamp.from(it.toInstant()))
                } ?: ps.setNull(4, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                ps.setString(5, dto.htmlContent)
                ps.setObject(6, innleggId)
                ps.setLong(7, treffDbId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.toInnlegg() else error("Update failed or not found")
                }
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
            treffId                      = getObject(T.COL_TREFF_UUID, UUID::class.java), // From subselect alias
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