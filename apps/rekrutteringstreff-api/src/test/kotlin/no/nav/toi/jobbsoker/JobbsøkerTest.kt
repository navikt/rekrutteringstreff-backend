package no.nav.toi.jobbsoker

import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerDataOutboundDto
import no.nav.toi.jobbsoker.dto.MinsideVarselSvarDataDto
import no.nav.toi.jobbsoker.dto.RekrutteringstreffendringerDto
import no.nav.toi.jobbsoker.sok.JobbsøkerSøkRespons
import no.nav.toi.rekrutteringstreff.Endringsfelttype
import no.nav.toi.rekrutteringstreff.Rekrutteringstreffendringer
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerTest {

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18012
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()

        private lateinit var app: App

        val mapper = JacksonConfig.mapper
    }

    private val eierRepository = EierRepository(db.dataSource)

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo)  {
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
            arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
            utvikler = AzureAdRoller.utvikler,
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
            httpClient = httpClient,
            leaderElection = LeaderElectionMock(),
        ).also { it.start() }
        authServer.start(port = authPort)
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

    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringLeggTilJobbsøker(autentiseringstest: UautentifiserendeTestCase) {
        val anyTreffId = "anyTreffID"
        val response = autentiseringstest.utførPost("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/jobbsoker", "", authServer, authPort)
        assertThat(response.statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringHentJobbsøker(autentiseringstest: UautentifiserendeTestCase) {
        val anyTreffId = "anyTreffID"
        val response = autentiseringstest.utførGet("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/jobbsoker", authServer, authPort)
        assertThat(response.statusCode()).isEqualTo(HTTP_UNAUTHORIZED)
    }

    @Test
    fun leggTilJobbsøkerTest() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val fnr = Fødselsnummer("55555555555")
        val fornavn = Fornavn("Foooornavn")
        val etternavn = Etternavn("Eeeetternavn")
        val navkontor = Navkontor("Oslo")
        val veilederNavn = VeilederNavn("Test Veileder")
        val veilederNavIdent = VeilederNavIdent("NAV001")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val requestBody = """
        [{
          "fødselsnummer" : "${fnr.asString}",
          "fornavn" : "${fornavn.asString}",
          "etternavn" : "${etternavn.asString}",
          "navkontor" : "${navkontor.asString}",
          "veilederNavn" : "${veilederNavn.asString}",
          "veilederNavIdent" : "${veilederNavIdent.asString}"
        }]
        """.trimIndent()
        assertThat(db.hentAlleJobbsøkere()).isEmpty()

        val response = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker",
            requestBody,
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(HTTP_CREATED)
        val actualJobbsøkere = db.hentAlleJobbsøkere()
        assertThat(actualJobbsøkere.size).isEqualTo(1)
        actualJobbsøkere.first().also { actual ->
            assertThatCode { UUID.fromString(actual.personTreffId.toString()) }.doesNotThrowAnyException()
            assertThat(actual.treffId).isEqualTo(treffId)
            assertThat(actual.fødselsnummer).isEqualTo(fnr)
            assertThat(actual.fornavn).isEqualTo(fornavn)
            assertThat(actual.etternavn).isEqualTo(etternavn)
            assertThat(actual.navkontor).isEqualTo(navkontor)
            assertThat(actual.veilederNavn).isEqualTo(veilederNavn)
            assertThat(actual.veilederNavIdent).isEqualTo(veilederNavIdent)
        }
        val hendelser = db.hentJobbsøkerHendelser(treffId)
        assertThat(hendelser).hasSize(1)
        val h = hendelser.first()
        assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktørIdentifikasjon).isEqualTo("A123456")
    }

    @Test
    fun hentJobbsøkereTest() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId1 = db.opprettRekrutteringstreffIDatabase()
        val treffId2 = db.opprettRekrutteringstreffIDatabase()
        val treffId3 = db.opprettRekrutteringstreffIDatabase()
        val fnr1 = Fødselsnummer("11111111111")
        val fnr2 = Fødselsnummer("22222222222")
        val fnr3 = Fødselsnummer("33333333333")
        val fnr4 = Fødselsnummer("44444444444")
        val fornavn1 = Fornavn("Fornavn1")
        val fornavn2 = Fornavn("Fornavn2")
        val fornavn3 = Fornavn("Fornavn3")
        val fornavn4 = Fornavn("Fornavn4")
        val etternavn1 = Etternavn("Etternavn1")
        val etternavn2 = Etternavn("Etternavn2")
        val etternavn3 = Etternavn("Etternavn3")
        val etternavn4 = Etternavn("Etternavn4")
        val navkontor1 = Navkontor("Oslo")
        val navkontor2 = Navkontor("Bergen")
        val navkontor3 = Navkontor("Trondheim")
        val veilederNavn1 = VeilederNavn("Veileder1")
        val veilederNavn2 = VeilederNavn("Veileder2")
        val veilederNavn3 = VeilederNavn("Veileder3")
        val veilederNavIdent1 = VeilederNavIdent("NAV001")
        val veilederNavIdent2 = VeilederNavIdent("NAV002")
        val veilederNavIdent3 = VeilederNavIdent("NAV003")
        val jobbsøkere1 = listOf(
            Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId1, fnr1, fornavn1, etternavn1, navkontor1, veilederNavn1, veilederNavIdent1, JobbsøkerStatus.LAGT_TIL)
        )
        val jobbsøkere2 = listOf(
            Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId2, fnr2, fornavn2, etternavn2, navkontor1, veilederNavn1, veilederNavIdent1, JobbsøkerStatus.LAGT_TIL),
            Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId2, fnr3, fornavn3, etternavn3, navkontor2, veilederNavn2, veilederNavIdent2, JobbsøkerStatus.LAGT_TIL)
        )
        val jobbsøkere3 = listOf(
            Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId3, fnr4, fornavn4, etternavn4, navkontor3, veilederNavn3, veilederNavIdent3, JobbsøkerStatus.LAGT_TIL)
        )
        db.leggTilJobbsøkere(jobbsøkere1)
        db.leggTilJobbsøkere(jobbsøkere2)
        db.leggTilJobbsøkere(jobbsøkere3)
        assertThat(db.hentAlleRekrutteringstreff().size).isEqualTo(3)
        assertThat(db.hentAlleJobbsøkere().size).isEqualTo(4)
        eierRepository.leggTil(treffId2, listOf("A123456"))
        val response = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId2.somUuid}/jobbsoker",
            token.serialize()
        )
        assertThat(response.statusCode()).isEqualTo(HTTP_OK)
        val result = mapper.readValue(response.body(), JobbsøkerSøkRespons::class.java)
        val actualJobbsøkere = result.jobbsøkere
        assertThat(actualJobbsøkere.size).isEqualTo(2)
        actualJobbsøkere.forEach { jobbsøker ->
            assertThatCode { UUID.fromString(jobbsøker.personTreffId) }.doesNotThrowAnyException()
            assertThat(jobbsøker.status).isEqualTo(JobbsøkerStatus.LAGT_TIL)
        }
    }

    @Test
    fun hentJobbsøkerTest() {
        val token = authServer.lagToken(authPort, navIdent = "testperson")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val requestBody = """
    [{
      "fødselsnummer" : "77777777777",
      "fornavn" : "Test",
      "etternavn" : "Bruker",
      "navkontor" : "Oslo",
      "veilederNavn" : "Test Veileder",
      "veilederNavIdent" : "NAV007"
    }]
""".trimIndent()
        eierRepository.leggTil(treffId, listOf("testperson"))

        val postResponse = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker",
            requestBody,
            token.serialize()
        )
        assertThat(postResponse.statusCode()).isEqualTo(HTTP_CREATED)

        val getResponse = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker",
            token.serialize()
        )
        assertThat(getResponse.statusCode()).isEqualTo(HTTP_OK)
        val result = mapper.readValue(getResponse.body(), JobbsøkerSøkRespons::class.java)
        val actualJobbsøkere = result.jobbsøkere
        assertThat(actualJobbsøkere.size).isEqualTo(1)
        val jobbsoeker = actualJobbsøkere.first()
        assertThatCode { UUID.fromString(jobbsoeker.personTreffId) }.doesNotThrowAnyException()
        assertThat(jobbsoeker.status).isEqualTo(JobbsøkerStatus.LAGT_TIL)
        assertThat(jobbsoeker.fornavn).isEqualTo("Test")
        assertThat(jobbsoeker.etternavn).isEqualTo("Bruker")
        assertThat(jobbsoeker.navkontor).isEqualTo("Oslo")
        assertThat(jobbsoeker.veilederNavn).isEqualTo("Test Veileder")
        assertThat(jobbsoeker.veilederNavident).isEqualTo("NAV007")
    }

    @Test
    fun hentJobbsøkerHendelserTest() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val input1 = LeggTilJobbsøker(
            Fødselsnummer("11111111111"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            Navkontor("NAV Oslo"),
            VeilederNavn("Veileder1"),
            VeilederNavIdent("NAV111")
        )
        val input2 = LeggTilJobbsøker(
            Fødselsnummer("22222222222"),
            Fornavn("Kari"),
            Etternavn("Nordmann"),
            Navkontor("NAV Bergen"),
            VeilederNavn("Veileder2"),
            VeilederNavIdent("NAV222")
        )
        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, input1.fødselsnummer, input1.fornavn, input1.etternavn, input1.navkontor, input1.veilederNavn, input1.veilederNavIdent, JobbsøkerStatus.LAGT_TIL)
            )
        )
        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, input2.fødselsnummer, input2.fornavn, input2.etternavn, input2.navkontor, input2.veilederNavn, input2.veilederNavIdent,JobbsøkerStatus.LAGT_TIL)
            )
        )
        eierRepository.leggTil(treffId, listOf("A123456"))

        val response = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/hendelser",
            token.serialize()
        )
        assertThat(response.statusCode()).isEqualTo(HTTP_OK)
        val hendelser = mapper.readValue(
            response.body(),
            mapper.typeFactory.constructCollectionType(List::class.java, JobbsøkerHendelseMedJobbsøkerDataOutboundDto::class.java)
        ) as List<JobbsøkerHendelseMedJobbsøkerDataOutboundDto>
        assertThat(hendelser).hasSize(2)
        assertThat(hendelser[0].tidspunkt.toInstant()).isAfterOrEqualTo(hendelser[1].tidspunkt.toInstant())
        hendelser.forEach { h ->
            assertThatCode { UUID.fromString(h.id) }.doesNotThrowAnyException()
            assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET.name)
            assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.name)
            assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
            assertThat(h.fødselsnummer).isIn("11111111111", "22222222222")
            assertThat(h.fornavn).isIn("Ola", "Kari")
            assertThat(h.etternavn).isEqualTo("Nordmann")
            assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
            assertThat(h.personTreffId).isNotNull()
        }
    }

    @Test
    fun invitererJobbsøkere() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr1 = Fødselsnummer("12312312312")
        val fnr2 = Fødselsnummer("45645645645")

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fnr1, Fornavn("Fornavn1"), Etternavn("Etternavn1"), null, null, null, JobbsøkerStatus.LAGT_TIL),
                Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fnr2, Fornavn("Fornavn2"), Etternavn("Etternavn2"), null, null, null, JobbsøkerStatus.LAGT_TIL)
            )
        )

        assertThat(db.hentJobbsøkerHendelser(treffId)).hasSize(2)

        val jobbsøkere = db.hentAlleJobbsøkere()
        val personTreffIder = jobbsøkere.toList().map { it.personTreffId }
        assertThat(personTreffIder).hasSize(2)

        val requestBody = """
        { "personTreffIder": ["${personTreffIder.first()}", "${personTreffIder.last()}"] }
    """.trimIndent()

        eierRepository.leggTil(treffId, listOf("A123456"))

        val response = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/inviter",
            requestBody,
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(HTTP_OK)

        val hendelser = db.hentJobbsøkerHendelser(treffId)
        assertThat(hendelser).hasSize(4)

        val inviterHendelser = hendelser.filter { it.hendelsestype == JobbsøkerHendelsestype.INVITERT }
        assertThat(inviterHendelser).hasSize(2)
        inviterHendelser.forEach {
            assertThat(it.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
            assertThat(it.aktørIdentifikasjon).isEqualTo("A123456")
        }

        val inviterteFødselsnumre =
            inviterHendelser.map { db.hentFødselsnummerForJobbsøkerHendelse(it.id)}

        assertThat(inviterteFødselsnumre)
            .containsExactlyInAnyOrder(fnr1, fnr2)
    }

    @Test
    fun `hentJobbsøker skal inkludere hendelseData i responsen`() {
        val repository = JobbsøkerRepository(db.dataSource, mapper)
        val service = JobbsøkerService(db.dataSource, repository, JobbsøkerSokRepository(db.dataSource))
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A123456"))
        val fødselsnummer = Fødselsnummer("12345678901")

        // Legg til jobbsøker
        val jobbsøker = Jobbsøker(
            PersonTreffId(UUID.randomUUID()),
            treffId,
            fødselsnummer,
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            null, null, null,
            JobbsøkerStatus.LAGT_TIL
        )
        db.leggTilJobbsøkere(listOf(jobbsøker))

        // Registrer hendelse med data via service
        val hendelseDataJson = """{"fnr": "12345678901", "svar": "JA"}"""
        service.registrerMinsideVarselSvar(fødselsnummer, treffId, "SYSTEM", hendelseDataJson)

        // Hent hendelser via hendelser-API
        val response = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/hendelser",
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(HTTP_OK)
        val hendelser = mapper.readValue(
            response.body(),
            mapper.typeFactory.constructCollectionType(List::class.java, JobbsøkerHendelseMedJobbsøkerDataOutboundDto::class.java)
        ) as List<JobbsøkerHendelseMedJobbsøkerDataOutboundDto>

        val hendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE.name }
        assertThat(hendelse).isNotNull
        assertThat(hendelse!!.hendelseData).isNotNull
        val data = mapper.convertValue(hendelse.hendelseData, MinsideVarselSvarDataDto::class.java)
        assertThat(data.fnr).isEqualTo("12345678901")
        assertThat(data.svar).isEqualTo("JA")
    }

    @Test
    fun `hentJobbsøker skal inkludere RekrutteringstreffendringerDto som hendelseData i responsen`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A123456"))
        val fødselsnummer = Fødselsnummer("12345678901")

        val jobbsøker = Jobbsøker(
            PersonTreffId(UUID.randomUUID()),
            treffId,
            fødselsnummer,
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            null, null, null,
            JobbsøkerStatus.LAGT_TIL
        )
        db.leggTilJobbsøkere(listOf(jobbsøker))

        val endringer = Rekrutteringstreffendringer(endredeFelter = setOf(Endringsfelttype.NAVN, Endringsfelttype.STED))
        db.registrerTreffEndretHendelse(treffId, fødselsnummer, endringer)

        val response = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/hendelser",
            token.serialize()
        )

        assertThat(response.statusCode()).isEqualTo(HTTP_OK)
        val hendelser = mapper.readValue(
            response.body(),
            mapper.typeFactory.constructCollectionType(List::class.java, JobbsøkerHendelseMedJobbsøkerDataOutboundDto::class.java)
        ) as List<JobbsøkerHendelseMedJobbsøkerDataOutboundDto>

        val hendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON.name }
        assertThat(hendelse).isNotNull
        assertThat(hendelse!!.hendelseData).isNotNull
        val data = mapper.convertValue(hendelse.hendelseData, RekrutteringstreffendringerDto::class.java)
        assertThat(data.endredeFelter).isNotNull
    }

    @Test
    fun `legg til samme jobbsøker to ganger gir idempotent respons - kun én jobbsøker opprettes`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val fnr = Fødselsnummer("55555555555")
        val fornavn = Fornavn("Test")
        val etternavn = Etternavn("Person")
        val navkontor = Navkontor("Oslo")
        val veilederNavn = VeilederNavn("Test Veileder")
        val veilederNavIdent = VeilederNavIdent("NAV001")
        val treffId = db.opprettRekrutteringstreffIDatabase()

        val requestBody = """
        [{
          "fødselsnummer" : "${fnr.asString}",
          "fornavn" : "${fornavn.asString}",
          "etternavn" : "${etternavn.asString}",
          "navkontor" : "${navkontor.asString}",
          "veilederNavn" : "${veilederNavn.asString}",
          "veilederNavIdent" : "${veilederNavIdent.asString}"
        }]
        """.trimIndent()

        // Første kall
        val response1 = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker",
            requestBody,
            token.serialize()
        )

        assertThat(response1.statusCode()).isEqualTo(HTTP_CREATED)

        // Andre kall med samme jobbsøker
        val response2 = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker",
            requestBody,
            token.serialize()
        )

        // Skal også returnere success (enten 200 eller 201)
        assertThat(response2.statusCode()).isIn(HTTP_OK, HTTP_CREATED)

        // Verifiser at kun én jobbsøker ble opprettet
        val jobbsøkere = db.hentAlleJobbsøkere()
        assertThat(jobbsøkere).hasSize(1)
        assertThat(jobbsøkere.first().fødselsnummer).isEqualTo(fnr)

        // Verifiser at det kun er én OPPRETTET-hendelse
        val hendelser = db.hentJobbsøkerHendelser(treffId)
        val opprettetHendelser = hendelser.filter { it.hendelsestype == JobbsøkerHendelsestype.OPPRETTET }
        assertThat(opprettetHendelser).hasSize(1)
    }

    @Test
    fun `leggTilJobbsøker med telefonnummer lagres og returneres i søkerespons`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A123456"))

        val requestBody = """
        [{
          "fødselsnummer": "55555555555",
          "fornavn": "Ola",
          "etternavn": "Nordmann",
          "telefonnummer": "99887766"
        }]
        """.trimIndent()

        val postResponse = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker",
            requestBody,
            token.serialize()
        )
        assertThat(postResponse.statusCode()).isEqualTo(HTTP_CREATED)

        val getResponse = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker",
            token.serialize()
        )
        assertThat(getResponse.statusCode()).isEqualTo(HTTP_OK)
        val result = mapper.readValue(getResponse.body(), JobbsøkerSøkRespons::class.java)
        assertThat(result.jobbsøkere).hasSize(1)
        assertThat(result.jobbsøkere.first().telefonnummer).isEqualTo("99887766")
    }

    @Test
    fun `leggTilJobbsøker med søkefelter lagres og returneres i søkerespons`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A123456"))

        val requestBody = """
        [{
          "fødselsnummer": "44444444444",
          "fornavn": "Kari",
          "etternavn": "Nordmann",
          "navkontor": "Nav Grünerløkka",
          "veilederNavn": "Vera Veileder",
          "veilederNavIdent": "Z123456",
          "innsatsgruppe": "STANDARD_INNSATS",
          "fylke": "Oslo",
          "kommune": "Oslo",
          "poststed": "Oslo",
          "telefonnummer": "99887766"
        }]
        """.trimIndent()

        val postResponse = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker",
            requestBody,
            token.serialize()
        )
        assertThat(postResponse.statusCode()).isEqualTo(HTTP_CREATED)

        val getResponse = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker",
            token.serialize()
        )
        assertThat(getResponse.statusCode()).isEqualTo(HTTP_OK)

        val result = mapper.readValue(getResponse.body(), JobbsøkerSøkRespons::class.java)
        assertThat(result.jobbsøkere).hasSize(1)

        val jobbsøker = result.jobbsøkere.first()
        assertThat(jobbsøker.fornavn).isEqualTo("Kari")
        assertThat(jobbsøker.etternavn).isEqualTo("Nordmann")
        assertThat(jobbsøker.navkontor).isEqualTo("Nav Grünerløkka")
        assertThat(jobbsøker.veilederNavn).isEqualTo("Vera Veileder")
        assertThat(jobbsøker.veilederNavident).isEqualTo("Z123456")
        assertThat(jobbsøker.innsatsgruppe).isEqualTo("STANDARD_INNSATS")
        assertThat(jobbsøker.fylke).isEqualTo("Oslo")
        assertThat(jobbsøker.kommune).isEqualTo("Oslo")
        assertThat(jobbsøker.poststed).isEqualTo("Oslo")
        assertThat(jobbsøker.telefonnummer).isEqualTo("99887766")
    }

    @Test
    fun `fritekstsøk på veilederIdent returnerer riktig jobbsøker`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A123456"))

        val requestBody = """
        [
          {
            "fødselsnummer": "11111111111",
            "fornavn": "Ola",
            "etternavn": "Nordmann",
            "veilederNavn": "Kari Veileder",
            "veilederNavIdent": "X999888"
          },
          {
            "fødselsnummer": "22222222222",
            "fornavn": "Per",
            "etternavn": "Hansen",
            "veilederNavn": "Nils Veileder",
            "veilederNavIdent": "Y111222"
          }
        ]
        """.trimIndent()

        httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker",
            requestBody,
            token.serialize()
        )

        val response = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker?fritekst=X999888",
            token.serialize()
        )
        assertThat(response.statusCode()).isEqualTo(HTTP_OK)
        val result = mapper.readValue(response.body(), JobbsøkerSøkRespons::class.java)
        assertThat(result.jobbsøkere).hasSize(1)
        assertThat(result.jobbsøkere.first().fornavn).isEqualTo("Ola")
        assertThat(result.jobbsøkere.first().veilederNavident).isEqualTo("X999888")
    }
}
