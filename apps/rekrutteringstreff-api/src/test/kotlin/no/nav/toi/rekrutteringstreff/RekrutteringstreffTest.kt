package no.nav.toi.rekrutteringstreff

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.dto.FellesHendelseOutboundDto
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class RekrutteringstreffTest {

    val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

    private val mapper = JacksonConfig.mapper

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val db = TestDatabase()
    private val appPort = ubruktPortnr()

    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        authServer.start(port = authPort)
        val accessTokenClient = AccessTokenClient(
            clientId = "client-id",
            secret = "secret",
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
            dataSource = db.dataSource,
            jobbsøkerrettet = jobbsøkerrettet,
            arbeidsgiverrettet = arbeidsgiverrettet,
            utvikler = utvikler,
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
            pilotkontorer = listOf("1234"),
            leaderElection = LeaderElectionMock(),
        ).also { it.start() }

    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @BeforeEach
    fun setUpStubs() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .willReturn(
                    WireMock.aResponse()
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
                assertThat(rekrutteringstreff.sistEndretAv).isEqualTo(navIdent)
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
                val rekrutteringstreff = mapper.readValue(result.get(), RekrutteringstreffDto::class.java)
                assertThat(rekrutteringstreff.tittel).isEqualTo(originalTittel)
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
            poststed = "Oslo",
            kommune = "Oppdatert Kommune",
            kommunenummer = "0301",
            fylke = "Oppdatert fylke",
            fylkesnummer = "03",
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
                assertThat(updatedDto.sistEndretAv).isEqualTo(navIdent)
                assertThat(created.sistEndret).isBefore(updatedDto.sistEndret)
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
                val remaining = db.hentAlleRekrutteringstreffSomIkkeErSlettet()
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
                    navkontor = null,
                    veilederNavn = null,
                    veilederNavIdent = null,
                    status = JobbsøkerStatus.LAGT_TIL,
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
                    gateadresse = "Fyrstikkalleen 1",
                    postnummer = "0661",
                    poststed = "Oslo",
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
        assertThat(db.hentAlleRekrutteringstreffSomIkkeErSlettet()).isNotEmpty
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
    fun `hent alle hendelser returnerer sortert kombinert liste`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treff = db.opprettRekrutteringstreffIDatabase("A123456")

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    personTreffId = PersonTreffId(UUID.randomUUID()),
                    treffId = treff,
                    fødselsnummer = Fødselsnummer("11111111111"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("N"),
                    navkontor = null,
                    veilederNavn = null,
                    veilederNavIdent = null,
                    status = JobbsøkerStatus.LAGT_TIL,
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
                    "Fyrstikkalleen 1",
                    "0661",
                    "Oslo",
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

        // Verifiser subjektId og subjektNavn for jobbsøker-hendelser
        val jobbsøkerHendelse = list.find { it.ressurs == HendelseRessurs.JOBBSØKER }
        assertThat(jobbsøkerHendelse?.subjektId).isEqualTo("11111111111")
        assertThat(jobbsøkerHendelse?.subjektNavn).isEqualTo("Ola N")

        // Verifiser subjektId og subjektNavn for arbeidsgiver-hendelser
        val arbeidsgiverHendelse = list.find { it.ressurs == HendelseRessurs.ARBEIDSGIVER }
        assertThat(arbeidsgiverHendelse?.subjektId).isEqualTo("999888777")
        assertThat(arbeidsgiverHendelse?.subjektNavn).isEqualTo("Test AS")

        // Verifiser at rekrutteringstreff-hendelser har null for subjektId og subjektNavn
        val treffHendelser = list.filter { it.ressurs == HendelseRessurs.REKRUTTERINGSTREFF }
        assertThat(treffHendelser).allSatisfy {
            assertThat(it.subjektId).isNull()
            assertThat(it.subjektNavn).isNull()
        }
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
            poststed = "Bergen",
            kommune = "Updated Kommune",
            kommunenummer = "1201",
            fylke = "Updated fylke",
            fylkesnummer = "12",
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

        if (path == "fullfor") {
            // For å kunne fullføre må treffet være publisert først
            db.endreTilTidTilPassert(treffId, navIdent)
            db.publiser(treffId, navIdent)
        }

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/$path")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertStatuscodeEquals(200, response, result)

        val hendelser = db.hentHendelser(treffId)
        if (path == "fullfor") {
            assertThat(hendelser).hasSize(4)
        } else {
            assertThat(hendelser).hasSize(2)
        }
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
        val jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)

        // Legg til tre jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        val jobbsøker3 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("34567890123"),
            fornavn = Fornavn("Per"),
            etternavn = Etternavn("Hansen"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2, jobbsøker3))

        // Jobbsøker1 og jobbsøker2 svarer ja
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)

        // Jobbsøker3 svarer ja og så nei (ombestemt seg)
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker3.fødselsnummer, treffId, jobbsøker3.fødselsnummer.asString)
        jobbsøkerService.svarNeiTilInvitasjon(jobbsøker3.fødselsnummer, treffId, jobbsøker3.fødselsnummer.asString)

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
        val jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)

        // Legg til to jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2))

        // Begge svarer ja
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)

        // Publiser treffet
        db.publiser(treffId, "navIdent")
        db.endreTilTidTilPassert(treffId, "navIdent")

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
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
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

        // Publiser treffet
        db.endreTilTidTilPassert(treffId, navIdent)
        db.publiser(treffId, navIdent)

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
        val jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)

        // Legg til jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2))

        // Inviter jobbsøkerne
        jobbsøkerService.inviter(listOf(jobbsøker1.personTreffId, jobbsøker2.personTreffId), treffId, navIdent)

        // Publiser treffet
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        // Registrer endringer
        val endringer = """
            {
                "navn": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel", "skalVarsle": true},
                "tidspunkt": {"gammelVerdi": "2025-10-30T10:00:00+01:00", "nyVerdi": "2025-10-30T14:00:00+01:00", "skalVarsle": true},
                "introduksjon": {"gammelVerdi": "Gammel beskrivelse", "nyVerdi": "Ny beskrivelse", "skalVarsle": false}
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringer)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        assertStatuscodeEquals(201, response, result)

        // Verifiser at rekrutteringstreff har TREFF_ENDRET_ETTER_PUBLISERING hendelse
        val treffHendelser = db.hentHendelser(treffId)
        assertThat(treffHendelser.map { it.hendelsestype }).contains(RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING)

        // Verifiser at jobbsøker ikke har TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON hendelse
        val alleJobbsøkerHendelser = db.hentJobbsøkerHendelser(treffId)
        val jobbsøker1Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker1.fødselsnummer
        }
        val jobbsøker2Hendelser = alleJobbsøkerHendelser.filter {
            db.hentFødselsnummerForJobbsøkerHendelse(it.id) == jobbsøker2.fødselsnummer
        }

        assertThat(jobbsøker1Hendelser.map { it.hendelsestype }).doesNotContain(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
        assertThat(jobbsøker2Hendelser.map { it.hendelsestype }).doesNotContain(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `registrer endring oppretter hendelser for publisert treff med jobbsøkere som har svart ja`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        val jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)

        // Legg til jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2))

        // Inviter og svar ja
        jobbsøkerService.inviter(listOf(jobbsøker1.personTreffId, jobbsøker2.personTreffId), treffId, navIdent)
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)

        // Publiser treffet
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        // Registrer endringer
        val endringer = """
            {
                "navn": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel", "skalVarsle": true},
                "sted": {"gammelVerdi": "Gammel gate 1, 0566 Oslo", "nyVerdi": "Ny gate 2, 0567 Oslo", "skalVarsle": true}
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringer)
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
        val jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)

        // Legg til tre jobbsøkere
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        val jobbsøker2 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("23456789012"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        val jobbsøker3 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("34567890123"),
            fornavn = Fornavn("Per"),
            etternavn = Etternavn("Hansen"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2, jobbsøker3))

        // Inviter alle
        jobbsøkerService.inviter(
            listOf(jobbsøker1.personTreffId, jobbsøker2.personTreffId, jobbsøker3.personTreffId),
            treffId,
            navIdent
        )

        // Jobbsøker1 svarer ja
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)

        // Jobbsøker2 svarer ja og så nei (ombestemt seg)
        jobbsøkerService.svarJaTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)
        jobbsøkerService.svarNeiTilInvitasjon(jobbsøker2.fødselsnummer, treffId, jobbsøker2.fødselsnummer.asString)

        // Publiser treffet
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        // Registrer endringer
        val endringer = """
            {
                "tidspunkt": {"gammelVerdi": "2025-10-30T10:00:00+01:00 - 2025-10-30T12:00:00+01:00", "nyVerdi": "2025-10-30T10:00:00+01:00 - 2025-10-30T14:00:00+01:00", "skalVarsle": true}
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringer)
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
        assertThat(jobbsøker3Hendelser.map { it.hendelsestype }).doesNotContain(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `registrer endring avvises for upubliserte treff`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        val jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)

        // Legg til jobbsøker
        val jobbsøker1 = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            fornavn = Fornavn("Ola"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder"),
            veilederNavIdent = VeilederNavIdent(navIdent),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker1))
        jobbsøkerService.inviter(listOf(jobbsøker1.personTreffId), treffId, navIdent)

        // Prøv å registrere endringer på upublisert treff (skal avvises)
        val endringer = """
            {
                "introduksjon": {"gammelVerdi": "Gammel beskrivelse", "nyVerdi": "Ny beskrivelse", "skalVarsle": true}
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringer)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        // Skal returnere 400 Bad Request fordi treffet ikke er publisert
        assertStatuscodeEquals(400, response, result)
    }

    @Test
    fun `registrer endring avvises for fullførte treff`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)
        db.endreTilTidTilPassert(treffId, navIdent)

        // Publiser og fullfør treffet
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/fullfor")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        // Prøv å registrere endringer på fullført treff (skal avvises)
        val endringer = """
            {
                "navn": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel", "skalVarsle": true}
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringer)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        // Skal returnere 400 Bad Request fordi treffet er fullført
        assertStatuscodeEquals(400, response, result)
    }

    @Test
    fun `registrer endring avvises for avlyste treff`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)

        // Publiser og avlys treffet
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()
        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/avlys")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        // Prøv å registrere endringer på avlyst treff (skal avvises)
        val endringer = """
            {
                "navn": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel", "skalVarsle": true}
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringer)
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .response()

        // Skal returnere 400 Bad Request fordi treffet er avlyst
        assertStatuscodeEquals(400, response, result)
    }

    @Test
    fun `registrer endring oppretter kun rekrutteringstreff-hendelse når ingen jobbsøkere skal varsles`() {
        val navIdent = "A123456"
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent)

        Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/publiser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        val endringer = """
            {
                "navn": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel", "skalVarsle": true}
            }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort$endepunktRekrutteringstreff/${treffId.somUuid}/endringer")
            .body(endringer)
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
