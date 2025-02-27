package no.nav.toi.rekrutteringstreff

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.ObjectMapperProvider.mapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffTest {

    companion object {
        @JvmStatic
        @RegisterExtension
        val wireMockServer: WireMockExtension = WireMockExtension.newInstance()
            .options(WireMockConfiguration.options().port(9955))
            .build()
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val appPort = 10000
    private val repo = RekrutteringstreffRepository(database.dataSource)

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
        val gyldigKontorfelt = "Gyldig NAV Kontor"
        val gyldigStatus = Status.Utkast
        val gyldigFraTid = nowOslo().minusDays(1)
        val gyldigTilTid = nowOslo().plusDays(1).plusHours(2)
        val gyldigSted = "Gyldig Sted"
        val gyldigBeskrivelse = "Beskrivelse for oppretting"
        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff")
            .body(
                """
                {
                    "tittel": "$gyldigTittelfelt",
                    "beskrivelse": "$gyldigBeskrivelse",
                    "opprettetAvPersonNavident": "$navIdent",
                    "opprettetAvNavkontorEnhetId": "$gyldigKontorfelt",
                    "opprettetAvTidspunkt": "${nowOslo().minusDays(10)}",
                    "fraTid": "$gyldigFraTid",
                    "tilTid": "$gyldigTilTid",
                    "sted": "$gyldigSted"
                }
                """.trimIndent()
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(201)
                val rekrutteringstreff = database.hentAlleRekrutteringstreff().first()
                assertThat(rekrutteringstreff.tittel).isEqualTo(gyldigTittelfelt)
                assertThat(rekrutteringstreff.beskrivelse).isEqualTo(gyldigBeskrivelse)
                assertThat(rekrutteringstreff.fraTid).isEqualTo(gyldigFraTid)
                assertThat(rekrutteringstreff.tilTid).isEqualTo(gyldigTilTid)
                assertThat(rekrutteringstreff.sted).isEqualTo(gyldigSted)
                assertThat(rekrutteringstreff.status).isEqualTo(gyldigStatus.name)
                assertThat(rekrutteringstreff.opprettetAvNavkontorEnhetId).isEqualTo(gyldigKontorfelt)
                assertThat(rekrutteringstreff.opprettetAvPersonNavident).isEqualTo(navIdent)
            }
        }
    }

    @Test
    fun hentAlleRekrutteringstreff() {
        val tittel1 = "Tittel1111111"
        val sted1 = "Sted1"
        val beskrivelse1 = "Beskrivelse 1"
        val tittel2 = "Tittel2222222"
        val sted2 = "Sted2"
        val beskrivelse2 = "Beskrivelse 2"
        opprettRekrutteringstreffIDatabase(
            navIdent = "navident1",
            tittel = tittel1,
            sted = sted1,
            beskrivelse = beskrivelse1
        )
        opprettRekrutteringstreffIDatabase(
            navIdent = "navIdent2",
            tittel = tittel2,
            sted = sted2,
            beskrivelse = beskrivelse2
        )

        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)

        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<RekrutteringstreffDTO>> {
                override fun deserialize(content: String): List<RekrutteringstreffDTO> {
                    val type =
                        mapper.typeFactory.constructCollectionType(List::class.java, RekrutteringstreffDTO::class.java)
                    return mapper.readValue(content, type)
                }
            })

        when (result) {
            is Failure<*> -> throw result.error
            is Success<List<RekrutteringstreffDTO>> -> {
                assertThat(response.statusCode).isEqualTo(200)
                val liste = result.get()
                assertThat(liste).hasSize(2)
                val dto1 = liste.find { it.tittel == tittel1 }!!
                assertThat(dto1.sted).isEqualTo(sted1)
                assertThat(dto1.beskrivelse).isEqualTo(beskrivelse1)
                val dto2 = liste.find { it.tittel == tittel2 }!!
                assertThat(dto2.sted).isEqualTo(sted2)
                assertThat(dto2.beskrivelse).isEqualTo(beskrivelse2)
            }
        }
    }

    @Test
    fun hentRekrutteringstreff() {
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val originalTittel = "Spesifikk Tittel"
        val originalSted = "Sted"
        val originalBeskrivelse = "Spesifikk beskrivelse"

        opprettRekrutteringstreffIDatabase(
            navIdent,
            tittel = originalTittel,
            sted = originalSted,
            beskrivelse = originalBeskrivelse
        )
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readValue(result.get(), RekrutteringstreffDTO::class.java)
                assertThat(dto.tittel).isEqualTo(originalTittel)
                assertThat(dto.sted).isEqualTo(originalSted)
                assertThat(dto.beskrivelse).isEqualTo(originalBeskrivelse)
            }
        }
    }

    @Test
    fun oppdaterRekrutteringstreff() {
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val created = database.hentAlleRekrutteringstreff().first()
        val updateDto = OppdaterRekrutteringstreffDto(
            tittel = "Oppdatert Tittel",
            beskrivelse = "Oppdatert beskrivelse",
            fraTid = nowOslo().minusHours(2),
            tilTid = nowOslo().plusHours(3),
            sted = "Oppdatert Sted"
        )
        val (_, updateResponse, updateResult) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${created.id}")
            .body(mapper.writeValueAsString(updateDto))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (updateResult) {
            is Failure -> throw updateResult.error
            is Success -> {
                assertThat(updateResponse.statusCode).isEqualTo(200)
                val updatedDto = mapper.readValue(updateResult.get(), RekrutteringstreffDTO::class.java)
                assertThat(updatedDto.tittel).isEqualTo(updateDto.tittel)
                assertThat(updatedDto.sted).isEqualTo(updateDto.sted)
                assertThat(updatedDto.beskrivelse).isEqualTo(updateDto.beskrivelse)
            }
        }
    }

    @Test
    fun slettRekrutteringstreff() {
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val (_, deleteResponse, deleteResult) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (deleteResult) {
            is Failure -> throw deleteResult.error
            is Success -> {
                assertThat(deleteResponse.statusCode).isEqualTo(200)
            }
        }
        val remaining = database.hentAlleRekrutteringstreff()
        assertThat(remaining).isEmpty()
    }


    @Test
    fun validerRekrutteringstreff() {
        val openAiResponseBody = """
             {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "{ \"bryterRetningslinjer\": true, \"begrunnelse\": \"Tittelen eller beskrivelsen inneholder potensielt diskriminerende uttrykk.\" }"
                  }
                }
              ]
            }
        """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post(WireMock.urlEqualTo("/openai/deployments/toi-gpt-4o/chat/completions?api-version=2023-03-15-preview"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiResponseBody)
                )
        )

        val token = lagToken(navIdent = "A123456")
        val payload = """
            {
                "tittel": "Kritisk Tittel",
                "beskrivelse": "Denne beskrivelsen kan oppfattes som diskriminerende."
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/valider")
            .body(payload)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val validationResult = mapper.readValue(result.get(), ValiderRekrutteringstreffResponsDto::class.java)
                assertThat(validationResult.bryterRetningslinjer).isTrue()
                assertThat(validationResult.begrunnelse).isEqualTo("Tittelen eller beskrivelsen inneholder potensielt diskriminerende uttrykk.")
            }
        }
    }

    private fun opprettRekrutteringstreffIDatabase(
        navIdent: String,
        tittel: String = "Original Tittel",
        sted: String = "Original Sted",
        beskrivelse: String? = "Original Beskrivelse"
    ) {
        val originalDto = OpprettRekrutteringstreffDto(
            tittel = tittel,
            beskrivelse = beskrivelse,
            opprettetAvNavkontorEnhetId = "Original Kontor",
            opprettetAvPersonNavident = navIdent,
            opprettetAvTidspunkt = nowOslo().minusDays(10),
            fraTid = nowOslo().minusDays(1),
            tilTid = nowOslo().plusDays(1),
            sted = sted
        )
        repo.opprett(originalDto, navIdent)
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
        expiry = expiry,
        audience = "rekrutteringstreff-audience"
    )

    @Test
    fun autentiseringOpprett() {
        val (_, response, _) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff")
            .body(
                """
                {
                    "tittel": "No Auth",
                    "beskrivelse": "Ingen autorisasjon",
                    "opprettetAvNavkontorEnhetId": "Test",
                    "fraTid": "${nowOslo()}",
                    "tilTid": "${nowOslo()}",
                    "sted": "Test"
                }
                """.trimIndent()
            )
            .header("Authorization", "Bearer invalidtoken")
            .responseString()
        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun autentiseringHentAlle() {
        val (_, response, _) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff")
            .header("Authorization", "Bearer invalidtoken")
            .responseString()
        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun autentiseringHentEnkelt() {
        val dummyId = UUID.randomUUID().toString()
        val (_, response, _) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/$dummyId")
            .header("Authorization", "Bearer invalidtoken")
            .responseString()
        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun autentiseringOppdater() {
        val dummyId = UUID.randomUUID().toString()
        val updateDto = OppdaterRekrutteringstreffDto(
            tittel = "Updated",
            beskrivelse = "Oppdatert beskrivelse",
            fraTid = nowOslo(),
            tilTid = nowOslo(),
            sted = "Updated"
        )
        val (_, response, _) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/$dummyId")
            .body(mapper.writeValueAsString(updateDto))
            .header("Authorization", "Bearer invalidtoken")
            .responseString()
        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun autentiseringSlett() {
        val dummyId = UUID.randomUUID().toString()
        val (_, response, result) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/$dummyId")
            .header("Authorization", "Bearer invalidtoken")
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }
}
