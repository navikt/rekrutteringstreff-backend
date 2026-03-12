package no.nav.toi

import java.sql.SQLException
import javax.sql.DataSource
import kotlin.use


class IsAliveRepository(
    private val dataSource: DataSource,
) {

    fun fårKontaktMedDatabasen(): Boolean {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT 1").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            log.error("Databasesjekk ok")
                            return true
                        }
                    }
                }
            }
            // Fikk ikke noe resultat tilbake
            log.error("Fikk ikke svar fra databasen")
            return false
        } catch (e: SQLException) {
            log.error("Feil ved kontakt med database: ${e.message}", e)
            return false
        }
    }
}
