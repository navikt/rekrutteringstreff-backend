package no.nav.toi

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.json.JavalinJackson
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.toi.ExceptionMapping.exceptionMapping
import no.nav.toi.jobbsoker.MinsideVarselSvarLytter
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortFeilLytter
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovLytter
import no.nav.toi.jobbsoker.synlighet.SynlighetsLytter
import org.flywaydb.core.Flyway
import java.time.Instant
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import javax.sql.DataSource
import kotlin.system.exitProcess

class App(
    private val ctx: ApplicationContext,
    private val port: Int = 8080,
) {
    private lateinit var javalin: Javalin

    fun start() {
        startJavalin()
        startSchedulere()
        startRR()
        log.info("Hele applikasjonen er startet og klar til å motta forespørsler.")
    }

    private fun startJavalin() {
        log.info("Starting Javalin on port $port")
        kjørFlywayMigreringer(ctx.dataSource)

        javalin = Javalin.create { config ->
            config.jsonMapper(JavalinJackson(JacksonConfig.mapper))
            configureOpenApi(config)

            config.routes.exceptionMapping()

            ctx.healthController.register(config.routes)
            config.routes.leggTilAutensieringPåRekrutteringstreffEndepunkt(
                authConfigs = ctx.authConfigs,
                rolleUuidSpesifikasjon = ctx.rolleUuidSpesifikasjon,
                modiaKlient = ctx.modiaKlient,
                pilotkontorer = ctx.pilotkontorer
            )

            ctx.sokController.register(config.routes)
            ctx.arbeidsgiverController.register(config.routes)
            ctx.rekrutteringstreffController.register(config.routes)
            ctx.innleggController.register(config.routes)
            ctx.eierController.register(config.routes)
            ctx.jobbsøkerController.register(config.routes)
            ctx.jobbsøkerInnloggetBorgerController.register(config.routes)
            ctx.jobbsøkerOutboundController.register(config.routes)
            ctx.kiController.register(config.routes)
        }

        javalin.start(port)
    }

    private fun startSchedulere() {
        log.info("Starting schedulers")
        ctx.jobbsøkerhendelserScheduler.start()
        ctx.synlighetsBehovScheduler.start()
        ctx.rekrutteringstreffOpprydningScheduler.start()
        ctx.rekrutteringstreffScheduler.start()
    }

    fun startRR() {
        log.info("Starting RapidsConnection")
        AktivitetskortFeilLytter(ctx.rapidsConnection, ctx.jobbsøkerService)
        MinsideVarselSvarLytter(ctx.rapidsConnection, ctx.jobbsøkerService, JacksonConfig.mapper)
        SynlighetsLytter(ctx.rapidsConnection, ctx.jobbsøkerService)
        SynlighetsBehovLytter(ctx.rapidsConnection, ctx.jobbsøkerService)
        Thread {
            try {
                ctx.rapidsConnection.start()
            } catch (e: Exception) {
                log.error("RapidsConnection feilet, avslutter applikasjonen", e)
                close()
                exitProcess(1)
            }
        }.start()
    }

    fun close() {
        log.info("Shutting down application")
        ctx.jobbsøkerhendelserScheduler.stop()
        ctx.synlighetsBehovScheduler.stop()
        ctx.rekrutteringstreffOpprydningScheduler.stop()
        ctx.rekrutteringstreffScheduler.stop()
        if (::javalin.isInitialized) javalin.stop()
        ctx.rapidsConnection.stop()
        (ctx.dataSource as? HikariDataSource)?.close()
        log.info("Application shutdown complete")
    }
}

fun main() {
    val dataSource = createDataSource()
    val rapidsConnection = RapidApplication.create(System.getenv(), builder = { withHttpPort(9000) })
    val ctx = ApplicationContext(dataSource, rapidsConnection)
    val app = App(ctx)
    Runtime.getRuntime().addShutdownHook(Thread { app.close() })
    app.start()
}


private fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}

private fun configureOpenApi(config: JavalinConfig) {
    val openApiPlugin = OpenApiPlugin { openApiConfig ->
        openApiConfig.withDefinitionConfiguration { _, schema ->
            schema.info { info ->
                info.title("Rekrutteringstreff API")
                info.version("1.0.0")
            }
            schema.withBearerAuth()
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

private fun createDataSource(): DataSource =
    HikariConfig().apply {
        val base = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_API_JDBC_URL")
        jdbcUrl = "$base&reWriteBatchedInserts=true"
        username = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_API_USERNAME")
        password = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_API_PASSWORD")
        driverClassName = "org.postgresql.Driver"  // PostgreSQL driver
        maximumPoolSize = 15  // Maks 15 samtidige tilkoblinger
        minimumIdle = 3       // Behold minst 3 ledige tilkoblinger
        isAutoCommit = true   // Auto-commit hver SQL-operasjon
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"  // PostgreSQL standard
        initializationFailTimeout = 10_000  // Vent maks 10 sekunder ved oppstart feil
        connectionTimeout = 30_000  // Vent maks 30 sekunder på ny tilkobling
        idleTimeout = 600_000  // 10 minutter - lukk ledige tilkoblinger etter dette
        maxLifetime = 1_800_000  // 30 minutter - lukk og erstatt tilkoblinger etter dette
        leakDetectionThreshold = 60_000  // Logg advarsel hvis tilkobling holdes > 60 sekunder
        poolName = "RekrutteringstreffPool"  // Navn for logging/debugging
        validate()
    }.let(::HikariDataSource)
