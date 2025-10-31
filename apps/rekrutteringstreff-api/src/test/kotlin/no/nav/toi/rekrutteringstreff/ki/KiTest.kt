package no.nav.toi.rekrutteringstreff.ki

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.ki.dto.KiLoggOutboundDto
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.sql.Types
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Stream

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
    private val oldBase = "/api/rekrutteringstreff/ki"
    private val newBaseTemplate = "/api/rekrutteringstreff/%s/ki"
    private val base = oldBase

    private val app = App(
        port = appPort,
        authConfigs = listOf(
            AuthenticationConfiguration(
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
                audience = "rekrutteringstreff-audience"
            )
        ),
        db.dataSource,
        arbeidsgiverrettet,
        utvikler,
        kandidatsokApiUrl = "",
        kandidatsokScope = "",
        azureClientId = "",
        azureClientSecret = "",
        azureTokenEndpoint = "",
        TestRapid(),
        httpClient = httpClient
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
    }

    @Test
    fun validerer_tekst_og_returnerer_logg_id() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val requestBody = """
            {
              "treffId": "$treffId",
              "feltType": "tittel",
              "tekst": "Dette er en uskyldig tittel"
            }
        """.trimIndent()
        val (_, response, result) = Fuel.post("http://localhost:$appPort$base/valider")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<ValiderMedLoggResponseDto> {
                override fun deserialize(content: String): ValiderMedLoggResponseDto =
                    mapper.readValue(content, ValiderMedLoggResponseDto::class.java)
            })
        assertThat(response.statusCode).isEqualTo(200)
        result as Result.Success
        assertThat(result.value.loggId).isNotBlank()
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
            WireMock.post(
                WireMock.urlEqualTo("/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview")
            )
                .withRequestBody(WireMock.containing(forventetFiltrertTekst))
                .withRequestBody(WireMock.not(WireMock.containing(fodselsnummer)))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )

        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid

        val requestBody = """
        {
          "treffId": "$treffId",
          "feltType": "tittel",
          "tekst": "$originalTekst"
        }
        """.trimIndent()

        val (_, postRes, postResult) = Fuel.post("http://localhost:$appPort$base/valider")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<ValiderMedLoggResponseDto> {
                override fun deserialize(content: String): ValiderMedLoggResponseDto =
                    mapper.readValue(content, ValiderMedLoggResponseDto::class.java)
            })

        assertThat(postRes.statusCode).isEqualTo(200)
        postResult as Result.Success
        val dto = postResult.value
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

        val (_, getRes, getResult) = Fuel.get("http://localhost:$appPort$base/logg/${dto.loggId}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<KiLoggOutboundDto> {
                override fun deserialize(content: String): KiLoggOutboundDto =
                    mapper.readValue(content, KiLoggOutboundDto::class.java)
            })

        assertThat(getRes.statusCode).isEqualTo(200)
        getResult as Result.Success
        val logg = getResult.value

        val sentToOpenAi: String = try {
            wireMockServer.serveEvents.serveEvents
                .filter { it.request.url.contains("/openai/deployments/toi-gpt-4o/chat/completions") }
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
            WireMock.postRequestedFor(
                WireMock.urlEqualTo("/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview")
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

        val fake = """
            {
              "promptVersjonsnummer": 0,
              "promptEndretTidspunkt": "2024-06-02T10:00:00+02:00[Europe/Oslo]",
              "promptHash": "beef00"
            }
        """.trimIndent()
        oppdaterEkstra(UUID.fromString(loggId), fake)

        val (_, getRes, getResult) = Fuel.get("http://localhost:$appPort$base/logg/$loggId")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<KiLoggOutboundDto> {
                override fun deserialize(content: String): KiLoggOutboundDto =
                    mapper.readValue(content, KiLoggOutboundDto::class.java)
            })
        assertThat(getRes.statusCode).isEqualTo(200)
        getResult as Result.Success
        val logg = getResult.value

        assertThat(logg.id).isEqualTo(loggId)
        assertThat(logg.treffId).isEqualTo(treffId.toString())
        assertThat(logg.feltType).isEqualTo("tittel")
        assertThat(logg.bryterRetningslinjer).isFalse()

        assertThat(logg.promptVersjonsnummer).isEqualTo(0)
        assertThat(logg.promptHash).isEqualTo("beef00")
        assertThat(logg.promptEndretTidspunkt).isEqualTo(ZonedDateTime.parse("2024-06-02T10:00:00+02:00[Europe/Oslo]"))
    }

    @Test
    fun lister_logglinjer_for_alle_treff_nar_treffId_er_utelatt__og_respekterer_lagret_promptmeta_eller_null() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))

        val treffId1 = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val treffId2 = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val id1 = opprettLogg(treffId1, token)
        Thread.sleep(5)
        val id2 = opprettLogg(treffId2, token)

        val meta1 = """
            {
              "promptVersjonsnummer": 0,
              "promptEndretTidspunkt": "2024-04-10T09:00:00+02:00[Europe/Oslo]",
              "promptHash": "c0ffee"
            }
        """.trimIndent()
        oppdaterEkstra(UUID.fromString(id1), meta1)
        oppdaterEkstra(UUID.fromString(id2), null) // simuler gammel rad uten ekstra -> null

        val (_, listRes, listResult) = Fuel.get("http://localhost:$appPort$base/logg?limit=50&offset=0")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<KiLoggOutboundDto>> {
                override fun deserialize(content: String): List<KiLoggOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(
                        List::class.java,
                        KiLoggOutboundDto::class.java
                    )
                    return mapper.readValue(content, type)
                }
            })

        assertThat(listRes.statusCode).isEqualTo(200)
        listResult as Result.Success
        val alle = listResult.value

        assertThat(alle.map { it.id }).containsExactly(id2, id1) // rekkefølge (nyeste først)
        assertThat(alle.size).isEqualTo(2)

        val byId = alle.associateBy { it.id }
        val r1 = byId[id1]!!
        val r2 = byId[id2]!!

        assertThat(r1.promptVersjonsnummer).isEqualTo(0)
        assertThat(r1.promptHash).isEqualTo("c0ffee")
        assertThat(r1.promptEndretTidspunkt).isEqualTo(ZonedDateTime.parse("2024-04-10T09:00:00+02:00[Europe/Oslo]"))

        assertThat(r2.promptVersjonsnummer).isNull()
        assertThat(r2.promptHash).isNull()
        assertThat(r2.promptEndretTidspunkt).isNull()
    }

    @Test
    fun markerer_logglinje_som_lagret() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val loggId = opprettLogg(treffId, token)
        val (_, putRes, _) = Fuel.put("http://localhost:$appPort$base/logg/$loggId/lagret")
            .header("Authorization", "Bearer ${token.serialize()}")
            .body("""{"lagret": true}""")
            .response()
        assertThat(putRes.statusCode).isEqualTo(200)
        assertThat(hentLagret(UUID.fromString(loggId))).isTrue()
    }

    @Test
    fun registrerer_resultat_av_manuell_kontroll_for_logglinje() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val loggId = opprettLogg(treffId, token)
        val (_, putRes, _) = Fuel.put("http://localhost:$appPort$base/logg/$loggId/manuell")
            .header("Authorization", "Bearer ${token.serialize()}")
            .body("""{"bryterRetningslinjer": true}""")
            .response()
        assertThat(putRes.statusCode).isEqualTo(200)
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

        oppdaterEkstra(UUID.fromString(loggId), null) // fjerne ekstra

        val (_, listRes, listResult) = Fuel.get("http://localhost:$appPort$base/logg?treffId=$treffId")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<KiLoggOutboundDto>> {
                override fun deserialize(content: String): List<KiLoggOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(
                        List::class.java,
                        KiLoggOutboundDto::class.java
                    )
                    return mapper.readValue(content, type)
                }
            })
        assertThat(listRes.statusCode).isEqualTo(200)
        listResult as Result.Success
        val items = listResult.value
        val row = items.first { it.id == loggId }

        assertThat(row.promptVersjonsnummer).isNull()
        assertThat(row.promptHash).isNull()
        assertThat(row.promptEndretTidspunkt).isNull()
    }

    fun forbudteKiEndepunkt(): Stream<Arguments> = Stream.of(
        Arguments.of("GET", "/logg?limit=10&offset=0"),
        Arguments.of("GET", "/logg/123"),
        Arguments.of("PUT", "/logg/123/lagret"),
        Arguments.of("PUT", "/logg/123/manuell")
    )

    @ParameterizedTest(name = "{index}: {0} {1} -> 403")
    @MethodSource("forbudteKiEndepunkt")
    fun arbeidsgiverrettet_faar_403_pa_alle_ki_logg_endepunkt(method: String, path: String) {
        val token = authServer.lagToken(authPort, navIdent = "A123456", groups = listOf(arbeidsgiverrettet))
        val url = "http://localhost:$appPort$base$path"

        val (_, res, _) = when (method) {
            "GET" -> Fuel.get(url)
            "POST" -> Fuel.post(url)
            "PUT" -> Fuel.put(url)
            else -> error("Unsupported method: $method")
        }.header("Authorization", "Bearer ${token.serialize()}").response()
        assertThat(res.statusCode).isEqualTo(403)
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
            val baseNew = newBaseTemplate.format(treffId)
            val (_, response, result) = Fuel.post("http://localhost:$appPort$baseNew/valider")
                .body(requestBody)
                .header("Authorization", "Bearer ${token.serialize()}")
                .responseObject(object : ResponseDeserializable<ValiderMedLoggResponseDto> {
                    override fun deserialize(content: String): ValiderMedLoggResponseDto =
                        mapper.readValue(content, ValiderMedLoggResponseDto::class.java)
                })
            assertThat(response.statusCode).isEqualTo(200)
            result as Result.Success
            assertThat(result.value.loggId).isNotBlank()
        }

        @Test
        fun lister_logglinjer_med_nytt_endepunkt() {
            stubOpenAi()
            val navIdent = "A123456"
            val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
            val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
            val loggId = opprettLogg(treffId, token)
            val baseNew = newBaseTemplate.format(treffId)
            val (_, listRes, listResult) = Fuel.get("http://localhost:$appPort$baseNew/logg")
                .header("Authorization", "Bearer ${token.serialize()}")
                .responseObject(object : ResponseDeserializable<List<KiLoggOutboundDto>> {
                    override fun deserialize(content: String): List<KiLoggOutboundDto> {
                        val type = mapper.typeFactory.constructCollectionType(
                            List::class.java,
                            KiLoggOutboundDto::class.java
                        )
                        return mapper.readValue(content, type)
                    }
                })
            assertThat(listRes.statusCode).isEqualTo(200)
            listResult as Result.Success
            assertThat(listResult.value.any { it.id == loggId }).isTrue()
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
            WireMock.post(
                WireMock.urlEqualTo(
                    "/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview"
                )
            )
                .willReturn(
                    WireMock.aResponse()
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

        val (_, response, result) = Fuel.post("http://localhost:$appPort$base/valider")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<ValiderMedLoggResponseDto> {
                override fun deserialize(content: String): ValiderMedLoggResponseDto =
                    mapper.readValue(content, ValiderMedLoggResponseDto::class.java)
            })

        require(response.statusCode == 200) { "Opprett logg feilet med ${response.statusCode}" }
        result as Result.Success
        return result.value.loggId
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