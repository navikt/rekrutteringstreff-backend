package no.nav.toi

import no.nav.toi.rekrutteringstreff.TestDatabase
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
    private val infra = TestInfrastructureContext(dataSource = TestDatabase().dataSource)
    private val app = App(
        ctx = ApplicationContext(infra),
        port = appPort,
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
