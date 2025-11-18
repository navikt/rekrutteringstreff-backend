package no.nav.toi


import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.responseUnit
import com.github.kittinunf.result.Result
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
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
        val (_, response: Response, result: Result<Unit, FuelError>) = Fuel.get("http://localhost:$appPort/isalive")
            .responseUnit()
        when (result) {
            is Result.Success -> assertThat(response.statusCode).isEqualTo(200)
            is Result.Failure -> throw result.error
        }
    }

    @Test
    fun isReady() {
        val (_, response: Response, result: Result<Unit, FuelError>) = Fuel.get("http://localhost:$appPort/isready")
            .responseUnit()
        when (result) {
            is Result.Success -> assertThat(response.statusCode).isEqualTo(200)
            is Result.Failure -> throw result.error
        }
    }
}
