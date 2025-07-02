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
        minimumIdle = 1
        maximumPoolSize = 7
        driverClassName = "org.postgresql.Driver"
        initializationFailTimeout = 5000
        username = user
        password = pw
        metricRegistry = meterRegistry
        validate()
    }.let(::HikariDataSource)
}

private fun Map<String, String>.variable(felt: String) = this[felt] ?: error("$felt er ikke angitt")