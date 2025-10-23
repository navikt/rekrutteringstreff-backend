package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.json.JavalinJackson
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.handleArbeidsgiver
import no.nav.toi.jobbsoker.AktivitetskortFeilLytter
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortRepository
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortInvitasjonScheduler
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortOppmøteScheduler
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortSvarScheduler
import no.nav.toi.jobbsoker.handleJobbsøker
import no.nav.toi.jobbsoker.handleJobbsøkerInnloggetBorger
import no.nav.toi.jobbsoker.handleJobbsøkerOutbound
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.handleRekrutteringstreff
import no.nav.toi.rekrutteringstreff.ki.KiLoggRepository
import no.nav.toi.rekrutteringstreff.ki.handleKi
import org.flywaydb.core.Flyway
import java.time.Instant
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*
import javax.sql.DataSource

class App(
    private val port: Int,
    private val authConfigs: List<AuthenticationConfiguration>,
    private val dataSource: DataSource,
    private val arbeidsgiverrettet: UUID,
    private val utvikler: UUID,
    private val kandidatsokKlient: KandidatsøkKlient,
    private val rapidsConnection: RapidsConnection,
) {
    constructor(
        port: Int,
        authConfigs: List<AuthenticationConfiguration>,
        dataSource: DataSource,
        arbeidsgiverrettet: UUID,
        utvikler: UUID,
        kandidatsokApiUrl: String,
        kandidatsokScope: String,
        azureClientId: String,
        azureClientSecret: String,
        azureTokenEndpoint: String,
        rapidsConnection: RapidsConnection,
    ) : this(
        port = port,
        authConfigs = authConfigs,
        dataSource = dataSource,
        arbeidsgiverrettet = arbeidsgiverrettet,
        utvikler = utvikler,
        kandidatsokKlient = KandidatsøkKlient(
            kandidatsokApiUrl = kandidatsokApiUrl,
            accessTokenClient = AccessTokenClient(
                secret = azureClientSecret,
                clientId = azureClientId,
                scope = kandidatsokScope,
                azureUrl = azureTokenEndpoint,
            )
        ),
        rapidsConnection = rapidsConnection

    )

    private lateinit var javalin: Javalin
    fun start() {
        val jobbsøkerRepository = JobbsøkerRepository(dataSource, JacksonConfig.mapper)
        startJavalin(jobbsøkerRepository)
        startSchedulere()
        startRR(jobbsøkerRepository)
        log.info("Hele applikasjonen er startet og klar til å motta forespørsler.")
    }

    private fun startJavalin(jobbsøkerRepository: JobbsøkerRepository) {
        log.info("Starting Javalin on port $port")
        kjørFlywayMigreringer(dataSource)

        javalin = Javalin.create { config ->
            config.jsonMapper(JavalinJackson(JacksonConfig.mapper))
            configureOpenApi(config)
        }


        javalin.exception(com.fasterxml.jackson.core.JsonParseException::class.java) { _, ctx ->
            ctx.status(400).json(
                mapOf(
                    "feil" to "Ugyldig JSON i request-body.",
                    "hint" to "Sett Content-Type: application/json, bruk gyldig JSON med \"-siterte feltnavn og ISO-8601 dato/tid med tidsone (f.eks. 2025-09-10T08:00:00+02:00)."
                )
            )
        }
        javalin.exception(com.fasterxml.jackson.databind.JsonMappingException::class.java) { _, ctx ->
            ctx.status(400).json(
                mapOf(
                    "feil" to "Klarte ikke å lese request-body til forventet format.",
                    "hint" to "Body må være JSON som matcher skjemaet for OppdaterRekrutteringstreffDto. Dato/tid må inkludere tidsone (f.eks. +02:00)."
                )
            )
        }
        // I enkelte Javalin-versjoner pakkes Jackson-feil i JsonMapperException
        try {
            val k = Class.forName("io.javalin.json.JsonMapperException") as Class<out Exception>
            @Suppress("UNCHECKED_CAST")
            javalin.exception(k) { _, ctx ->
                ctx.status(400).json(
                    mapOf(
                        "feil" to "Ugyldig request-body (JSON).",
                        "hint" to "Sett Content-Type: application/json og bruk ISO-8601 dato/tid med tidsone på alle datoer."
                    )
                )
            }
        } catch (_: ClassNotFoundException) {
            // Ignorer – typen finnes ikke i denne Javalin-versjonen
        }

        javalin.exception(java.sql.SQLException::class.java) { e, ctx ->
            if (e.sqlState == "23503") {
                ctx.status(409).json(mapOf(
                    "feil" to "Kan ikke slette rekrutteringstreff fordi avhengige rader finnes. Slett barn først."
                ))
            } else {
                secure(log).error("SQL-feil", e)
                ctx.status(500).json(mapOf("feil" to "En databasefeil oppstod på serveren."))
            }
        }
        javalin.exception(no.nav.toi.rekrutteringstreff.UlovligSlettingException::class.java) { e, ctx ->
            ctx.status(409).json(mapOf("feil" to (e.message ?: "Ulovlig sletting")))
        }

        javalin.exception(Exception::class.java) { e, ctx ->
            log.error("Uventet feil", e)
            ctx.status(500).json(
                mapOf(
                    "feil" to "En databasefeil oppstod på serveren."
                )
            )
        }

        javalin.handleHealth()
        javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(
            authConfigs,
            RolleUuidSpesifikasjon(arbeidsgiverrettet, utvikler)
        )
        
        javalin.handleRekrutteringstreff(RekrutteringstreffRepository(dataSource))
        javalin.handleArbeidsgiver(ArbeidsgiverRepository(dataSource, JacksonConfig.mapper))
        javalin.handleJobbsøker(jobbsøkerRepository)
        javalin.handleJobbsøkerInnloggetBorger(jobbsøkerRepository)
        javalin.handleJobbsøkerOutbound(jobbsøkerRepository, kandidatsokKlient)
        javalin.handleKi(KiLoggRepository(dataSource))

        javalin.start(port)
    }

    private fun startSchedulere() {
        log.info("Starting scheduler")
        AktivitetskortInvitasjonScheduler(
            aktivitetskortRepository = AktivitetskortRepository(dataSource),
            rekrutteringstreffRepository = RekrutteringstreffRepository(dataSource),
            rapidsConnection = rapidsConnection
        ).start()
        AktivitetskortSvarScheduler(
            aktivitetskortRepository = AktivitetskortRepository(dataSource),
            rekrutteringstreffRepository = RekrutteringstreffRepository(dataSource),
            rapidsConnection = rapidsConnection
        ).start()
        AktivitetskortOppmøteScheduler(
            aktivitetskortRepository = AktivitetskortRepository(dataSource),
            rekrutteringstreffRepository = RekrutteringstreffRepository(dataSource),
            rapidsConnection = rapidsConnection
        ).start()
    }

    fun startRR(jobbsøkerRepository: JobbsøkerRepository) {
        log.info("Starting RapidsConnection")
        AktivitetskortFeilLytter(rapidsConnection, jobbsøkerRepository)
        Thread(rapidsConnection::start).start()
    }

    fun close() {
        if (::javalin.isInitialized) javalin.stop()
    }
}

