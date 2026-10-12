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

/**
 * En transaksjon inne i en annen åpner en ny tilkobling. Venter den indre på en lås som den ytre holder,
 * henger kallet uten at PostgreSQL oppdager det. Vi stopper derfor nestede transaksjoner med en gang.
 */
private val transactionIsOpen = ThreadLocal.withInitial { false }

private fun <T> DataSource.runInTransaction(readOnly: Boolean, block: (Connection) -> T): T {
    check(!transactionIsOpen.get()) {
        "Transaksjonen startes inne i en annen transaksjon. Send connection videre i stedet."
    }
    transactionIsOpen.set(true)
    try {
        return executeTransaction(readOnly, block)
    } finally {
        transactionIsOpen.set(false)
    }
}

private fun <T> DataSource.executeTransaction(readOnly: Boolean, block: (Connection) -> T): T {
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
        } catch (e: Throwable) {
            try {
                c.rollback()
            } catch (rollbackError: Exception) {
                e.addSuppressed(rollbackError)
            }
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
