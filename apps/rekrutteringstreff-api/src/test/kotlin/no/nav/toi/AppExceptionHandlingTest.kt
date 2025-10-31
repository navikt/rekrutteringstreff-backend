package no.nav.toi

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppExceptionHandlingTest {
    private val port = ubruktPortnr()
    private val authServer = MockOAuth2Server()
    private val authPort = ubruktPortnr()
    private val app = App(
        port,
        listOf(
            AuthenticationConfiguration(
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
                audience = "rekrutteringstreff-audience"
            )
        ),
        TestDatabase().dataSource,
        arbeidsgiverrettet,
        utvikler,
        "", "", "", "", "",
        TestRapid(),
        httpClient = httpClient
    )

    private lateinit var treffId: UUID

    @BeforeAll
    fun start() {
        authServer.start(port = authPort)
        app.start()
        // Opprett et treff via API for å ha en gyldig id å PUT'e mot
        val token = authServer.lagToken(authPort).serialize()
        val (_, response, _) = Fuel.post("http://localhost:$port/api/rekrutteringstreff")
            .header("Authorization", "Bearer $token")
            .jsonBody("""{"opprettetAvNavkontorEnhetId":"0313"}""")
            .response()
        assertThat(response.statusCode).isEqualTo(201)
        val body = String(response.data)
        // body expected like: {"id":"uuid"}
        val idRegex = Regex(""""id"\s*:\s*"([a-f0-9\-]+)"""")
        val match = idRegex.find(body) ?: error("Fikk ikke id fra opprett-respons: $body")
        treffId = UUID.fromString(match.groupValues[1])
    }

    @AfterAll
    fun stop() { authServer.shutdown(); app.close() }

    @Test
    fun `ugyldig JSON (JsonParseException) gir 400`() {
        val (_, response: Response, result: Result<ByteArray, FuelError>) = Fuel.put("http://localhost:$port/api/rekrutteringstreff/$treffId")
            .header("Authorization", "Bearer ${authServer.lagToken(authPort).serialize()}")
            .header("Content-Type", "text/plain")
            .body("{tittel: uten quotes}") // Ikke gyldig JSON
            .response()

        assertThat(response.statusCode).isEqualTo(400)
        val body = String(response.data)
        assertThat(body).contains("feil")
        assertThat(body).contains("hint")
        when (result) {
            is Result.Success -> {}
            is Result.Failure -> {}
        }
    }

    @Test
    fun `gyldig JSON men feil mapping (JsonMappingException) gir 400`() {
        // tittel er påkrevd, mangler -> mapping-feil
        val json = """{
            "beskrivelse":"hei",
            "fraTid":"2025-09-10T08:00:00",
            "tilTid":"2025-09-10T10:00:00",
            "svarfrist":"2025-09-10T00:00:00",
            "gateadresse":"Malmøgata 2",
            "postnummer":"0284",
            "poststed":"Oslo"
        }"""
        val (_, response: Response, _) = Fuel.put("http://localhost:$port/api/rekrutteringstreff/$treffId")
            .header("Authorization", "Bearer ${authServer.lagToken(authPort).serialize()}")
            .header("Content-Type", "application/json")
            .body(json)
            .response()

        assertThat(response.statusCode).isEqualTo(400)
        val body = String(response.data)
        assertThat(body).contains("feil")
    }

    @Test
    fun `uventet feil gir 500 med standard feilmelding`() {
        // For å trigge en uventet feil kan vi kalle en PUT med gyldig JSON men med en id som ikke finnes i DB,
        // hvilket i repository vil forsøke å hente db_id og next() uten rad og dermed kaste en Exception.
        val ikkeEksisterendeId = UUID.randomUUID()
        val json = """{
            "tittel":"Test",
            "beskrivelse":null,
            "fraTid":null,
            "tilTid":null,
            "svarfrist":null,
            "gateadresse":null,
            "postnummer":null,
            "poststed":null
        }"""
        val (_, response: Response, _) = Fuel.put("http://localhost:$port/api/rekrutteringstreff/$ikkeEksisterendeId")
            .header("Authorization", "Bearer ${authServer.lagToken(authPort).serialize()}")
            .header("Content-Type", "application/json")
            .body(json)
            .response()

        assertThat(response.statusCode).isEqualTo(500)
        val body = String(response.data)
        assertThat(body).contains("databasefeil")
    }
}
