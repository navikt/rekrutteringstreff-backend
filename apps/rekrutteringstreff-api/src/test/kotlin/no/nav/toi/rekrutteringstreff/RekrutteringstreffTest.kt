package no.nav.toi.rekrutteringstreff

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.ArbeidsgiverStatus
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.dto.FellesHendelseOutboundDto
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
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
    fun opprettRekrutteringstreff() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val gyldigKontorfelt = "Gyldig NAV Kontor"
        val gyldigStatus = RekrutteringstreffStatus.UTKAST
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
                assertThat(rekrutteringstreff.status.name).isEqualTo(gyldigStatus.name)
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
            .responseObject(object : ResponseDeserializable<List<RekrutteringstreffDto>> {
                override fun deserialize(content: String): List<RekrutteringstreffDto> {
                    val type =
                        mapper.typeFactory.constructCollectionType(List::class.java, RekrutteringstreffDto::class.java)
                    return mapper.readValue(content, type)
                }
            })

        when (result) {
            is Failure<*> -> throw result.error
            is Success<List<RekrutteringstreffDto>> -> {
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
                val updatedDto = mapper.readValue(updateResult.get(), RekrutteringstreffDto::class.java)
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
    fun slettRekrutteringstreffMedUpublisertedata() {
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
    fun `slett rekrutteringstreff feiler (409) hvis treffet er publisert og har jobbsøkerinformasjon`() {
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
                    orgnavn = Orgnavn("Testbedrift AS"),
                    status = ArbeidsgiverStatus.AKTIV,
                )
            )
        )

        assertThat(db.hentAlleJobbsøkere()).isNotEmpty
        assertThat(db.hentAlleArbeidsgivere()).isNotEmpty

        val (_, response, _) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${treff.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertThat(response.statusCode).isEqualTo(409)
        // Verifiser at data står igjen når sletting avvises
        assertThat(db.hentAlleRekrutteringstreff()).isNotEmpty
        assertThat(db.hentAlleJobbsøkere()).isNotEmpty
        assertThat(db.hentAlleArbeidsgivere()).isNotEmpty
    }

    @Test
    fun `slett rekrutteringstreff feiler (409) etter publisering uansett hvilke data den har`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        db.opprettRekrutteringstreffIDatabase(navIdent)
        val treff = db.hentAlleRekrutteringstreff().first()

        // Publiser treffet
        val (_, pubRes, _) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/${treff.id}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        assertThat(pubRes.statusCode).isEqualTo(200)

        // Forsøk å slette
        val (_, delRes, _) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${treff.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        assertThat(delRes.statusCode).isEqualTo(409)

        // Treffet skal fortsatt eksistere
        assertThat(db.hentAlleRekrutteringstreff()).isNotEmpty
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
        result as Success
        val list = result.value
        assertThat(list.map { it.hendelsestype }).containsExactly("OPPDATERT", "OPPRETTET")
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
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = result.value
                assertThat(dto.hendelser).hasSize(1)
                assertThat(dto.hendelser.first().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETTET.name)
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
                    Orgnavn("Test AS"),
                    ArbeidsgiverStatus.AKTIV,
                )
            )
        )

        db.leggTilRekrutteringstreffHendelse(treff, RekrutteringstreffHendelsestype.OPPDATERT, "A123456")

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
        assertThat(list.map { it.hendelsestype }).containsExactlyInAnyOrder("OPPRETTET", "OPPRETTET", "OPPRETTET", "OPPDATERT")
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
        Arguments.of("publiser", RekrutteringstreffHendelsestype.PUBLISERT),
        Arguments.of("gjenapn", RekrutteringstreffHendelsestype.GJENÅPNET),
        Arguments.of("avlys", RekrutteringstreffHendelsestype.AVLYST),
        Arguments.of("avpubliser", RekrutteringstreffHendelsestype.AVPUBLISERT),
        Arguments.of("fullfor", RekrutteringstreffHendelsestype.FULLFØRT)
    )

    @ParameterizedTest
    @MethodSource("hendelseEndepunktVarianter")
    fun `Endepunkter som kun legger til hendelse`(
        path: String,
        forventetHendelsestype: RekrutteringstreffHendelsestype
    ) {
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
        assertThat(hendelser.last().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETTET)
    }

    @Test
    fun `avlys oppretter hendelse for rekrutteringstreff og alle jobbsøkere med aktivt svar ja`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)

        // Legg til tre jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            kandidatnummer = Kandidatnummer("K1"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            kandidatnummer = Kandidatnummer("K2"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        val jobbsøker3 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("34567890123"),
            kandidatnummer = Kandidatnummer("K3"),
            fornavn = Fornavn("Per"),
            etternavn = Etternavn("Hansen"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2, jobbsøker3))

        // Jobbsøker1 og jobbsøker2 svarer ja
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)

        // Jobbsøker3 svarer ja og så nei (ombestemt seg)
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker3.fødselsnummer, treffId, jobbsøker3.fødselsnummer.asString)
        jobbsøkerRepository.svarNeiTilInvitasjon(jobbsøker3.fødselsnummer, treffId, jobbsøker3.fødselsnummer.asString)

        // Avlys treffet via endpoint
        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/avlys")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertStatuscodeEquals(200, response, result)

        // Verifiser at rekrutteringstreff har AVLYST hendelse
        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.AVLYST)

        // Verifiser at kun jobbsøker1 og jobbsøker2 har SVART_JA_TREFF_AVLYST hendelse
        val alleJobbsøkerHendelser = db.hentJobbsøkerHendelser(treffId)
        val jobbsøker1Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker1.fødselsnummer
        }
        val jobbsøker2Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker2.fødselsnummer
        }
        val jobbsøker3Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker3.fødselsnummer
        }

        assertThat(jobbsøker1Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
        assertThat(jobbsøker2Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
        assertThat(jobbsøker3Hendelser.map { it.hendelsestype }).doesNotContain(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
    }

    @Test
    fun `fullfor oppretter hendelse for rekrutteringstreff og alle jobbsøkere med aktivt svar ja`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)

        // Legg til to jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            kandidatnummer = Kandidatnummer("K1"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            kandidatnummer = Kandidatnummer("K2"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2))

        // Begge svarer ja
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)

        // Fullfør treffet via endpoint
        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/fullfor")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertStatuscodeEquals(200, response, result)

        // Verifiser at rekrutteringstreff har FULLFØRT hendelse
        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.FULLFØRT)

        // Verifiser at begge jobbsøkere har SVART_JA_TREFF_FULLFØRT hendelse
        val alleJobbsøkerHendelser = db.hentJobbsøkerHendelser(treffId)
        val jobbsøker1Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker1.fødselsnummer
        }
        val jobbsøker2Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker2.fødselsnummer
        }

        assertThat(jobbsøker1Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)
        assertThat(jobbsøker2Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)
    }

    @Test
    fun `avlys oppretter kun rekrutteringstreff-hendelse når ingen jobbsøkere har aktivt svar ja`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)

        // Legg til en jobbsøker som ikke svarer
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            kandidatnummer = Kandidatnummer("K1"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1))

        // Avlys treffet via endpoint
        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/avlys")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertStatuscodeEquals(200, response, result)

        // Verifiser at rekrutteringstreff har AVLYST hendelse
        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.AVLYST)

        // Verifiser at jobbsøkeren IKKE har SVART_JA_TREFF_AVLYST hendelse
        val alleJobbsøkerHendelser = db.hentJobbsøkerHendelser(treffId)
        val jobbsøker1Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker1.fødselsnummer
        }
        assertThat(jobbsøker1Hendelser.map { it.hendelsestype }).doesNotContain(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
    }

    @Test
    fun `fullfor oppretter kun rekrutteringstreff-hendelse når ingen jobbsøkere har aktivt svar ja`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)

        // Fullfør treffet uten jobbsøkere via endpoint
        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/fullfor")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertStatuscodeEquals(200, response, result)

        // Verifiser at rekrutteringstreff har FULLFØRT hendelse
        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.FULLFØRT)
    }


    @Test
    fun `registrer endring oppretter hendelser for publisert treff med inviterte jobbsøkere`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)

        // Legg til jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            kandidatnummer = Kandidatnummer("K1"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            kandidatnummer = Kandidatnummer("K2"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2))

        // Inviter jobbsøkerne
        jobbsøkerRepository.inviter(listOf(jobbsøker1.personTreffId, jobbsøker2.personTreffId), treffId, navIdent)

        // Publiser treffet
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        // Registrer endringer
        val endringerDto = """
            {
                "gamleVerdierForEndringer": {
                    "tittel": {"value": "Gammel tittel", "endret": true},
                    "beskrivelse": {"value": "Gammel beskrivelse", "endret": true},
                    "fraTid": {"value": "2025-10-30T10:00:00+01:00", "endret": true},
                    "tilTid": {"value": null, "endret": false},
                    "svarfrist": {"value": null, "endret": false},
                    "gateadresse": {"value": null, "endret": false},
                    "postnummer": {"value": null, "endret": false},
                    "poststed": {"value": null, "endret": false},
                    "innlegg": {"value": null, "endret": false}
                }
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringerDto)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        assertStatuscodeEquals(201, response, result)

        // Verifiser at rekrutteringstreff har TREFF_ENDRET_ETTER_PUBLISERING hendelse
        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING)

        // Verifiser at begge jobbsøkere har TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON hendelse
        val alleJobbsøkerHendelser = db.hentJobbsøkerHendelser(treffId)
        val jobbsøker1Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker1.fødselsnummer
        }
        val jobbsøker2Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker2.fødselsnummer
        }

        assertThat(jobbsøker1Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
        assertThat(jobbsøker2Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `registrer endring oppretter hendelser for publisert treff med jobbsøkere som har svart ja`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)

        // Legg til jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            kandidatnummer = Kandidatnummer("K1"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            kandidatnummer = Kandidatnummer("K2"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2))

        // Inviter og svar ja
        jobbsøkerRepository.inviter(listOf(jobbsøker1.personTreffId, jobbsøker2.personTreffId), treffId, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)

        // Publiser treffet
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        // Registrer endringer
        val endringerDto = """
            {
                "gamleVerdierForEndringer": {
                    "tittel": {"value": "Gammel tittel", "endret": true},
                    "beskrivelse": {"value": null, "endret": false},
                    "fraTid": {"value": null, "endret": false},
                    "tilTid": {"value": null, "endret": false},
                    "svarfrist": {"value": null, "endret": false},
                    "gateadresse": {"value": "Gammel gate 1", "endret": true},
                    "postnummer": {"value": "0566", "endret": true},
                    "poststed": {"value": "Oslo", "endret": true},
                    "innlegg": {"value": null, "endret": false}
                }
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringerDto)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        assertStatuscodeEquals(201, response, result)

        // Verifiser at rekrutteringstreff har TREFF_ENDRET_ETTER_PUBLISERING hendelse
        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING)

        // Verifiser at begge jobbsøkere har TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON hendelse
        val alleJobbsøkerHendelser = db.hentJobbsøkerHendelser(treffId)
        val jobbsøker1Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker1.fødselsnummer
        }
        val jobbsøker2Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker2.fødselsnummer
        }

        assertThat(jobbsøker1Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
        assertThat(jobbsøker2Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `registrer endring varsler ikke jobbsøkere som har svart nei`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)

        // Legg til tre jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            kandidatnummer = Kandidatnummer("K1"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            kandidatnummer = Kandidatnummer("K2"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        val jobbsøker3 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("34567890123"),
            kandidatnummer = Kandidatnummer("K3"),
            fornavn = Fornavn("Per"),
            etternavn = Etternavn("Hansen"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2, jobbsøker3))

        // Inviter alle
        jobbsøkerRepository.inviter(
            listOf(jobbsøker1.personTreffId, jobbsøker2.personTreffId, jobbsøker3.personTreffId),
            treffId,
            navIdent
        )

        // Jobbsøker1 svarer ja
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)

        // Jobbsøker2 svarer ja og så nei (ombestemt seg)
        jobbsøkerRepository.svarJaTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)
        jobbsøkerRepository.svarNeiTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)

        // Publiser treffet
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        // Registrer endringer
        val endringerDto = """
            {
                "gamleVerdierForEndringer": {
                    "tittel": {"value": null, "endret": false},
                    "beskrivelse": {"value": null, "endret": false},
                    "fraTid": {"value": null, "endret": false},
                    "tilTid": {"value": "2025-10-30T14:00:00+01:00", "endret": true},
                    "svarfrist": {"value": null, "endret": false},
                    "gateadresse": {"value": null, "endret": false},
                    "postnummer": {"value": null, "endret": false},
                    "poststed": {"value": null, "endret": false},
                    "innlegg": {"value": null, "endret": false}
                }
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringerDto)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        assertStatuscodeEquals(201, response, result)

        // Verifiser at kun jobbsøker1 og jobbsøker3 (invitert) får notifikasjon
        val alleJobbsøkerHendelser = db.hentJobbsøkerHendelser(treffId)
        val jobbsøker1Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker1.fødselsnummer
        }
        val jobbsøker2Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker2.fødselsnummer
        }
        val jobbsøker3Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker3.fødselsnummer
        }

        assertThat(jobbsøker1Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
        assertThat(jobbsøker2Hendelser.map { it.hendelsestype }).doesNotContain(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
        assertThat(jobbsøker3Hendelser.map { it.hendelsestype }).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `registrer endring fungerer uavhengig av publiseringsstatus`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)

        // Legg til jobbsøker
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            kandidatnummer = Kandidatnummer("K1"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent)
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1))
        jobbsøkerRepository.inviter(listOf(jobbsøker1.personTreffId), treffId, navIdent)

        // Registrer endringer (fungerer nå uavhengig av status)
        val endringerDto = """
            {
                "gamleVerdierForEndringer": {
                    "tittel": {"value": null, "endret": false},
                    "beskrivelse": {"value": "Gammel beskrivelse", "endret": true},
                    "fraTid": {"value": null, "endret": false},
                    "tilTid": {"value": null, "endret": false},
                    "svarfrist": {"value": null, "endret": false},
                    "gateadresse": {"value": null, "endret": false},
                    "postnummer": {"value": null, "endret": false},
                    "poststed": {"value": null, "endret": false},
                    "innlegg": {"value": null, "endret": false}
                }
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringerDto)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        assertStatuscodeEquals(201, response, result)

        // Verifiser at hendelse er opprettet
        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING)
    }


    @Test
    fun `registrer endring oppretter kun rekrutteringstreff-hendelse når ingen jobbsøkere skal varsles`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)

        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        val endringerDto = """
            {
                "endringer": {
                    "tittel": "Gammel tittel"
                }
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringerDto)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        assertStatuscodeEquals(201, response, result)

        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING)

        val alleJobbsøkerHendelser = db.hentJobbsøkerHendelser(treffId)
        assertThat(alleJobbsøkerHendelser).isEmpty()
    }
}