private val log = noClassLogger()

fun main() {
    val dataSource = createDataSource()
    val rapidsConnection = RapidApplication.create(System.getenv())

    App(
        port = 8080,
        authConfigs = listOfNotNull(
            AuthenticationConfiguration(
                audience = getenv("AZURE_APP_CLIENT_ID"),
                issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI")
            ),
            AuthenticationConfiguration(
                audience = getenv("TOKEN_X_CLIENT_ID"),
                issuer = getenv("TOKEN_X_ISSUER"),
                jwksUri = getenv("TOKEN_X_JWKS_URI")
            ),
            if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp")
                AuthenticationConfiguration(
                    audience = "dev-gcp:toi:rekrutteringstreff-api",
                    issuer = "https://fakedings.intern.dev.nav.no/fake",
                    jwksUri = "https://fakedings.intern.dev.nav.no/fake/jwks",
                ) else null
        ),
        dataSource = dataSource,
        arbeidsgiverrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET")),
        utvikler = UUID.fromString(getenv("REKRUTTERINGSBISTAND_UTVIKLER")),
        kandidatsokApiUrl = getenv("KANDIDATSOK_API_URL"),
        kandidatsokScope = getenv("KANDIDATSOK_API_SCOPE"),
        azureClientId = getenv("AZURE_APP_CLIENT_ID"),
        azureClientSecret = getenv("AZURE_APP_CLIENT_SECRET"),
        azureTokenEndpoint = getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        rapidsConnection = rapidsConnection
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

private fun createDataSource(): DataSource =
    HikariConfig().apply {
        val base = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_JDBC_URL")
        jdbcUrl = "$base&reWriteBatchedInserts=true"
        username = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_USERNAME")
        password = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_DB_PASSWORD")
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 4
        minimumIdle = 1
        isAutoCommit = true
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        initializationFailTimeout = 5_000
        validate()
    }.let(::HikariDataSource)
