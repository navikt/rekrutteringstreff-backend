package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.openapi.BearerAuth
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.rekrutteringstreff.handleRekrutteringstreff
import org.flywaydb.core.Flyway
import java.time.Instant
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import javax.sql.DataSource

class App(
    private val port: Int,
    private val authConfigs: List<AuthenticationConfiguration>,
    private val dataSource: DataSource
) {
    lateinit var javalin: Javalin
        private set

    fun start() {
        kjørFlywayMigreringer(dataSource)
        javalin = Javalin.create { config ->
            configureOpenApi(config)
        }
        javalin.handleHealth()
        javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(authConfigs)
        javalin.handleRekrutteringstreff(RekrutteringstreffRepository(dataSource))
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
    App(
        8080,
        listOfNotNull(
            AuthenticationConfiguration(
                audience = getenv("AZURE_APP_CLIENT_ID"),
                issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI")
            ),
            if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp")
                AuthenticationConfiguration(
                    audience = "dev-gcp:toi:rekrutteringstreff-api",
                    issuer = "https://fakedings.intern.dev.nav.no/fake",
                    jwksUri = "https://fakedings.intern.dev.nav.no/fake/jwks",
                )
            else
                null
        ),
        dataSource
    ).start()
}

fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}

fun configureOpenApi(config: JavalinConfig) {
    val openApiPlugin = OpenApiPlugin { openApiConfig ->
        openApiConfig.withDefinitionConfiguration { _, definition ->
            definition.withInfo { info ->
                info.title = "Rekrutteringstreff API"
                info.version = "1.0.0"
            }
            definition.withSecurity { security ->
                security.withBearerAuth()
            }
        }
    }
    config.registerPlugin(openApiPlugin)
    config.registerPlugin(SwaggerPlugin { swaggerConfiguration ->
        swaggerConfiguration.validatorUrl = null
    })
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
