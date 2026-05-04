package no.nav.toi

import no.nav.toi.config.testKoinApplication
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.koin.core.KoinApplication

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwaggerEndpointsTest {

    private val appPort = ubruktPortnr()
    private val database = TestDatabase()

    private lateinit var koinApp: KoinApplication
    private lateinit var app: App

    @BeforeAll
    fun setUp() {
        koinApp = testKoinApplication(
            dataSource = database.dataSource,
            authServer = null,
            port = appPort,
            authPort = 0,
            pilotkontorer = emptyList(),
        )
        app = App(koinApp.koin)
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
