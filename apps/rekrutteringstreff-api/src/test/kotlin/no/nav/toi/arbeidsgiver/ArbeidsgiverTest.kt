package no.nav.toi.rekrutteringstreff.arbeidsgiver

import com.github.kittinunf.fuel.Fuel
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.util.UUID


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArbeidsgiverTest {


    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18012
        private val database = TestDatabase()
        private val appPort = ubruktPortnr()

        private val app = App(
            port = appPort,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer = "http://localhost:$authPort/default",
                    jwksUri = "http://localhost:$authPort/default/jwks",
                    audience = "rekrutteringstreff-audience"
                )
            ),
            database.dataSource
        )
    }

    @BeforeAll
    fun setUp() {
        authServer.start(port = authPort)
        app.start()
    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringLeggTilArbeidsgiver(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val anyTreffId = "anyTreffID"
        val (_, response, result) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/arbeidsgiver")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, response, result)
    }


    @Test
    fun leggTilArbeidsgiver() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val anyOrgnr = "555555555"
        val anyOrgnavn = "anyOrgnavn"
        val anyTreffId = TreffId(UUID.randomUUID())
        val requestBody = """
            {
              "orgnr" : "$anyOrgnr",
              "orgnavn" : "$anyOrgnavn"
            }
            """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$anyTreffId/arbeidsgiver")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_CREATED, response, result)
        // TODO Assert antall i DB
    }

    // TODO testcase: Når treffet ikke finnes

}
