import no.nav.toi.rekrutteringstreff.ki.ValiderMedLoggResponseDto

ackage no.nav.toi.rekrutteringstreff.ki

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.App
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.TestRapid
import no.nav.toi.nowOslo
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.*
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KiLoggTest {

    companion object {
        @JvmStatic
        @RegisterExtension
        val wireMockServer: WireMockExtension = WireMockExtension.newInstance()
            .options(WireMockConfiguration.options().port(9955))
            .build()
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18022

    private val db = TestDatabase()
    private val dataSource: DataSource get() = db.dataSource

    private val appPort = no.nav.toi.ubruktPortnrFra10000.ubruktPortnr()

    private val app = App(
        port = appPort,
        authConfigs = listOf(
            AuthenticationConfiguration(
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
                audience = "rekrutteringstreff-audience"
            )
        ),
        dataSource = dataSource,
        arbeidsgiverrettet = arbeidsgiverrettet,
        utvikler = utvikler,
        kandidatsokApiUrl = "",
        kandidatsokScope = "",
        azureClientId = "",
        azureClientSecret = "",
        azureTokenEndpoint = "",
        rapid = TestRapid()
    )

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
        db.slettAlt()
        wireMockServer.resetAll()
    }

    @Test
    fun `POST \\/api\\/ki\\/valider lagrer logg og returnerer loggId`() {
        // Stub OpenAI completion
        val begrunnelse = "Beskrivelsen spesifiserer et geografisk område for søkere, noe som kan være diskriminerende."
        val responseBody = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "{ \"bryterRetningslinjer\": true, \"begrunnelse\": \"$begrunnelse\" }"
                  }
                }
              ]
            }
        """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post(
                WireMock.urlEqualTo("/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview")
            ).willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)
            )
        )

        // Arrange: create a rekrutteringstreff and fetch its internal db_id (long)
        val navIdent = "A123456"
        val token = authServer.issueToken(
            issuerId = "default",
            subject = "test-subject",
            audience = "rekrutteringstreff-audience",
            claims = mapOf("NAVident" to navIdent)
        )
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "KI-logg test ${nowOslo()}")
        val treffDbId = hentTreffDbId(treffId)

        // Act: call POST /api/ki/valider
        val requestJson = """
            {
              "treffDbId": $treffDbId,
              "feltType": "tittel",
              "tekst": "Vi ser kun etter deltakere fra Oslo."
            }
        """.trimIndent()

        val (req, res, result) = Fuel.post("http://localhost:$appPort/api/ki/valider")
            .header("Authorization", "Bearer ${token.serialize()}")
            .body(requestJson)
            .responseString()

        // Assert response
        when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> {
                assertThat(res.statusCode).isEqualTo(200)
                val dto = no.nav.toi.JacksonConfig.mapper.readValue(
                    result.get(),
                    ValiderMedLoggResponseDto::class.java
                )
                assertThat(dto.bryterRetningslinjer).isTrue()
                assertThat(dto.begrunnelse).isEqualTo(begrunnelse)
                assertThat(dto.loggId).isNotBlank()
                val loggId = UUID.fromString(dto.loggId)

                // Assert DB: row exists and fields are persisted
                dataSource.connection.use { c ->
                    c.prepareStatement(
                        """
                        SELECT treff_db_id, felt_type, bryter_retningslinjer, lagret,
                               manuell_kontroll_bryter_retningslinjer, manuell_kontroll_utført_av, manuell_kontroll_tidspunkt
                          FROM ki_sporring_logg
                         WHERE id = ?
                        """.trimIndent()
                    ).use { ps ->
                        ps.setObject(1, loggId)
                        ps.executeQuery().use { rs ->
                            assertThat(rs.next()).isTrue()
                            assertThat(rs.getLong("treff_db_id")).isEqualTo(treffDbId)
                            assertThat(rs.getString("felt_type")).isEqualTo("tittel")
                            assertThat(rs.getBoolean("bryter_retningslinjer")).isTrue()
                            assertThat(rs.getBoolean("lagret")).isFalse()
                            assertThat(rs.getObject("manuell_kontroll_bryter_retningslinjer")).isNull()
                            assertThat(rs.getString("manuell_kontroll_utført_av")).isNull()
                            assertThat(rs.getTimestamp("manuell_kontroll_tidspunkt")).isNull()
                            assertThat(rs.next()).isFalse()
                        }
                    }
                }
            }
        }
    }

    private fun hentTreffDbId(treffId: TreffId): Long =
        dataSource.connection.use { c ->
            c.prepareStatement("SELECT db_id FROM rekrutteringstreff WHERE id = ?").use { ps ->
                ps.setObject(1, treffId.somUuid)
                ps.executeQuery().use { rs ->
                    check(rs.next()) { "Fant ikke rekrutteringstreff ${treffId.somUuid}" }
                    rs.getLong(1)
                }
            }
        }
}