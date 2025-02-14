package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import no.nav.toi.rekrutteringstreff.App
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwaggerEndpointsTest {

    private val appPort = 10000
    private val app = App(port = appPort, listOf(), TestDatabase().dataSource)

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
        val (_, response, result) = Fuel.get("$baseUrl$endpoint").responseString()
        when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> assertThat(response.statusCode).isEqualTo(200)
        }
    }
}