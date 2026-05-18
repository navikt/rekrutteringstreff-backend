package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import java.net.http.HttpClient
import java.util.*
import javax.sql.DataSource

@Suppress("MemberVisibilityCanBePrivate")
open class InfrastructureContext(
    private val env: Map<String, String> = System.getenv()
) {
    private fun getenv(key: String): String =
        env[key] ?: throw NullPointerException("Det finnes ingen miljøvariabel med navn [$key]")

    open val dataSource: DataSource by lazy {
        HikariConfig().apply {
            val base = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_API_JDBC_URL")
            jdbcUrl = "$base&reWriteBatchedInserts=true"
            username = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_API_USERNAME")
            password = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_API_PASSWORD")
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 15
            minimumIdle = 3
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            initializationFailTimeout = 10_000
            connectionTimeout = 30_000
            idleTimeout = 600_000
            maxLifetime = 1_800_000
            leakDetectionThreshold = 60_000
            poolName = "RekrutteringstreffPool"
            validate()
        }.let(::HikariDataSource)
    }

    open val rapidsConnection: RapidsConnection by lazy {
        RapidApplication.create(env, builder = { withHttpPort(9000) })
    }

    open val authConfigs: List<AuthenticationConfiguration> by lazy {
        listOfNotNull(
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
            if (env["NAIS_CLUSTER_NAME"] == "dev-gcp")
                AuthenticationConfiguration(
                    audience = "dev-gcp:toi:rekrutteringstreff-api",
                    issuer = "https://fakedings.intern.dev.nav.no/fake",
                    jwksUri = "https://fakedings.intern.dev.nav.no/fake/jwks",
                ) else null
        )
    }

    open val rolleUuidSpesifikasjon: RolleUuidSpesifikasjon by lazy {
        RolleUuidSpesifikasjon(
            jobbsøkerrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_JOBBSOKERRETTET")),
            arbeidsgiverrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET")),
            utvikler = UUID.fromString(getenv("REKRUTTERINGSBISTAND_UTVIKLER"))
        )
    }

    open val pilotkontorer: List<String> by lazy { getenv("PILOTKONTORER").split(",").map { it.trim() } }

    open val leaderElection: LeaderElectionInterface by lazy { LeaderElection() }

    open val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
    }

    open val accessTokenClient: AccessTokenClient by lazy {
        AccessTokenClient(
            secret = getenv("AZURE_APP_CLIENT_SECRET"),
            clientId = getenv("AZURE_APP_CLIENT_ID"),
            azureUrl = getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
            httpClient = httpClient
        )
    }

    open val modiaKlient: ModiaKlient by lazy {
        ModiaKlient(
            modiaContextHolderUrl = getenv("MODIACONTEXTHOLDER_URL"),
            modiaContextHolderScope = getenv("MODIACONTEXTHOLDER_SCOPE"),
            accessTokenClient = accessTokenClient,
            httpClient = httpClient,
        )
    }

    open val kandidatsøkKlient: KandidatsøkKlient by lazy {
        KandidatsøkKlient(
            kandidatsokApiUrl = getenv("KANDIDATSOK_API_URL"),
            kandidatsokScope = getenv("KANDIDATSOK_API_SCOPE"),
            accessTokenClient = accessTokenClient,
            httpClient = httpClient
        )
    }
}
