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
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffTest {

    val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

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
        utvikler,
        kandidatsokApiUrl = "",
        kandidatsokScope = "",
        azureClientId = "",
        azureClientSecret = "",
        azureTokenEndpoint = "",
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
                assertThat(rekrutteringstreff.svarfrist).isNull()
                assertThat(rekrutteringstreff.gateadresse).isNull()
                assertThat(rekrutteringstreff.postnummer).isNull()
                assertThat(rekrutteringstreff.poststed).isNull()
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
                val dto = mapper.readValue(result.get(), RekrutteringstreffDetaljOutboundDto::class.java)
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
            svarfrist = nowOslo().minusDays(1),
            gateadresse = "Oppdatert Gateadresse",
            postnummer = "1234",
            poststed = "Oslo"
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
                assertThat(updatedDto.beskrivelse).isEqualTo(updateDto.beskrivelse)
                assertThat(updatedDto.fraTid).isEqualTo(updateDto.fraTid)
                assertThat(updatedDto.tilTid).isEqualTo(updateDto.tilTid)
                assertThat(updatedDto.svarfrist).isEqualTo(updateDto.svarfrist)
                assertThat(updatedDto.gateadresse).isEqualTo(updateDto.gateadresse)
                assertThat(updatedDto.postnummer).isEqualTo(updateDto.postnummer)
                assertThat(updatedDto.poststed).isEqualTo(updateDto.poststed)

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

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    personTreffId = PersonTreffId(UUID.randomUUID()),
                    treffId = treff.id,
                    fødselsnummer = Fødselsnummer("01010112345"),
                    fornavn = Fornavn("Kari"),
                    etternavn = Etternavn("Nordmann"),
                    kandidatnummer = null,
                    navkontor = null,
                    veilederNavn = null,
                    veilederNavIdent = null
                )
            )
        )
        db.leggTilArbeidsgivere(
            listOf(
                Arbeidsgiver(
                    arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
                    treffId = treff.id,
                    orgnr = Orgnr("999888777"),
                    orgnavn = Orgnavn("Testbedrift AS")
                )
            )
        )

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
        val validationResponseBody = """
             {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "{ \"bryterRetningslinjer\": true, \"begrunnelse\": \"Beskrivelsen spesifiserer et geografisk område for søkere, noe som kan være diskriminerende.\" }"
                  }
                }
              ]
            }
        """.trimIndent()

        val sanitizationResponseBody = """
             {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "{ \"bryterRetningslinjer\": true, \"begrunnelse\": \"Teksten spesifiserer et geografisk krav for kandidatene, noe som kan være diskriminerende.\" }"
                  }
                }
              ]
            }
        """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post(WireMock.urlEqualTo("/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview"))
                .inScenario("Validation and Sanitization")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(validationResponseBody)
                )
                .willSetStateTo("Validation Complete")
        )

        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val payload = """
            {
                "tekst": "Vi ser kun etter deltakere fra Oslo."
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
                assertThat(validationResult.begrunnelse).isEqualTo("Beskrivelsen spesifiserer et geografisk område for søkere, noe som kan være diskriminerende.")
            }
        }
    }

    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @Test
    fun `GET hendelser gir 200 og sortert liste`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val id = db.opprettRekrutteringstreffIDatabase("A123456")
        Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${id.somUuid}")
            .body(
                """{"tittel":"x","beskrivelse":null,"fraTid":"${nowOslo()}",
                 "tilTid":"${nowOslo()}","svarfrist":"${nowOslo().minusDays(1)}","gateadresse":"y","postnummer":"1234"},"poststed":"Bergen"} """
            )
            .header("Authorization", "Bearer ${token.serialize()}").response()

        val (_, res, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${id.somUuid}/hendelser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<RekrutteringstreffHendelseOutboundDto>> {
                override fun deserialize(content: String): List<RekrutteringstreffHendelseOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(
                        List::class.java, RekrutteringstreffHendelseOutboundDto::class.java
                    )
                    return mapper.readValue(content, type)
                }
            })

        assertThat(res.statusCode).isEqualTo(200)
        result as com.github.kittinunf.result.Result.Success
        val list = result.value
        assertThat(list.map { it.hendelsestype }).containsExactly("OPPDATER", "OPPRETT")
    }

    @Test
    fun `hent rekrutteringstreff returnerer hendelser`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)

        val id = db.opprettRekrutteringstreffIDatabase(navIdent)

        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${id.somUuid}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<RekrutteringstreffDetaljOutboundDto> {
                override fun deserialize(content: String): RekrutteringstreffDetaljOutboundDto =
                    mapper.readValue(content, RekrutteringstreffDetaljOutboundDto::class.java)
            })

        when (result) {
            is com.github.kittinunf.result.Result.Failure -> throw result.error
            is com.github.kittinunf.result.Result.Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = result.value
                assertThat(dto.hendelser).hasSize(1)
                assertThat(dto.hendelser.first().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETT.name)
            }
        }
    }

    @Test
    fun `hent alle hendelser returnerer sortert kombinert liste`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treff = db.opprettRekrutteringstreffIDatabase("A123456")

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    personTreffId = PersonTreffId(UUID.randomUUID()),
                    treff,
                    Fødselsnummer("11111111111"),
                    null,
                    Fornavn("Ola"),
                    Etternavn("N"),
                    null,
                    null,
                    null
                )
            )
        )

        db.leggTilArbeidsgivere(
            listOf(
                Arbeidsgiver(
                    arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
                    treff,
                    Orgnr("999888777"),
                    Orgnavn("Test AS")
                )
            )
        )

        db.leggTilRekrutteringstreffHendelse(treff, RekrutteringstreffHendelsestype.OPPDATER, "A123456")

        val (_, res, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/allehendelser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<FellesHendelseOutboundDto>> {
                override fun deserialize(content: String): List<FellesHendelseOutboundDto> =
                    mapper.readValue(
                        content,
                        mapper.typeFactory.constructCollectionType(
                            List::class.java,
                            FellesHendelseOutboundDto::class.java
                        )
                    )
            })

        assertThat(res.statusCode).isEqualTo(200)
        result as Success
        val list = result.value
        assertThat(list).hasSize(4)
        assertThat(list).isSortedAccordingTo(compareByDescending<FellesHendelseOutboundDto> { it.tidspunkt })
        assertThat(list.map { it.ressurs }).containsExactlyInAnyOrder(
            HendelseRessurs.REKRUTTERINGSTREFF,
            HendelseRessurs.REKRUTTERINGSTREFF,
            HendelseRessurs.JOBBSØKER,
            HendelseRessurs.ARBEIDSGIVER
        )
        assertThat(list.map { it.hendelsestype }).containsExactlyInAnyOrder("OPPRETT", "OPPRETT", "OPPRETT", "OPPDATER")
    }

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
            svarfrist = nowOslo(),
            gateadresse = "Updated Gateadresse",
            postnummer = "5678",
            poststed = "Bergen"
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

    private fun hendelseEndepunktVarianter() = listOf(
        Arguments.of("publiser", RekrutteringstreffHendelsestype.PUBLISER),
        Arguments.of("avslutt-invitasjon", RekrutteringstreffHendelsestype.AVSLUTT_INVITASJON),
        Arguments.of("avslutt-arrangement", RekrutteringstreffHendelsestype.AVSLUTT_ARRANGEMENT),
        Arguments.of("avslutt-oppfolging", RekrutteringstreffHendelsestype.AVSLUTT_OPPFØLGING),
        Arguments.of("avslutt", RekrutteringstreffHendelsestype.AVSLUTT)
    )

    @ParameterizedTest
    @MethodSource("hendelseEndepunktVarianter")
    fun `Endepunkter som kun legger til hendelse`(path: String, forventetHendelsestype: RekrutteringstreffHendelsestype) {
        val navIdent = "Z999999"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/$path")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertStatuscodeEquals(200, response, result)

        val hendelser = db.hentHendelser(treffId)
        assertThat(hendelser).hasSize(2)
        assertThat(hendelser.first().hendelsestype).isEqualTo(forventetHendelsestype)
        assertThat(hendelser.first().aktørIdentifikasjon).isEqualTo(navIdent)
        assertThat(hendelser.last().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETT)
    }
}