package no.nav.toi.config

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AccessTokenClient
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.AzureAdRoller
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.LeaderElectionMock
import no.nav.toi.RolleUuidSpesifikasjon
import no.nav.toi.TestRapid
import no.nav.toi.arbeidsgiver.arbeidsgiverModule
import no.nav.toi.httpClient
import no.nav.toi.jobbsoker.jobbsøkerModule
import no.nav.toi.rekrutteringstreff.rekrutteringstreffModule
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import javax.sql.DataSource

fun testKoinApplication(
    dataSource: DataSource,
    authServer: MockOAuth2Server?,
    port: Int,
    authPort: Int,
    rapidsConnection: RapidsConnection = TestRapid(),
    wireMockBaseUrl: String = "",
    pilotkontorer: List<String> = listOf("1234"),
    modiaKlientOverride: ModiaKlient? = null,
): KoinApplication {
    val testConfig = AppConfig(
        port = port,
        authConfigs = if (authServer != null) listOf(
            AuthenticationConfiguration(
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
                audience = "rekrutteringstreff-audience",
            )
        ) else emptyList(),
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            jobbsøkerrettet = AzureAdRoller.jobbsøkerrettet,
            arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
            utvikler = AzureAdRoller.utvikler,
        ),
        pilotkontorer = pilotkontorer,
        azureClientId = "client-id",
        azureClientSecret = "secret",
        azureTokenEndpoint = "http://localhost:$authPort/token",
        kandidatsokApiUrl = wireMockBaseUrl,
        kandidatsokScope = "api://kandidatsok/.default",
        modiaContextHolderUrl = wireMockBaseUrl,
        modiaContextHolderScope = "",
    )

    val testInfraModule = module {
        single { testConfig }
        single<DataSource> { dataSource }
        single { httpClient }
        single<RapidsConnection> { rapidsConnection }
        single<LeaderElectionInterface> { LeaderElectionMock() }
        single {
            AccessTokenClient(
                secret = "secret",
                clientId = "client-id",
                azureUrl = "http://localhost:$authPort/token",
                httpClient = get(),
            )
        }
        single {
            modiaKlientOverride ?: ModiaKlient(
                modiaContextHolderUrl = wireMockBaseUrl,
                modiaContextHolderScope = "",
                accessTokenClient = get(),
                httpClient = get(),
            )
        }
    }

    return koinApplication {
        modules(
            testInfraModule,
            javalinModule,
            jobbsøkerModule,
            rekrutteringstreffModule,
            arbeidsgiverModule,
        )
    }
}
