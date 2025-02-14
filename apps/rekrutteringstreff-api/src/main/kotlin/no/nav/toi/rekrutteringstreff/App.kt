package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import org.flywaydb.core.Flyway
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

    val dataSource = createDataSource()
    kjørFlywayMigreringer(dataSource)
    App(
        8080, RekrutteringstreffRepository(dataSource), listOf(
            AuthenticationConfiguration(
                audience = getenv("AZURE_APP_CLIENT_ID"),
                issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI")
            )
        )
    ).start()
}

fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
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
    val env: Map<String?, String?> = System.getenv()!!

    val jdbcurl = env["NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_JDBC_URL"]!!
    val user = env["NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_USERNAME"]!!
    val pw = env["NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_PASSWORD"]!!

    return HikariConfig().apply {
        jdbcUrl = jdbcurl
        username = user
        password = pw
        maximumPoolSize = 2
        isAutoCommit = true
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        minimumIdle = 1
        driverClassName = "org.postgresql.Driver"
        initializationFailTimeout = 5000
        validate()
    }.let(::HikariDataSource)
}
