package no.nav.toi

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

class DatabaseConfig(
    env: Map<String, String>,
    private val meterRegistry: PrometheusMeterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
) {
    private val host = env.variable("NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_HOST")
    private val port = env.variable("NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PORT")
    private val database = env.variable("NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_DATABASE")
    private val user = env.variable("NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_USERNAME")
    private val pw = env.variable("NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PASSWORD")

    private val sslcert = env["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_SSLCERT"]
    private val sslkey = env["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_SSLKEY_PK8"]
    private val sslrootcert = env["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_SSLROOTCERT"]
    private val sslmode = env["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_SSLMODE"]
    private val sslsuffix = if (sslcert == null ) "" else
        "?ssl=on&sslrootcert=$sslrootcert&sslcert=$sslcert&sslmode=$sslmode&sslkey=$sslkey"

    fun lagDatasource() = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://$host:$port/$database$sslsuffix"
        username = user
        password = pw
        driverClassName = "org.postgresql.Driver"  // PostgreSQL driver
        maximumPoolSize = 10  // Maks 10 samtidige tilkoblinger
        minimumIdle = 2  // Behold minst 2 ledige tilkoblinger
        isAutoCommit = true  // Auto-commit hver SQL-operasjon
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"  // PostgreSQL standard
        initializationFailTimeout = 10_000  // Vent maks 10 sekunder ved oppstart feil
        connectionTimeout = 30_000  // Vent maks 30 sekunder på ny tilkobling
        idleTimeout = 600_000  // 10 minutter - lukk ledige tilkoblinger etter dette
        maxLifetime = 1_800_000  // 30 minutter - lukk og erstatt tilkoblinger etter dette
        leakDetectionThreshold = 60_000  // Logg advarsel hvis tilkobling holdes > 60 sekunder
        poolName = "RekrutteringAktivitetskortPool"  // Navn for logging/debugging
        metricRegistry = meterRegistry
        validate()
    }.let(::HikariDataSource)
}

private fun Map<String, String>.variable(felt: String) = this[felt] ?: error("$felt er ikke angitt")