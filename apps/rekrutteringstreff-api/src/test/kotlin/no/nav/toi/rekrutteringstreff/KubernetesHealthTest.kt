package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.responseUnit
import com.github.kittinunf.result.Result
import no.nav.toi.rekrutteringstreff.App
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KubernetesHealthTest {
    private val appPort = 10000
    private val app = App(port = appPort, RekrutteringstreffRepository(TestDatabase().dataSource), listOf())

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
