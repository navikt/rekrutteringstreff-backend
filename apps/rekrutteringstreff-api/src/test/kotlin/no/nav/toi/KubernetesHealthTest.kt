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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KubernetesHealthTest {
    private val appPort = ubruktPortnr()
    private val accessTokenClient = AccessTokenClient(
        clientId = "clientId",
        secret = "clientSecret",
        azureUrl = "",
        httpClient = httpClient
    )
    private val app = App(
        port = appPort,
        authConfigs = emptyList<AuthenticationConfiguration>(),
        dataSource = TestDatabase().dataSource,
        jobbsøkerrettet = jobbsøkerrettet,
        arbeidsgiverrettet = arbeidsgiverrettet,
        utvikler = utvikler,
        kandidatsokApiUrl = "",
        kandidatsokScope = "",
        rapidsConnection = TestRapid(),
        accessTokenClient = AccessTokenClient(
            clientId = "clientId",
            secret = "clientSecret",
            azureUrl = "",
            httpClient = httpClient
        ),
        modiaKlient = ModiaKlient(
            modiaContextHolderUrl = "",
            modiaContextHolderScope = "",
            accessTokenClient = accessTokenClient,
            httpClient = httpClient
        ),
        pilotkontorer = emptyList<String>()

    )

    @BeforeAll
    fun setUp() {
        app.start()
    }

    @AfterAll
    fun tearDown() {
        app.close()
    }

    @Test
    fun isAlive() {
        val response = httpGet("http://localhost:$appPort/isalive")
        assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun isReady() {
        val response = httpGet("http://localhost:$appPort/isready")
        assertThat(response.statusCode()).isEqualTo(200)
    }
}
