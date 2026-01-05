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
import no.nav.toi.arbeidsgiver.ArbeidsgiverController
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverService
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.exception.UlovligOppdateringException
import no.nav.toi.exception.UlovligSlettingException
import no.nav.toi.jobbsoker.JobbsøkerController
import no.nav.toi.jobbsoker.JobbsøkerInnloggetBorgerController
import no.nav.toi.jobbsoker.JobbsøkerOutboundController
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.MinsideVarselSvarLytter
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortFeilLytter
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortRepository
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortJobbsøkerScheduler
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.RekrutteringstreffController
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.eier.EierController
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.innlegg.InnleggController
import no.nav.toi.rekrutteringstreff.innlegg.InnleggRepository
import no.nav.toi.rekrutteringstreff.ki.KiController
import no.nav.toi.rekrutteringstreff.ki.KiLoggRepository
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.flywaydb.core.Flyway
import java.net.http.HttpClient
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
    private val jobbsøkerrettet: UUID,
    private val arbeidsgiverrettet: UUID,
    private val utvikler: UUID,
    private val kandidatsokKlient: KandidatsøkKlient,
    private val rapidsConnection: RapidsConnection,
    private val modiaKlient: ModiaKlient,
    private val pilotkontorer: List<String>
) {
    constructor(
        port: Int,
        authConfigs: List<AuthenticationConfiguration>,
        dataSource: DataSource,
        jobbsøkerrettet: UUID,
        arbeidsgiverrettet: UUID,
        utvikler: UUID,
        kandidatsokApiUrl: String,
        kandidatsokScope: String,
        rapidsConnection: RapidsConnection,
        accessTokenClient: AccessTokenClient,
        modiaKlient: ModiaKlient,
        pilotkontorer: List<String>,
    ) : this(
        port = port,
        authConfigs = authConfigs,
        dataSource = dataSource,
        jobbsøkerrettet = jobbsøkerrettet,
        arbeidsgiverrettet = arbeidsgiverrettet,
        utvikler = utvikler,
        kandidatsokKlient = KandidatsøkKlient(
            kandidatsokApiUrl = kandidatsokApiUrl,
            kandidatsokScope = kandidatsokScope,
            accessTokenClient = accessTokenClient
        ),
        rapidsConnection = rapidsConnection,
        modiaKlient = modiaKlient,
        pilotkontorer = pilotkontorer
    )

    private lateinit var javalin: Javalin
    private lateinit var aktivitetskortJobbsøkerScheduler: AktivitetskortJobbsøkerScheduler
    fun start() {
        val jobbsøkerRepository = JobbsøkerRepository(dataSource, JacksonConfig.mapper)
        val jobbsøkerService = JobbsøkerService(dataSource, jobbsøkerRepository)
        startJavalin(jobbsøkerRepository)
        startSchedulere()
        startRR(jobbsøkerService)
        log.info("Hele applikasjonen er startet og klar til å motta forespørsler.")
    }

    private fun startJavalin(jobbsøkerRepository: JobbsøkerRepository) {
        log.info("Starting Javalin on port $port")
        kjørFlywayMigreringer(dataSource)

        javalin = Javalin.create { config ->
            config.jsonMapper(JavalinJackson(JacksonConfig.mapper))
            configureOpenApi(config)
        }

        // TODO exceptions kan også skje steder hvor disse feilmeldingene ikke gir mening.
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
        javalin.exception(UlovligSlettingException::class.java) { e, ctx ->
            ctx.status(409).json(mapOf("feil" to (e.message ?: "Ulovlig sletting")))
        }

        javalin.exception(UlovligOppdateringException::class.java) { e, ctx ->
            ctx.status(409).json(mapOf("feil" to (e.message ?: "Konflikt ved oppdatering")))
        }

        javalin.exception(RekrutteringstreffIkkeFunnetException::class.java) { e, ctx ->
            ctx.status(404).json(mapOf("feil" to (e.message ?: "Fant ikke rekrutteringstreffet")))
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
            authConfigs = authConfigs,
            rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
                jobbsøkerrettet = jobbsøkerrettet,
                arbeidsgiverrettet = arbeidsgiverrettet,
                utvikler = utvikler
            ),
            modiaKlient = modiaKlient,
            pilotkontorer = pilotkontorer
        )

        val rekrutteringstreffRepository = RekrutteringstreffRepository(dataSource)
        val eierRepository = EierRepository(dataSource)
        val innleggRepository = InnleggRepository(dataSource)
        val arbeidsgiverRepository = ArbeidsgiverRepository(dataSource, JacksonConfig.mapper)
        val kiLoggRepository = KiLoggRepository(dataSource)

        val jobbsøkerService = JobbsøkerService(dataSource, jobbsøkerRepository)
        val arbeidsgiverService = ArbeidsgiverService(dataSource, arbeidsgiverRepository)
        val rekrutteringstreffService = RekrutteringstreffService(dataSource, rekrutteringstreffRepository, jobbsøkerRepository, arbeidsgiverRepository, jobbsøkerService)
        val eierService = EierService(eierRepository)

        RekrutteringstreffController(
            rekrutteringstreffService = rekrutteringstreffService,
            eierService = eierService,
            javalin = javalin
        )
        InnleggController(
            innleggRepository = innleggRepository,
            javalin = javalin
        )
        EierController(
            eierRepository = eierRepository,
            eierService = eierService,
            javalin = javalin
        )
        ArbeidsgiverController(
            arbeidsgiverService = arbeidsgiverService,
            eierService = eierService,
            javalin = javalin
        )
        JobbsøkerController(
            jobbsøkerService = jobbsøkerService,
            eierService = eierService,
            javalin = javalin
        )
        JobbsøkerInnloggetBorgerController(
            jobbsøkerService = jobbsøkerService,
            javalin = javalin
        )
        JobbsøkerOutboundController(
            jobbsøkerRepository = jobbsøkerRepository,
            kandidatsøkKlient = kandidatsokKlient,
            eierService = eierService,
            javalin = javalin
        )
        KiController(
            kiLoggRepository = kiLoggRepository,
            javalin
        )

        javalin.start(port)
    }

    private fun startSchedulere() {
        log.info("Starting scheduler")

        val aktivitetskortRepository = AktivitetskortRepository(dataSource)
        val rekrutteringstreffRepository = RekrutteringstreffRepository(dataSource)

        aktivitetskortJobbsøkerScheduler = AktivitetskortJobbsøkerScheduler(
            dataSource = dataSource,
            aktivitetskortRepository = aktivitetskortRepository,
            rekrutteringstreffRepository = rekrutteringstreffRepository,
            rapidsConnection = rapidsConnection,
            objectMapper = JacksonConfig.mapper
        )
        aktivitetskortJobbsøkerScheduler.start()
    }

    fun startRR(jobbsøkerService: JobbsøkerService) {
        log.info("Starting RapidsConnection")
        AktivitetskortFeilLytter(rapidsConnection, jobbsøkerService)
        MinsideVarselSvarLytter(rapidsConnection, jobbsøkerService, JacksonConfig.mapper)
        Thread {
            try {
                rapidsConnection.start()
            } catch (e: Exception) {
                log.error("RapidsConnection feilet, avslutter applikasjonen", e)
                System.exit(1)
            }
        }.start()
    }

    fun close() {
        log.info("Shutting down application")
        if (::aktivitetskortJobbsøkerScheduler.isInitialized) aktivitetskortJobbsøkerScheduler.stop()
        if (::javalin.isInitialized) javalin.stop()
        (dataSource as? HikariDataSource)?.close()
        log.info("Application shutdown complete")
    }
}

