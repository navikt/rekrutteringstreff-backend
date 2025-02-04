package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.rekrutteringstreff.App
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffTest {

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val appPort = 10000
    private val app = App(port = appPort)

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

    @Test
    fun opprettRekrutteringstreff() {
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val gyldigTittelfelt = "Tittelfeltet"
        val gyldigFraTid = ZonedDateTime.now().minusDays(1)
        val gyldigTilTid = ZonedDateTime.now().plusDays(1).plusHours(2)
        val gyldigSted = "Gyldig Sted"
        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff")
            .body(
                """
                {
                    "tittel": "$gyldigTittelfelt",
                    "fraTid": "$gyldigFraTid",
                    "tilTid": "$gyldigTilTid",
                    "sted": "$gyldigSted"
                }
            """.trimIndent()
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> {
                assertThat(response.statusCode).isEqualTo(201)

                val rekrutteringstreff = database.hentRekrutteringstreff()
                assertThat(rekrutteringstreff).size().isEqualTo(1)

                rekrutteringstreff[0].apply {
                    assertThat(tittel).isEqualTo(gyldigTittelfelt)
                    assertThat(fraTid).isEqualTo(gyldigFraTid)
                    assertThat(tilTid).isEqualTo(gyldigTilTid)
                    assertThat(sted).isEqualTo(gyldigSted)
                }
            }
        }
    }

    private fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        navIdent: String = "A000001",
        claims: Map<String, Any> = mapOf("NAVident" to navIdent),
        expiry: Long = 3600
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        claims = claims,
        expiry = expiry
    )
}
