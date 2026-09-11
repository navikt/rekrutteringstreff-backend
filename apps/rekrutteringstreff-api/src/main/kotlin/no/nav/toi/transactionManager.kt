package no.nav.toi

import java.sql.Connection
import javax.sql.DataSource

fun <T> DataSource.executeInTransaction(
    transactionIsolation: Int? = null,
    block: (Connection) -> T,
): T {
    this.connection.use { c ->
        val originalIsolation = c.transactionIsolation
        if (transactionIsolation != null) c.transactionIsolation = transactionIsolation
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
            if (transactionIsolation != null) c.transactionIsolation = originalIsolation
        }
    }
}
