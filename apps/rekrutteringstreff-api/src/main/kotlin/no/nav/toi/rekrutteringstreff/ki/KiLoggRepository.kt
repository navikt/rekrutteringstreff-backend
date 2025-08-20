package no.nav.toi.rekrutteringstreff.ki
import no.nav.toi.JacksonConfig
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class KiLoggRepository(private val dataSource: DataSource) {
    private val mapper = JacksonConfig.mapper

    fun insert(entry: KiLoggInsert): UUID =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO $TABELL
                    ($COL_TREFF_DB_ID, $COL_FELT_TYPE, $COL_SPØRRING_FRA_FRONTEND, $COL_SPØRRING_FILTRERT, $COL_SYSTEMPROMPT, $COL_EKSTRA_PARAMETRE,
                     $COL_BRYTER_RETNINGSLINJER, $COL_BEGRUNNELSE, $COL_KI_NAVN, $COL_KI_VERSJON, $COL_SVARTID_MS)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING $COL_ID
                """.trimIndent()
            ).use { ps ->
                var i = 0
                ps.setLong(++i, entry.treffDbId)
                ps.setString(++i, entry.feltType)
                ps.setString(++i, entry.spørringFraFrontend)
                ps.setString(++i, entry.spørringFiltrert)
                ps.setString(++i, entry.systemprompt)

                if (entry.ekstraParametre == null) {
                    ps.setNull(++i, Types.OTHER)
                } else {
                    val json = mapper.writeValueAsString(entry.ekstraParametre)
                    val pg = PGobject().apply { type = "jsonb"; value = json }
                    ps.setObject(++i, pg)
                }

                ps.setBoolean(++i, entry.bryterRetningslinjer)
                ps.setString(++i, entry.begrunnelse)
                ps.setString(++i, entry.kiNavn)
                ps.setString(++i, entry.kiVersjon)
                ps.setInt(++i, entry.svartidMs)

                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getObject(1, UUID::class.java)
                }
            }
        }

    fun setLagret(id: UUID, lagret: Boolean): Int =
        dataSource.connection.use { c ->
            c.prepareStatement(
                "UPDATE $TABELL SET $COL_LAGRET=? WHERE $COL_ID = ?"
            ).use { ps ->
                ps.setBoolean(1, lagret)
                ps.setObject(2, id)
                ps.executeUpdate()
            }
        }

    fun setManuellKontroll(
        id: UUID,
        bryterRetningslinjer: Boolean,
        utfortAv: String,
        tidspunkt: ZonedDateTime
    ): Int =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                UPDATE $TABELL
                SET $COL_MAN_KONTROLL_BRYTER=?, $COL_MAN_KONTROLL_UTFORT_AV=?, $COL_MAN_KONTROLL_TIDSPUNKT=?
                WHERE $COL_ID=?
                """.trimIndent()
            ).use { ps ->
                ps.setBoolean(1, bryterRetningslinjer)
                ps.setString(2, utfortAv)
                ps.setTimestamp(3, Timestamp.from(tidspunkt.toInstant()))
                ps.setObject(4, id)
                ps.executeUpdate()
            }
        }

    fun findById(id: UUID): KiLoggRow? =
        dataSource.connection.use { c ->
            c.prepareStatement(
                "SELECT * FROM $TABELL WHERE $COL_ID = ?"
            ).use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.toRow() else null
                }
            }
        }

    fun list(treffDbId: Long?, feltType: String?, limit: Int, offset: Int): List<KiLoggRow> =
        dataSource.connection.use { c ->
            val sql = """
                SELECT * FROM $TABELL
                WHERE ($COL_TREFF_DB_ID = COALESCE(?, $COL_TREFF_DB_ID))
                  AND ($COL_FELT_TYPE = COALESCE(?, $COL_FELT_TYPE))
                ORDER BY $COL_OPPRETTET DESC
                LIMIT ? OFFSET ?
            """.trimIndent()

            c.prepareStatement(sql).use { ps ->
                var i = 0
                if (treffDbId == null) ps.setNull(++i, Types.BIGINT) else ps.setLong(++i, treffDbId)
                if (feltType.isNullOrBlank()) ps.setNull(++i, Types.VARCHAR) else ps.setString(++i, feltType)
                ps.setInt(++i, limit)
                ps.setInt(++i, offset)

                ps.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs.toRow() else null }.toList()
                }
            }
        }

    private fun ResultSet.toRow(): KiLoggRow =
        KiLoggRow(
            id = getObject(COL_ID, UUID::class.java),
            opprettetTidspunkt = getTimestamp(COL_OPPRETTET).toInstant().atZone(ZoneOffset.UTC),
            treffDbId = getLong(COL_TREFF_DB_ID),
            feltType = getString(COL_FELT_TYPE),
            spørringFraFrontend = getString(COL_SPØRRING_FRA_FRONTEND),
            spørringFiltrert = getString(COL_SPØRRING_FILTRERT),
            systemprompt = getString(COL_SYSTEMPROMPT),
            ekstraParametreJson = getString(COL_EKSTRA_PARAMETRE),
            bryterRetningslinjer = getBoolean(COL_BRYTER_RETNINGSLINJER),
            begrunnelse = getString(COL_BEGRUNNELSE),
            kiNavn = getString(COL_KI_NAVN),
            kiVersjon = getString(COL_KI_VERSJON),
            svartidMs = getInt(COL_SVARTID_MS),
            lagret = getBoolean(COL_LAGRET),
            manuellKontrollBryterRetningslinjer = getObject(COL_MAN_KONTROLL_BRYTER) as Boolean?,
            manuellKontrollUtfortAv = getString(COL_MAN_KONTROLL_UTFORT_AV),
            manuellKontrollTidspunkt = getTimestamp(COL_MAN_KONTROLL_TIDSPUNKT)?.toInstant()?.atZone(ZoneOffset.UTC)
        )

    companion object {
        private const val TABELL = "ki_spørring_logg"

        private const val COL_ID = "id"
        private const val COL_OPPRETTET = "opprettet_tidspunkt"
        private const val COL_TREFF_DB_ID = "treff_db_id"
        private const val COL_FELT_TYPE = "felt_type"

        private const val COL_SPØRRING_FRA_FRONTEND = "spørring_fra_frontend"
        private const val COL_SPØRRING_FILTRERT = "spørring_filtrert"
        private const val COL_SYSTEMPROMPT = "systemprompt"
        private const val COL_EKSTRA_PARAMETRE = "ekstra_parametre"

        private const val COL_BRYTER_RETNINGSLINJER = "bryter_retningslinjer"
        private const val COL_BEGRUNNELSE = "begrunnelse"

        private const val COL_KI_NAVN = "ki_navn"
        private const val COL_KI_VERSJON = "ki_versjon"

        private const val COL_SVARTID_MS = "svartid_ms"
        private const val COL_LAGRET = "lagret"

        private const val COL_MAN_KONTROLL_BRYTER = "manuell_kontroll_bryter_retningslinjer"
        private const val COL_MAN_KONTROLL_UTFORT_AV = "manuell_kontroll_utført_av"
        private const val COL_MAN_KONTROLL_TIDSPUNKT = "manuell_kontroll_tidspunkt"
    }
}

data class KiLoggInsert(
    val treffDbId: Long,
    val feltType: String, // 'tittel' | 'innlegg'
    val spørringFraFrontend: String,
    val spørringFiltrert: String,
    val systemprompt: String?,
    val ekstraParametre: Map<String, Any?>?,
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String?,
    val kiNavn: String,
    val kiVersjon: String,
    val svartidMs: Int
)

data class KiLoggRow(
    val id: UUID,
    val opprettetTidspunkt: ZonedDateTime,
    val treffDbId: Long,
    val feltType: String,
    val spørringFraFrontend: String,
    val spørringFiltrert: String,
    val systemprompt: String?,
    val ekstraParametreJson: String?,
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String?,
    val kiNavn: String,
    val kiVersjon: String,
    val svartidMs: Int,
    val lagret: Boolean,
    val manuellKontrollBryterRetningslinjer: Boolean?,
    val manuellKontrollUtfortAv: String?,
    val manuellKontrollTidspunkt: ZonedDateTime?
)