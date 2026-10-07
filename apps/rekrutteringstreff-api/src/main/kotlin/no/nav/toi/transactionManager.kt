package no.nav.toi

import java.sql.Connection
import javax.sql.DataSource

/**
 * Transaksjon for skrivinger som tar radlås. Under poolens REPEATABLE READ avbryter PostgreSQL med
 * 40001 når raden er endret mens vi ventet på låsen. READ COMMITTED lar neste spørring se endringen.
 */
fun <T> DataSource.executeInLockingTransaction(block: (Connection) -> T): T =
    executeInTransaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED, block = block)

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
