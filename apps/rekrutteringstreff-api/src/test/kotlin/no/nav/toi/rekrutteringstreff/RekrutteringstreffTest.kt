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
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffTest {

    private val mapper = JacksonConfig.mapper


    companion object {
        @JvmStatic
        @RegisterExtension
        val wireMockServer: WireMockExtension = WireMockExtension.newInstance()
            .options(WireMockConfiguration.options().port(9955))
            .build()
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val db = TestDatabase()
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
        db.dataSource,
        arbeidsgiverrettet,
        utvikler
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
    fun opprettRekrutteringstreff() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val gyldigKontorfelt = "Gyldig NAV Kontor"
        val gyldigStatus = Status.Utkast
        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff")
            .body(
                """
                {
                    "opprettetAvNavkontorEnhetId": "$gyldigKontorfelt"
                }
                """.trimIndent()
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(201)
                val json = mapper.readTree(result.get())
                val postId = json.get("id").asText()
                assertThat(postId).isNotEmpty()

                val rekrutteringstreff = db.hentAlleRekrutteringstreff().first()
                assertThat(rekrutteringstreff.tittel).isEqualTo("Nytt rekrutteringstreff")
                assertThat(rekrutteringstreff.beskrivelse).isNull()
                assertThat(rekrutteringstreff.fraTid).isNull()
                assertThat(rekrutteringstreff.tilTid).isNull()
                assertThat(rekrutteringstreff.sted).isNull()
                assertThat(rekrutteringstreff.status).isEqualTo(gyldigStatus.name)
                assertThat(rekrutteringstreff.opprettetAvNavkontorEnhetId).isEqualTo(gyldigKontorfelt)
                assertThat(rekrutteringstreff.opprettetAvPersonNavident).isEqualTo(navIdent)
                assertThat(rekrutteringstreff.id.somString).isEqualTo(postId)
            }
        }
    }

    @Test
    fun hentAlleRekrutteringstreff() {
        val tittel1 = "Tittel1111111"
        val tittel2 = "Tittel2222222"
        db.opprettRekrutteringstreffIDatabase(
            navIdent = "navident1",
            tittel = tittel1,
        )
        db.opprettRekrutteringstreffIDatabase(
            navIdent = "navIdent2",
            tittel = tittel2,
        )

        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)

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
                assertThat(liste.map { it.tittel }).contains(tittel1, tittel2)
            }
        }
    }

    @Test
    fun hentRekrutteringstreff() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val originalTittel = "Spesifikk Tittel"

        db.opprettRekrutteringstreffIDatabase(
            navIdent,
            tittel = originalTittel,
        )
        val opprettetRekrutteringstreff = db.hentAlleRekrutteringstreff().first()
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readValue(result.get(), RekrutteringstreffDTO::class.java)
                assertThat(dto.tittel).isEqualTo(originalTittel)
            }
        }
    }

    @Test
    fun oppdaterRekrutteringstreff() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        db.opprettRekrutteringstreffIDatabase(navIdent)
        val created = db.hentAlleRekrutteringstreff().first()
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
    fun slettRekrutteringstreffUtenCascade() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        db.opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = db.hentAlleRekrutteringstreff().first()
        val (_, response, result) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val remaining = db.hentAlleRekrutteringstreff()
                assertThat(remaining).isEmpty()
            }
        }
    }

    @Test
    fun `cascade delete av jobbsøkere og arbeidsgivere ved sletting av rekrutteringstreff`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)

        db.opprettRekrutteringstreffIDatabase(navIdent)
        val treff = db.hentAlleRekrutteringstreff().first()

        db.leggTilJobbsøkere(listOf(Jobbsøker(
            treffId = treff.id,
            fødselsnummer = Fødselsnummer("01010112345"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            kandidatnummer = null,
            navkontor = null,
            veilederNavn = null,
            veilederNavIdent = null
        )))
        db.leggTilArbeidsgivere(listOf(Arbeidsgiver(
            treffId = treff.id,
            orgnr = Orgnr("999888777"),
            orgnavn = Orgnavn("Testbedrift AS")
        )))

        assertThat(db.hentAlleJobbsøkere()).isNotEmpty
        assertThat(db.hentAlleArbeidsgivere()).isNotEmpty

        val (_, response, result) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${treff.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)

                assertThat(db.hentAlleRekrutteringstreff()).isEmpty()

                assertThat(db.hentAlleJobbsøkere()).isEmpty()
                assertThat(db.hentAlleArbeidsgivere()).isEmpty()
            }
        }
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

        val token = authServer.lagToken(authPort, navIdent = "A123456")
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


    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringOpprett(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff")
            .body(
                """
                {
                    "opprettetAvNavkontorEnhetId": "Test",
                }
                """.trimIndent()
            )
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringHentAlle(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringHentEnkelt(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val dummyId = UUID.randomUUID().toString()
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/$dummyId")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringOppdater(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val dummyId = UUID.randomUUID().toString()
        val updateDto = OppdaterRekrutteringstreffDto(
            tittel = "Updated",
            beskrivelse = "Oppdatert beskrivelse",
            fraTid = nowOslo(),
            tilTid = nowOslo(),
            sted = "Updated"
        )
        val (_, response, result) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/$dummyId")
            .body(mapper.writeValueAsString(updateDto))
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringSlett(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val dummyId = UUID.randomUUID().toString()
        val (_, response, result) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/$dummyId")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(401, response, result)
    }
}
