// kotlin
package no.nav.toi.rekrutteringstreff.ki

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.extensions.cUrlString
import com.github.kittinunf.result.Result
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneOffset
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
    private val base = "/api/rekrutteringstreff/ki"

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
        TestRapid()
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
        val (req, response, result) = Fuel.post("http://localhost:$appPort$base/valider")
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
    fun filtrerer_personsensitiv_info_for_OpenAI_og_logger_original_og_filtrert() {
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

        println(
            """
        [KI debug]
        Original: <${logg.spørringFraFrontend}>
        Filtered: <${logg.spørringFiltrert}>
        Expected filtered: <${forventetFiltrertTekst}>
        FNR: <${fodselsnummer}>
        Sent to OpenAI:
        $sentToOpenAi
        """.trimIndent()
        )

        assertThat(logg.spørringFraFrontend.contains(fodselsnummer)).isTrue()
        assertThat(logg.spørringFiltrert.contains(fodselsnummer)).isFalse()
        assertThat(
            logg.spørringFiltrert.contains("fødselsnummer .") ||
                    logg.spørringFiltrert.contains(forventetFiltrertTekst)
        ).isTrue()
        assertThat(sentToOpenAi.contains(fodselsnummer)).isFalse()

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
    fun returnerer_opprettet_logglinje_med_id() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val loggId = opprettLogg(treffId, token)
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
    }

    @Test
    fun lister_logglinjer_for_alle_treff_nar_treffId_er_utelatt() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))

        val treffId1 = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val treffId2 = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid

        val id1 = opprettLogg(treffId1, token)
        Thread.sleep(5)
        val id2 = opprettLogg(treffId2, token)

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

        assertThat(alle.map { it.id }).containsExactly(id2, id1)
        assertThat(alle.size).isEqualTo(2)
        assertThat(alle.map { it.treffId }.toSet())
            .containsExactlyInAnyOrder(treffId1.toString(), treffId2.toString())
    }

    @Test
    fun markerer_logglinje_som_lagret() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val loggId = opprettLogg(treffId, token)
        val (_, patchRes, _) = Fuel.patch("http://localhost:$appPort$base/logg/$loggId/lagret")
            .header("Authorization", "Bearer ${token.serialize()}")
            .body("""{"lagret": true}""")
            .response()
        assertThat(patchRes.statusCode).isEqualTo(204)
        assertThat(hentLagret(UUID.fromString(loggId))).isTrue()
    }

    @Test
    fun registrerer_resultat_av_manuell_kontroll_for_logglinje() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val loggId = opprettLogg(treffId, token)
        val (_, patchRes, _) = Fuel.patch("http://localhost:$appPort$base/logg/$loggId/manuell")
            .header("Authorization", "Bearer ${token.serialize()}")
            .body("""{"bryterRetningslinjer": true}""")
            .response()
        assertThat(patchRes.statusCode).isEqualTo(204)
        val manuell = hentManuell(UUID.fromString(loggId))
        assertThat(manuell.bryter).isTrue()
        assertThat(manuell.utfortAv).isEqualTo(navIdent)
        assertThat(manuell.tidspunkt).isNotNull()
    }

    @Test
    fun lister_logglinjer_for_valgt_treff() {
        stubOpenAi()
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = listOf(utvikler))
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent).somUuid
        val loggId = opprettLogg(treffId, token)
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
        assertThat(listResult.value.any { it.id == loggId }).isTrue()
    }

    fun forbudteKiEndepunkt(): Stream<Arguments> = Stream.of(
        Arguments.of("GET", "/logg?limit=10&offset=0"),
        Arguments.of("GET", "/logg/123"),
        Arguments.of("PATCH", "/logg/123/lagret"),
        Arguments.of("PATCH", "/logg/123/manuell")
    )

    @ParameterizedTest(name = "{index}: {0} {1} -> 403")
    @MethodSource("forbudteKiEndepunkt")
    fun arbeidsgiverrettet_faar_403_pa_alle_ki_logg_endepunkt(method: String, path: String) {
        val token = authServer.lagToken(authPort, navIdent = "A123456", groups = listOf(arbeidsgiverrettet))
        val url = "http://localhost:$appPort$base$path"

        val (_, res, _) = when (method) {
            "GET" -> Fuel.get(url)
            "PATCH" -> Fuel.patch(url)
            "POST" -> Fuel.post(url)
            else -> error("Unsupported method: $method")
        }.header("Authorization", "Bearer ${token.serialize()}").response()
        assertThat(res.statusCode).isEqualTo(403)
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

    private fun opprettLogg(treffId: UUID, token: com.nimbusds.jwt.SignedJWT): String {
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
                        tidspunkt = rs.getTimestamp(3)?.toInstant()?.atZone(ZoneOffset.UTC)
                    )
                }
            }
        }
}
