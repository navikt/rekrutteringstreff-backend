package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import java.time.Instant
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import javax.sql.DataSource

class App(
    private val port: Int,
    private val repo: RekrutteringstreffRepository,
    private val authConfigs: List<AuthenticationConfiguration>
) {
    lateinit var javalin: Javalin
        private set

    fun start() {
        javalin = Javalin.create()
        javalin.handleHealth()
        javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(authConfigs)
        javalin.handleRekrutteringstreff(repo)
        javalin.start(port)
    }

    fun close() {
        if (::javalin.isInitialized) {
            javalin.stop()
        }
    }
}

fun main() {
    App(
        8080, RekrutteringstreffRepository(createDataSource()), listOf(
            AuthenticationConfiguration(
                audience = getenv("AZURE_APP_CLIENT_ID"),
                issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI")
            )
        )
    ).start()
}

/**
 * Tidspunkt uten nanosekunder, for å unngå at to like tidspunkter blir ulike pga at database og Microsoft Windws håndterer nanos annerledes enn Mac og Linux.
 */
fun nowOslo(): ZonedDateTime = ZonedDateTime.now().atOslo()

fun ZonedDateTime.atOslo(): ZonedDateTime = this.withZoneSameInstant(of("Europe/Oslo")).truncatedTo(MILLIS)

fun Instant.atOslo(): ZonedDateTime = this.atZone(of("Europe/Oslo")).truncatedTo(MILLIS)


private fun getenv(key: String) =
    System.getenv(key) ?: throw IllegalArgumentException("Det finnes ingen system-variabel ved navn $key")

private fun createDataSource(): DataSource {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost:5432/rekrrutteringstreffdb"
        username = "todo"
        password = "todo"
        maximumPoolSize = 3
        isAutoCommit = true
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    }
    return HikariDataSource(config)
}
