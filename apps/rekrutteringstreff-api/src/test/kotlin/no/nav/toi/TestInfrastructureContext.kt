package no.nav.toi

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import javax.sql.DataSource

class TestInfrastructureContext(
    override val dataSource: DataSource,
    override val pilotkontorer: List<String> = listOf("1234"),
    modiaKlient: ModiaKlient? = null,
    modiaKlientUrl: String = "",
    kandidatsøkKlient: KandidatsøkKlient? = null,
    kandidatsøkKlientUrl: String = "",
    val authPort: Int = ubruktPortnr(),
) : InfrastructureContext() {
    val authServer = MockOAuth2Server()

    override val rapidsConnection = TestRapid()
    override val httpClient = no.nav.toi.httpClient

    override val accessTokenClient = AccessTokenClient(
        clientId = "test",
        secret = "test",
        azureUrl = "http://localhost:$authPort/token",
        httpClient = httpClient
    )

    override val authConfigs = listOf(
        AuthenticationConfiguration(
            issuer = "http://localhost:$authPort/default",
            jwksUri = "http://localhost:$authPort/default/jwks",
            audience = "rekrutteringstreff-audience"
        )
    )

    override val rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
        jobbsøkerrettet = AzureAdRoller.jobbsøkerrettet,
        arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
        utvikler = AzureAdRoller.utvikler
    )

    override val leaderElection = LeaderElectionMock()

    override val modiaKlient: ModiaKlient = modiaKlient ?: ModiaKlient(
        modiaContextHolderUrl = modiaKlientUrl,
        modiaContextHolderScope = "",
        accessTokenClient = accessTokenClient,
        httpClient = httpClient,
    )

    override val kandidatsøkKlient: KandidatsøkKlient = kandidatsøkKlient ?: KandidatsøkKlient(
        kandidatsokApiUrl = kandidatsøkKlientUrl,
        kandidatsokScope = "",
        accessTokenClient = accessTokenClient,
        httpClient = httpClient,
    )

    fun start() { authServer.start(port = authPort) }
    fun stop() { authServer.shutdown() }
}
