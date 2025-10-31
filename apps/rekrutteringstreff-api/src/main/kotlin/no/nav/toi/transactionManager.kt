package no.nav.toi

import java.sql.Connection
import javax.sql.DataSource

fun <T> DataSource.executeInTransaction(block: (Connection) -> T): T {
    this.connection.use { c ->
        c.autoCommit = false
        try {
            val result = block(c)
            c.commit()
            return result
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }
}
