package no.nav.toi.config

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.toi.AccessTokenClient
import no.nav.toi.LeaderElection
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.koin.dsl.module
import java.net.http.HttpClient
import javax.sql.DataSource

val infrastrukturModule get() = module {
    single<HttpClient> {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
    }

    single<DataSource> {
        fun getenv(key: String) = System.getenv(key)
            ?: throw NullPointerException("Det finnes ingen miljøvariabel med navn [$key]")
        val base = getenv("NAIS_DATABASE_REKRUTTERINGSTREFF_API_REKRUTTERINGSTREFF_API_JDBC_URL")
        HikariConfig().apply {
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

    single {
        val config = get<AppConfig>()
        AccessTokenClient(
            secret = config.azureClientSecret,
            clientId = config.azureClientId,
            azureUrl = config.azureTokenEndpoint,
            httpClient = get(),
        )
    }

    single {
        val config = get<AppConfig>()
        ModiaKlient(
            modiaContextHolderUrl = config.modiaContextHolderUrl,
            modiaContextHolderScope = config.modiaContextHolderScope,
            accessTokenClient = get(),
            httpClient = get(),
        )
    }

    single<LeaderElectionInterface> { LeaderElection() }

    single<RapidsConnection> {
        RapidApplication.create(System.getenv(), builder = { withHttpPort(9000) })
    }
}
