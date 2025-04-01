package no.nav.toi

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwaggerEndpointsTest {

    private val appPort = ubruktPortnr()
    private val app = App(port = appPort, listOf(), TestDatabase().dataSource, arbeidsgiverrettet, utvikler)

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
