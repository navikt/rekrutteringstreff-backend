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
import no.nav.toi.ExceptionMapping.exceptionMapping
import no.nav.toi.arbeidsgiver.ArbeidsgiverController
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverService
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortFeilLytter
import no.nav.toi.jobbsoker.aktivitetskort.JobbsøkerhendelserScheduler
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortRepository
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovLytter
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovScheduler
import no.nav.toi.jobbsoker.synlighet.SynlighetsLytter
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.RekrutteringstreffController
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffScheduler
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.eier.EierController
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.innlegg.InnleggController
import no.nav.toi.rekrutteringstreff.innlegg.InnleggRepository
import no.nav.toi.rekrutteringstreff.innlegg.InnleggService
import no.nav.toi.rekrutteringstreff.ki.KiController
import no.nav.toi.rekrutteringstreff.ki.KiLoggRepository
import no.nav.toi.rekrutteringstreff.ki.KiLoggService
import no.nav.toi.rekrutteringstreff.ki.OpenAiClient
import no.nav.toi.rekrutteringstreff.opprydning.RekrutteringstreffOpprydningScheduler
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokController
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokRepository
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokService
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
    private val pilotkontorer: List<String>,
    private val leaderElection: LeaderElectionInterface,
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
        httpClient: HttpClient,
        leaderElection: LeaderElectionInterface,
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
            accessTokenClient = accessTokenClient,
            httpClient = httpClient
        ),
        rapidsConnection = rapidsConnection,
        modiaKlient = modiaKlient,
        pilotkontorer = pilotkontorer,
        leaderElection = leaderElection,
    )

    private lateinit var javalin: Javalin
    private lateinit var jobbsøkerhendelserScheduler: JobbsøkerhendelserScheduler
    private lateinit var synlighetsBehovScheduler: SynlighetsBehovScheduler
    private lateinit var rekrutteringstreffOpprydningScheduler: RekrutteringstreffOpprydningScheduler
    private lateinit var rekrutteringstreffScheduler: RekrutteringstreffScheduler
    private val secureLog = SecureLog(log)

    fun start() {
        val jobbsøkerRepository = JobbsøkerRepository(dataSource, JacksonConfig.mapper)
        val jobbsøkerService = JobbsøkerService(dataSource, jobbsøkerRepository)
        val kiLoggRepository = KiLoggRepository(dataSource)
        val kiLoggService = KiLoggService(kiLoggRepository)
        val aktivitetskortRepository = AktivitetskortRepository(dataSource)
        val eierRepository = EierRepository(dataSource)
        val rekrutteringstreffRepository = RekrutteringstreffRepository(dataSource)
        val eierService = EierService(eierRepository, rekrutteringstreffRepository, dataSource)
        val arbeidsgiverRepository = ArbeidsgiverRepository(dataSource, JacksonConfig.mapper)
        val rekrutteringstreffService = RekrutteringstreffService(
            dataSource,
            rekrutteringstreffRepository,
            jobbsøkerRepository,
            arbeidsgiverRepository,
            jobbsøkerService,
            eierService
        )

        startJavalin(jobbsøkerRepository, rekrutteringstreffService, eierService, arbeidsgiverRepository, jobbsøkerService, kiLoggService)
        startSchedulere(jobbsøkerService, kiLoggService, leaderElection, aktivitetskortRepository, rekrutteringstreffRepository, rekrutteringstreffService)
        startRR(jobbsøkerService)
        log.info("Hele applikasjonen er startet og klar til å motta forespørsler.")
    }

    private fun startJavalin(
        jobbsøkerRepository: JobbsøkerRepository,
        rekrutteringstreffService: RekrutteringstreffService,
        eierService: EierService,
        arbeidsgiverRepository: ArbeidsgiverRepository,
        jobbsøkerService: JobbsøkerService,
        kiLoggService: KiLoggService,
    ) {
        log.info("Starting Javalin on port $port")
        kjørFlywayMigreringer(dataSource)

        javalin = Javalin.create { config ->
            config.jsonMapper(JavalinJackson(JacksonConfig.mapper))
            configureOpenApi(config)
        }

        javalin.exceptionMapping()

        HealthController(javalin, HealthRepository(dataSource))
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

        val innleggRepository = InnleggRepository(dataSource)
        val kiLoggRepository = KiLoggRepository(dataSource)

        val arbeidsgiverRepository = ArbeidsgiverRepository(dataSource, JacksonConfig.mapper)
        val arbeidsgiverService = ArbeidsgiverService(dataSource, arbeidsgiverRepository)

        val innleggService = InnleggService(innleggRepository, rekrutteringstreffService)

        val sokRepository = RekrutteringstreffSokRepository(dataSource)
        val sokService = RekrutteringstreffSokService(sokRepository)
        RekrutteringstreffSokController(
            sokService = sokService,
            javalin = javalin
        )

        RekrutteringstreffController(
            rekrutteringstreffService = rekrutteringstreffService,
            eierService = eierService,
            kiLoggService = kiLoggService,
            javalin = javalin
        )
        InnleggController(
            innleggService = innleggService,
            kiLoggService = kiLoggService,
            eierService = eierService,
            javalin = javalin
        )
        EierController(
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
            openAiClient = OpenAiClient(repo = kiLoggRepository),
            javalin = javalin
        )

        javalin.start(port)
    }

    private fun startSchedulere(
        jobbsøkerService: JobbsøkerService,
        kiLoggService: KiLoggService,
        leaderElection: LeaderElectionInterface,
        aktivitetskortRepository: AktivitetskortRepository,
        rekrutteringstreffRepository: RekrutteringstreffRepository,
        rekrutteringstreffService: RekrutteringstreffService,
    ) {
        log.info("Starting schedulers")

        jobbsøkerhendelserScheduler = JobbsøkerhendelserScheduler(
            dataSource = dataSource,
            aktivitetskortRepository = aktivitetskortRepository,
            rekrutteringstreffRepository = rekrutteringstreffRepository,
            rapidsConnection = rapidsConnection,
            objectMapper = JacksonConfig.mapper,
            leaderElection = leaderElection,
        )
        jobbsøkerhendelserScheduler.start()

        synlighetsBehovScheduler = SynlighetsBehovScheduler(
            jobbsøkerService = jobbsøkerService,
            rapidsConnection = rapidsConnection,
            leaderElection = leaderElection,
        )
        synlighetsBehovScheduler.start()

        rekrutteringstreffOpprydningScheduler = RekrutteringstreffOpprydningScheduler(
            kiLoggService = kiLoggService,
            leaderElection = leaderElection,
        )
        rekrutteringstreffOpprydningScheduler.start()

        rekrutteringstreffScheduler = RekrutteringstreffScheduler(rekrutteringstreffService, leaderElection)
        rekrutteringstreffScheduler.start()
    }

    fun startRR(jobbsøkerService: JobbsøkerService) {
        log.info("Starting RapidsConnection")
        AktivitetskortFeilLytter(rapidsConnection, jobbsøkerService)
        MinsideVarselSvarLytter(rapidsConnection, jobbsøkerService, JacksonConfig.mapper)
        SynlighetsLytter(rapidsConnection, jobbsøkerService)
        SynlighetsBehovLytter(rapidsConnection, jobbsøkerService)
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
        if (::jobbsøkerhendelserScheduler.isInitialized) jobbsøkerhendelserScheduler.stop()
        if (::synlighetsBehovScheduler.isInitialized) synlighetsBehovScheduler.stop()
        if (::rekrutteringstreffOpprydningScheduler.isInitialized) rekrutteringstreffOpprydningScheduler.stop()
        if (::rekrutteringstreffScheduler.isInitialized) rekrutteringstreffScheduler.stop()
        if (::javalin.isInitialized) javalin.stop()
        rapidsConnection.stop()
        (dataSource as? HikariDataSource)?.close()
        log.info("Application shutdown complete")
    }
}

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

    val app = App(
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
        pilotkontorer = getenv("PILOTKONTORER").split(",").map { it.trim() },
        httpClient = httpClient,
        leaderElection = LeaderElection(),
    )
    Runtime.getRuntime().addShutdownHook(Thread {
        app.close()
    })
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
