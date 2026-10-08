package no.nav.toi

import java.sql.Connection
import javax.sql.DataSource

/**
 * Isolasjonsnivå for både produksjonspoolen og TestDatabase, så testene oppfører seg som produksjon.
 * Under READ COMMITTED ser transaksjoner endringer som ble committet mens de ventet på låsen.
 */
const val READ_COMMITTED = "TRANSACTION_READ_COMMITTED"

fun <T> DataSource.executeInTransaction(block: (Connection) -> T): T =
    runInTransaction(readOnly = false, block)

/**
 * Lesetransaksjon der alle spørringene ser samme øyeblikksbilde, for eksempel totalt antall og én side.
 * Transaksjonen kan ikke skrive, og får derfor aldri 40001 under REPEATABLE READ.
 */
fun <T> DataSource.executeInReadOnlyTransaction(block: (Connection) -> T): T =
    runInTransaction(readOnly = true, block)

private fun <T> DataSource.runInTransaction(readOnly: Boolean, block: (Connection) -> T): T {
    this.connection.use { c ->
        val originalIsolation = c.transactionIsolation
        if (readOnly) {
            c.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            c.isReadOnly = true
        }
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
            if (readOnly) {
                c.isReadOnly = false
                c.transactionIsolation = originalIsolation
            }
        }
    }
}
