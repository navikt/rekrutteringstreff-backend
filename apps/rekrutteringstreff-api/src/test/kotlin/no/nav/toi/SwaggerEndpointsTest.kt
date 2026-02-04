package no.nav.toi

import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.collections.emptyList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwaggerEndpointsTest {

    private val appPort = ubruktPortnr()
    private val accessTokenClient = AccessTokenClient(
        clientId = "clientId",
        secret = "clientSecret",
        azureUrl = "",
        httpClient = httpClient
    )
    private val app = App(
        port = appPort,
        authConfigs = emptyList(),
        dataSource = TestDatabase().dataSource,
        jobbsøkerrettet = jobbsøkerrettet,
        arbeidsgiverrettet = arbeidsgiverrettet,
        utvikler = utvikler,
        kandidatsokApiUrl = "",
        kandidatsokScope = "",
        rapidsConnection = TestRapid(),
        accessTokenClient = accessTokenClient,
        modiaKlient = ModiaKlient(
            modiaContextHolderUrl = "",
            modiaContextHolderScope = "",
            accessTokenClient = accessTokenClient,
            httpClient = httpClient
        ),
        pilotkontorer = emptyList(),
        httpClient = httpClient,
        leaderElection = LeaderElectionMock(),
    )

    @BeforeAll
    fun setUp() {
        app.start()
    }

    @AfterAll
    fun tearDown() {
        app.close()
    }

    @ParameterizedTest
    @ValueSource(strings = ["/swagger", "/openapi"])
    fun `swagger endpoints respond ok`(endpoint: String) {
        val baseUrl = "http://localhost:$appPort"
        val response = httpGet("$baseUrl$endpoint")
        assertThat(response.statusCode()).isEqualTo(200)
    }
}
