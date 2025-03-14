package no.nav.toi

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.handleArbeidsgiver
import no.nav.toi.rekrutteringstreff.handleRekrutteringstreff
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
    private lateinit var javalin: Javalin

    fun start() {
        log.info("Starter app")
        kjørFlywayMigreringer(dataSource)
        log.info("Har kjørt flyway migreringer")
        javalin = Javalin.create { config ->
            configureOpenApi(config)
            jacksonObjectMapper().apply {
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
            log.info("Javalin opprettet")
        }
        javalin.handleHealth()
        javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(authConfigs)
        javalin.handleRekrutteringstreff(RekrutteringstreffRepository(dataSource))
        javalin.handleArbeidsgiver(ArbeidsgiverRepository(dataSource))
        javalin.start(port)
    }

    fun close() {
        if (::javalin.isInitialized) {
            javalin.stop()
        }
    }
}

private val log = noClassLogger()

fun main() {
    log.info("Starter App")
    secure(log).info("Starter App. Dette er ment å logges til Securelogs. Hvis du ser dette i den ordinære apploggen er noe galt, og sensitive data kan havne i feil logg.")
    val dataSource = createDataSource()
    log.info("Datasource opprettet")

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

private fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}

private fun configureOpenApi(config: JavalinConfig) {
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


private fun getenv(key: String): String =
    System.getenv(key) ?: throw NullPointerException("Det finnes ingen miljøvariabel med navn [$key]")

private fun createDataSource(): DataSource {
    val jdbcurl = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_JDBC_URL")
    val user = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_USERNAME")
    val pw = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_PASSWORD")

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
