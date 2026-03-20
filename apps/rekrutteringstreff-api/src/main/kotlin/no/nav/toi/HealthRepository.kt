package no.nav.toi

import java.sql.SQLException
import javax.sql.DataSource

class HealthRepository(
    private val dataSource: DataSource,
) {
    fun fårKontaktMedDatabasen(): Boolean {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT 1").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
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
