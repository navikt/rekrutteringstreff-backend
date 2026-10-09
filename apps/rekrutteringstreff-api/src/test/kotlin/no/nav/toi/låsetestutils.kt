package no.nav.toi

import org.assertj.core.api.Assertions.assertThat
import java.sql.Connection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Tar [lås] i en egen transaksjon og starter [operasjon] i en annen tråd. Når operasjonen står i kø
 * bak låsen, kjører [førFrigivelse] i den låste transaksjonen. Så committer transaksjonen, og
 * operasjonen får fortsette. Testen feiler hvis operasjonen ikke venter på låsen innen fem sekunder.
 */
fun <T> DataSource.medVentendeOperasjon(
    lås: (Connection) -> Unit,
    operasjon: () -> T,
    førFrigivelse: (Connection) -> Unit = {},
): T = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
    connection.use { connection ->
        connection.autoCommit = false
        lås(connection)
        val pid = connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT pg_backend_pid()").use { it.next(); it.getInt(1) }
        }
        val ventende = executor.submit<T> { operasjon() }
        try {
            assertThat(venterPåLåsFra(pid)).withFailMessage("Operasjonen ventet ikke på låsen").isTrue()
            førFrigivelse(connection)
            connection.commit()
        } finally {
            connection.rollback()
        }
        ventende.get(10, TimeUnit.SECONDS)
    }
}

private fun DataSource.venterPåLåsFra(pid: Int): Boolean {
    val frist = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < frist) {
        val venter = connection.use { connection ->
            connection.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))"
            ).use { stmt ->
                stmt.setInt(1, pid)
                stmt.executeQuery().use { it.next(); it.getBoolean(1) }
            }
        }
        if (venter) return true
        Thread.sleep(10)
    }
    return false
}
