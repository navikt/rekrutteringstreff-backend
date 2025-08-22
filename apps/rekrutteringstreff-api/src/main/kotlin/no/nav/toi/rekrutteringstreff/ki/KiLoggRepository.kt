package no.nav.toi.rekrutteringstreff.ki

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class KiLoggRepository(private val dataSource: DataSource) {

    fun insert(i: KiLoggInsert): UUID =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
            insert into ki_spørring_logg(
                id,
                opprettet_tidspunkt,
                treff_id,
                felt_type,
                spørring_fra_frontend,
                spørring_filtrert,
                systemprompt,
                ekstra_parametre,
                bryter_retningslinjer,
                begrunnelse,
                ki_navn,
                ki_versjon,
                svartid_ms,
                lagret
            ) values (?, now(), ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, false)
            returning id
            """.trimIndent()
            ).use { ps ->
                val id = UUID.randomUUID()
                var p = 0
                ps.setObject(++p, id)
                ps.setObject(++p, i.treffId)
                ps.setString(++p, i.feltType)
                ps.setString(++p, i.spørringFraFrontend)
                ps.setString(++p, i.spørringFiltrert)
                ps.setString(++p, i.systemprompt)
                ps.setString(++p, i.ekstraParametreJson)
                ps.setBoolean(++p, i.bryterRetningslinjer)
                ps.setString(++p, i.begrunnelse)
                ps.setString(++p, i.kiNavn)
                ps.setString(++p, i.kiVersjon)
                ps.setInt(++p, i.svartidMs)

                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getObject(1, UUID::class.java)
                }
                id
            }
        }

    fun setLagret(id: UUID, lagret: Boolean): Int =
        dataSource.connection.use { c ->
            c.prepareStatement("update ki_spørring_logg set lagret = ? where id = ?").use { ps ->
                ps.setBoolean(1, lagret)
                ps.setObject(2, id)
                ps.executeUpdate()
            }
        }

    fun setManuellKontroll(id: UUID, bryterRetningslinjer: Boolean, utfortAv: String, tidspunkt: ZonedDateTime): Int =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                update ki_spørring_logg set
                    manuell_kontroll_bryter_retningslinjer = ?,
                    manuell_kontroll_utført_av = ?,
                    manuell_kontroll_tidspunkt = ?
                where id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setBoolean(1, bryterRetningslinjer)
                ps.setString(2, utfortAv)
                ps.setTimestamp(3, Timestamp.from(tidspunkt.toInstant()))
                ps.setObject(4, id)
                ps.executeUpdate()
            }
        }

    fun list(treffId: UUID?, feltType: String?, limit: Int, offset: Int): List<KiLoggMedTreff> =
        dataSource.connection.use { c ->
            val cond = mutableListOf<String>()
            val params = mutableListOf<Any>()
            if (treffId != null) { cond += "k.treff_id = ?"; params += treffId }
            if (feltType != null) { cond += "k.felt_type = ?"; params += feltType }
            val whereClause = if (cond.isNotEmpty()) "where ${cond.joinToString(" and ")}" else ""
            val sql = """
            select
                k.*,
                r.tittel as treff_tittel
            from ki_spørring_logg k
            left join rekrutteringstreff r on r.id = k.treff_id
            $whereClause
            order by k.opprettet_tidspunkt desc, k.db_id desc
            limit ? offset ?
            """.trimIndent()

            c.prepareStatement(sql).use { ps ->
                var idx = 0
                params.forEach { ps.setObject(++idx, it) }
                ps.setInt(++idx, limit)
                ps.setInt(++idx, offset)
                ps.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) fromResultSet(rs) else null }.toList()
                }
            }
        }

    fun findById(id: UUID): KiLoggMedTreff? =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                select
                    k.*,
                    r.tittel as treff_tittel
                from ki_spørring_logg k
                left join rekrutteringstreff r on r.id = k.treff_id
                where k.id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) fromResultSet(rs) else null }
            }
        }

    private fun fromResultSet(rs: ResultSet) = KiLoggMedTreff(
        id = rs.getObject("id", UUID::class.java),
        opprettetTidspunkt = rs.getTimestamp("opprettet_tidspunkt").toInstant().atZone(ZonedDateTime.now().zone),
        treffId = rs.getObject("treff_id", UUID::class.java),
        tittel = rs.getString("treff_tittel"),
        feltType = rs.getString("felt_type"),
        spørringFraFrontend = rs.getString("spørring_fra_frontend"),
        spørringFiltrert = rs.getString("spørring_filtrert"),
        systemprompt = rs.getString("systemprompt"),
        ekstraParametreJson = rs.getString("ekstra_parametre"),
        bryterRetningslinjer = rs.getBoolean("bryter_retningslinjer"),
        begrunnelse = rs.getString("begrunnelse"),
        kiNavn = rs.getString("ki_navn"),
        kiVersjon = rs.getString("ki_versjon"),
        svartidMs = rs.getInt("svartid_ms"),
        lagret = rs.getBoolean("lagret"),
        manuellKontrollBryterRetningslinjer = rs.getObject("manuell_kontroll_bryter_retningslinjer") as? Boolean,
        manuellKontrollUtfortAv = rs.getString("manuell_kontroll_utført_av"),
        manuellKontrollTidspunkt = rs.getTimestamp("manuell_kontroll_tidspunkt")?.toInstant()?.atZone(ZonedDateTime.now().zone)
    )
}

data class KiLoggInsert(
    val treffId: UUID?,
    val feltType: String,
    val spørringFraFrontend: String,
    val spørringFiltrert: String,
    val systemprompt: String?,
    val ekstraParametreJson: String?,
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String?,
    val kiNavn: String,
    val kiVersjon: String,
    val svartidMs: Int
)

data class KiLoggMedTreff(
    val id: UUID,
    val opprettetTidspunkt: ZonedDateTime,
    val treffId: UUID?,
    val tittel: String?,
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
