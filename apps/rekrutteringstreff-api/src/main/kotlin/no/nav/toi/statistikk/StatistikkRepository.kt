package no.nav.toi.statistikk

import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource

data class FåttJobbStatistikk(
    val totalt: Int,
    val under30år: Int,
    val innsatsgruppeIkkeStandard: Int,
)

class StatistikkRepository(private val dataSource: DataSource) {

    fun hentFåttJobbStatistikk(navKontor: String, fraOgMed: LocalDate, tilOgMed: LocalDate): FåttJobbStatistikk {
        val sql = """
            SELECT
                COUNT(*) AS totalt,
                COUNT(*) FILTER (WHERE j.alder IS NOT NULL AND j.alder < 30) AS under30,
                COUNT(*) FILTER (WHERE j.innsatsgruppe IN ($innsatsgrupperIkkeStandardSql)) AS ikke_standard
            FROM formidling f
            JOIN jobbsoker j ON j.jobbsoker_id = f.jobbsoker_id
            WHERE f.slettet_tidspunkt IS NULL
              AND f.utfall_sendt_tidspunkt IS NOT NULL
              AND f.utfall_sendt_tidspunkt >= ?
              AND f.utfall_sendt_tidspunkt < ?
              AND f.kontornummer = ?
        """.trimIndent()

        val fra = fraOgMed.atStartOfDay(OSLO).toOffsetDateTime()
        val til = tilOgMed.plusDays(1).atStartOfDay(OSLO).toOffsetDateTime()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, fra)
                stmt.setObject(2, til)
                stmt.setString(3, navKontor)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    return FåttJobbStatistikk(
                        totalt = rs.getInt("totalt"),
                        under30år = rs.getInt("under30"),
                        innsatsgruppeIkkeStandard = rs.getInt("ikke_standard"),
                    )
                }
            }
        }
    }

    companion object {
        private val OSLO = ZoneId.of("Europe/Oslo")

        private val innsatsgrupperIkkeStandard = listOf(
            "BATT",
            "BFORM",
            "VARIG",
            "SPESIELT_TILPASSET_INNSATS",
            "SITUASJONSBESTEMT_INNSATS",
            "VARIG_TILPASSET_INNSATS",
            "GRADERT_VARIG_TILPASSET_INNSATS",
        )

        private val innsatsgrupperIkkeStandardSql =
            innsatsgrupperIkkeStandard.joinToString(", ") { "'$it'" }
    }
}
