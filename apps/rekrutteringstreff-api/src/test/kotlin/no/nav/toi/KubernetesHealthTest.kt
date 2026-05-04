package no.nav.toi

import no.nav.toi.config.testKoinApplication
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.KoinApplication

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KubernetesHealthTest {
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