private val log = noClassLogger()

fun main() {
    val dataSource = createDataSource()
    val rapidsConnection = RapidApplication.create(System.getenv(), builder = { withHttpPort(9000) })

    val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    val azureClientId = getenv("AZURE_APP_CLIENT_ID")
    val azureClientSecret = getenv("AZURE_APP_CLIENT_SECRET")
    val azureTokenEndpoint = getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")

    val accessTokenClient = AccessTokenClient(
        secret = azureClientSecret,
        clientId = azureClientId,
        azureUrl = azureTokenEndpoint,
        httpClient = httpClient
    )

    val modiaKlient = ModiaKlient(
        modiaContextHolderUrl = getenv("MODIACONTEXTHOLDER_URL"),
        modiaContextHolderScope = getenv("MODIACONTEXTHOLDER_SCOPE"),
        accessTokenClient = accessTokenClient,
        httpClient = httpClient,
    )

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
        jobbsøkerrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_JOBBSOKERRETTET")),
        arbeidsgiverrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET")),
        utvikler = UUID.fromString(getenv("REKRUTTERINGSBISTAND_UTVIKLER")),
        kandidatsokApiUrl = getenv("KANDIDATSOK_API_URL"),
        kandidatsokScope = getenv("KANDIDATSOK_API_SCOPE"),
        rapidsConnection = rapidsConnection,
        accessTokenClient = accessTokenClient,
        modiaKlient = modiaKlient,
        pilotkontorer = getenv("PILOTKONTORER").split(",").map { it.trim() }
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
