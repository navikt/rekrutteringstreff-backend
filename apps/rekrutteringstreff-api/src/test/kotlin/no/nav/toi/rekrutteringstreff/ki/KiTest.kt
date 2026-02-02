package no.nav.toi.rekrutteringstreff.ki

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.ki.dto.KiLoggOutboundDto
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_OK
import java.sql.Types
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Stream

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class KiTest {

    companion object {
        @JvmStatic
        @RegisterExtension
        val wireMockServer: WireMockExtension = WireMockExtension.newInstance()
            .options(WireMockConfiguration.options().port(9955))
            .build()
    }

    private val mapper = JacksonConfig.mapper
    private val authServer = MockOAuth2Server()
    private val authPort = 18013
    private val db = TestDatabase()
    private val appPort = ubruktPortnr()
    private val baseTemplate = "/api/rekrutteringstreff/%s/ki"

    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        authServer.start(port = authPort)

        val accessTokenClient = AccessTokenClient(
            clientId = "clientId",
            secret = "clientSecret",
            azureUrl = "http://localhost:$authPort/token",
            httpClient = httpClient
        )

        app = App(
            port = appPort,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer = "http://localhost:$authPort/default",
                    jwksUri = "http://localhost:$authPort/default/jwks",
                    audience = "rekrutteringstreff-audience"
                )
            ),
            db.dataSource,
            jobbsøkerrettet = jobbsøkerrettet,
            arbeidsgiverrettet,
            utvikler,
            kandidatsokApiUrl = "",
            kandidatsokScope = "",
            rapidsConnection = TestRapid(),
            accessTokenClient = accessTokenClient,
            modiaKlient = ModiaKlient(
                modiaContextHolderUrl = wmInfo.httpBaseUrl,
                modiaContextHolderScope = "",
                accessTokenClient = accessTokenClient,
                httpClient = httpClient
            ),
            pilotkontorer = listOf("1234")
        ).also { it.start() }
    }

    @BeforeEach
    fun setupStubs() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "aktivEnhet": "1234"
                            }
                            """.trimIndent()
                        )
                )
        )
    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    @Test
    fun validerer_tekst_og_returnerer_logg_id() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val base = baseTemplate.format(treffId)
        val requestBody = """
            {
              "treffId": "$treffId",
              "feltType": "tittel",
              "tekst": "Dette er en uskyldig tittel"
            }
        """.trimIndent()
        val response = httpPost("http://localhost:$appPort$base/valider", requestBody, token.serialize())
        assertThat(response.statusCode()).isEqualTo(HTTP_OK)
        val dto = mapper.readValue(response.body(), ValiderMedLoggResponseDto::class.java)
        assertThat(dto.loggId).isNotBlank()
    }

    @Test
    fun filtrerer_personsensitiv_info_for_OpenAI_og_logger_original_og_filtrert__og_returnerer_lagret_promptmeta() {
        val fodselsnummer = "12345678901"
        val originalTekst = "Vi ser kun etter deltakere fra Oslo med fødselsnummer $fodselsnummer."
        val forventetFiltrertTekst = "Vi ser kun etter deltakere fra Oslo med fødselsnummer ."
        val begrunnelseFraOpenAi =
            "Beskrivelsen spesifiserer et geografisk område for søkere, noe som kan være diskriminerende."

        val responseBody = """
        {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "{ \"bryterRetningslinjer\": true, \"begrunnelse\": \"$begrunnelseFraOpenAi\" }"
              }
            }
          ]
        }
        """.trimIndent()

        wireMockServer.stubFor(
            post(
                urlEqualTo(TEST_OPENAI_PATH)
            )
                .withRequestBody(containing(forventetFiltrertTekst))
                .withRequestBody(not(WireMock.containing(fodselsnummer)))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )

        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val base = baseTemplate.format(treffId)

        val requestBody = """
        {
          "treffId": "$treffId",
          "feltType": "tittel",
          "tekst": "$originalTekst"
        }
        """.trimIndent()

        val postRes = httpPost("http://localhost:$appPort$base/valider", requestBody, token.serialize())

        assertThat(postRes.statusCode()).isEqualTo(HTTP_OK)
        val dto = mapper.readValue(postRes.body(), ValiderMedLoggResponseDto::class.java)
        assertThat(dto.loggId).isNotBlank()
        assertThat(dto.bryterRetningslinjer).isTrue()
        assertThat(dto.begrunnelse).isEqualTo(begrunnelseFraOpenAi)

        // Fake en eldre promptmeta og lagre i DB for denne loggen
        val oldMeta = """
            {
              "promptVersjonsnummer": 0,
              "promptEndretTidspunkt": "2024-05-01T08:00:00+02:00[Europe/Oslo]",
              "promptHash": "deadbe"
            }
        """.trimIndent()
        oppdaterEkstra(UUID.fromString(dto.loggId), oldMeta)

        val getRes = httpGet("http://localhost:$appPort$base/logg/${dto.loggId}", token.serialize())

        assertThat(getRes.statusCode()).isEqualTo(HTTP_OK)
        val logg = mapper.readValue(getRes.body(), KiLoggOutboundDto::class.java)

        val sentToOpenAi: String = try {
            wireMockServer.serveEvents.serveEvents
                .filter { it.request.url.contains(TEST_OPENAI_PATH) }
                .joinToString("\n----\n") { it.request.bodyAsString }
        } catch (_: Throwable) {
            "<could not capture WireMock serve events>"
        }

        assertThat(logg.spørringFraFrontend.contains(fodselsnummer)).isTrue()
        assertThat(logg.spørringFiltrert.contains(fodselsnummer)).isFalse()
        assertThat(
            logg.spørringFiltrert.contains("fødselsnummer .") ||
                    logg.spørringFiltrert.contains(forventetFiltrertTekst)
        ).isTrue()
        assertThat(sentToOpenAi.contains(fodselsnummer)).isFalse()

        assertThat(logg.promptVersjonsnummer).isEqualTo(0)
        assertThat(logg.promptHash).isEqualTo("deadbe")
        assertThat(logg.promptEndretTidspunkt).isEqualTo(ZonedDateTime.parse("2024-05-01T08:00:00+02:00[Europe/Oslo]"))

        wireMockServer.verify(
            1,
            postRequestedFor(
                urlEqualTo(TEST_OPENAI_PATH)
            )
                .withRequestBody(WireMock.containing(forventetFiltrertTekst))
                .withRequestBody(WireMock.not(WireMock.containing(fodselsnummer)))
        )
    }

    @Test
    fun returnerer_opprettet_logglinje_med_id__og_lagret_promptmeta() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val loggId = opprettLogg(treffId, token)
        val base = baseTemplate.format(treffId)

        val fake = """
            {
              "promptVersjonsnummer": 0,
              "promptEndretTidspunkt": "2024-06-02T10:00:00+02:00[Europe/Oslo]",
              "promptHash": "beef00"
            }
        """.trimIndent()
        oppdaterEkstra(UUID.fromString(loggId), fake)

        val getRes = httpGet("http://localhost:$appPort$base/logg/$loggId", token.serialize())
        assertThat(getRes.statusCode()).isEqualTo(HTTP_OK)
        val logg = mapper.readValue(getRes.body(), KiLoggOutboundDto::class.java)

        assertThat(logg.id).isEqualTo(loggId)
        assertThat(logg.treffId).isEqualTo(treffId.toString())
        assertThat(logg.feltType).isEqualTo("tittel")
        assertThat(logg.bryterRetningslinjer).isFalse()

        assertThat(logg.promptVersjonsnummer).isEqualTo(0)
        assertThat(logg.promptHash).isEqualTo("beef00")
        assertThat(logg.promptEndretTidspunkt).isEqualTo(ZonedDateTime.parse("2024-06-02T10:00:00+02:00[Europe/Oslo]"))
    }

    @Test
    fun markerer_logglinje_som_lagret() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val base = baseTemplate.format(treffId)
        val loggId = opprettLogg(treffId, token)
        val putRes = httpPut("http://localhost:$appPort$base/logg/$loggId/lagret", """{"lagret": true}""", token.serialize())
        assertThat(putRes.statusCode()).isEqualTo(HTTP_OK)
        assertThat(hentLagret(UUID.fromString(loggId))).isTrue()
    }

    @Test
    fun registrerer_resultat_av_manuell_kontroll_for_logglinje() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val base = baseTemplate.format(treffId)
        val loggId = opprettLogg(treffId, token)
        val putRes = httpPut("http://localhost:$appPort$base/logg/$loggId/manuell", """{"bryterRetningslinjer": true}""", token.serialize())
        assertThat(putRes.statusCode()).isEqualTo(HTTP_OK)
        val manuell = hentManuell(UUID.fromString(loggId))
        assertThat(manuell.bryter).isTrue()
        assertThat(manuell.utfortAv).isEqualTo(navIdent)
        assertThat(manuell.tidspunkt).isNotNull()
    }

    @Test
    fun lister_logglinjer_for_valgt_treff__null_promptmeta_for_eldre_rad() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val loggId = opprettLogg(treffId, token)
        val base = baseTemplate.format(treffId)

        oppdaterEkstra(UUID.fromString(loggId), null) // fjerne ekstra

        val listRes = httpGet("http://localhost:$appPort$base/logg?treffId=$treffId", token.serialize())
        val type = mapper.typeFactory.constructCollectionType(List::class.java, KiLoggOutboundDto::class.java)
        assertThat(listRes.statusCode()).isEqualTo(HTTP_OK)
        val items: List<KiLoggOutboundDto> = mapper.readValue(listRes.body(), type)
        val row = items.first { it.id == loggId }

        assertThat(row.promptVersjonsnummer).isNull()
        assertThat(row.promptHash).isNull()
        assertThat(row.promptEndretTidspunkt).isNull()
    }

    fun forbudteKiEndepunkt(): Stream<Arguments> = Stream.of(
        Arguments.of("GET", "/logg?limit=10&offset=0"),
        Arguments.of("GET", "/logg/123"),
        Arguments.of("PUT", "/logg/123/manuell")
    )

    @ParameterizedTest(name = "{index}: {0} {1} -> 403")
    @MethodSource("forbudteKiEndepunkt")
    fun arbeidsgiverrettet_faar_403_pa_ki_logg_endepunkt(method: String, path: String) {
        val base = "/api/rekrutteringstreff/123455/ki"

        val token = authServer.lagToken(authPort, navIdent = "A123456", groups = listOf(arbeidsgiverrettet))
        val url = "http://localhost:$appPort$base$path"

        val res = when (method) {
            "GET" -> httpGet(url, token.serialize())
            "POST" -> httpPost(url, "", token.serialize())
            "PUT" -> httpPut(url, "", token.serialize())
            else -> error("Unsupported method: $method")
        }
        assertThat(res.statusCode()).isEqualTo(403)
    }

    @Test
        fun validerer_tekst_med_nytt_endepunkt_og_returnerer_logg_id() {
            stubOpenAi()
            val navIdent = "A123456"
            val token = authServer.lagToken(authPort, navIdent = navIdent)
            val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
            val requestBody = """
                {
                  "feltType": "tittel",
                  "tekst": "Dette er en uskyldig tittel"
                }
            """.trimIndent()
            val baseNew = baseTemplate.format(treffId)
            val response = httpPost("http://localhost:$appPort$baseNew/valider", requestBody, token.serialize())
            assertThat(response.statusCode()).isEqualTo(HTTP_OK)
            val dto = mapper.readValue(response.body(), ValiderMedLoggResponseDto::class.java)
            assertThat(dto.loggId).isNotBlank()
        }

        @Test
        fun lister_logglinjer_med_nytt_endepunkt() {
            stubOpenAi()
            val navIdent = "A123456"
            val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
            val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
            val loggId = opprettLogg(treffId, token)
            val base = baseTemplate.format(treffId)
            val listRes = httpGet("http://localhost:$appPort$base/logg", token.serialize())
            val type = mapper.typeFactory.constructCollectionType(List::class.java, KiLoggOutboundDto::class.java)
            assertThat(listRes.statusCode()).isEqualTo(HTTP_OK)
            val items: List<KiLoggOutboundDto> = mapper.readValue(listRes.body(), type)
            assertThat(items.any { it.id == loggId }).isTrue()
        }

        private fun stubOpenAi(bryter: Boolean = false, begrunnelse: String = "OK") {
        val responseBody = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "{ \"bryterRetningslinjer\": ${bryter}, \"begrunnelse\": \"${begrunnelse}\" }"
                  }
                }
              ]
            }
        """.trimIndent()

        wireMockServer.stubFor(
            post(
                urlEqualTo(
                    TEST_OPENAI_PATH
                )
            )
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )
    }

    private fun opprettLogg(treffId: UUID, token: SignedJWT): String {
        val requestBody = """
            {
              "treffId": "$treffId",
              "feltType": "tittel",
              "tekst": "En uskyldig tittel"
            }
        """.trimIndent()
        val base = baseTemplate.format(treffId)

        val response = httpPost("http://localhost:$appPort$base/valider", requestBody, token.serialize())

        require(response.statusCode() == HTTP_OK) { "Opprett logg feilet med ${response.statusCode()}" }
        val dto = mapper.readValue(response.body(), ValiderMedLoggResponseDto::class.java)
        return dto.loggId
    }

    private fun oppdaterEkstra(id: UUID, nyJson: String?) {
        db.dataSource.connection.use { c ->
            c.prepareStatement("update ki_spørring_logg set ekstra_parametre = cast(? as jsonb) where id = ?").use { ps ->
                if (nyJson == null) ps.setNull(1, Types.OTHER) else ps.setString(1, nyJson)
                ps.setObject(2, id)
                ps.executeUpdate()
            }
        }
    }

    private fun hentLagret(id: UUID): Boolean =
        db.dataSource.connection.use { c ->
            c.prepareStatement("select lagret from ki_spørring_logg where id = ?").use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        }

    private data class Manuell(val bryter: Boolean?, val utfortAv: String?, val tidspunkt: ZonedDateTime?)

    private fun hentManuell(id: UUID): Manuell =
        db.dataSource.connection.use { c ->
            c.prepareStatement(
                """
                select manuell_kontroll_bryter_retningslinjer,
                       manuell_kontroll_utført_av,
                       manuell_kontroll_tidspunkt
                from ki_spørring_logg where id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs ->
                    rs.next()
                    Manuell(
                        bryter = rs.getObject(1) as Boolean?,
                        utfortAv = rs.getString(2),
                        tidspunkt = rs.getTimestamp(3)?.toInstant()?.atZone(SystemPrompt.endretTidspunkt.zone)
                    )
                }
            }
        }

}