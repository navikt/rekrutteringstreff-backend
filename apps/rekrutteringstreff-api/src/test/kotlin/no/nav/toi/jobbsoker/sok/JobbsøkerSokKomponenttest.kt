package no.nav.toi.jobbsoker.sok

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerSokKomponenttest {

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = ubruktPortnrFra10000.ubruktPortnr()
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()
        private val mapper = JacksonConfig.mapper

        private lateinit var app: App
    }

    private val eierRepository = EierRepository(db.dataSource)

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
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
            jobbsøkerrettet = AzureAdRoller.jobbsøkerrettet,
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
                        .withBody("""{"aktivEnhet": "1234"}""")
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

    private fun opprettTreffMedEier(navIdent: String = "A123456"): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")
        eierRepository.leggTil(treffId, listOf(navIdent))
        return treffId
    }

    private fun httpGet(path: String, navIdent: String = "A123456"): HttpResponse<String> {
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$appPort$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun httpPost(path: String, body: String, navIdent: String = "A123456"): HttpResponse<String> {
        val token = authServer.lagToken(authPort, navIdent = navIdent)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$appPort$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun søkePath(treffId: TreffId, query: String = ""): String {
        val parametre = linkedMapOf(
            "side" to "1",
            "antallPerSide" to "20",
        )

        query.takeIf { it.isNotBlank() }
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.forEach { del ->
                val (navn, verdi) = del.split("=", limit = 2)
                parametre[navn] = verdi
            }

        return "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker?${parametre.entries.joinToString("&") { "${it.key}=${it.value}" }}"
    }

    @Test
    fun `hent alle uten filtre returnerer paginert respons`() {
        val treffId = opprettTreffMedEier()
        val js1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), Navkontor("NAV Oslo"), VeilederNavn("Veil1"), VeilederNavIdent("NAV001"))
        val js2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), Navkontor("NAV Bergen"), VeilederNavn("Veil2"), VeilederNavIdent("NAV002"))
        db.leggTilJobbsøkereMedHendelse(listOf(js1, js2), treffId)

        val response = httpGet(søkePath(treffId))
        assertThat(response.statusCode()).isEqualTo(200)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        assertThat(dto.totalt).isEqualTo(2)
        assertThat(dto.side).isEqualTo(1)
        assertThat(dto.antallPerSide).isEqualTo(20)
        assertThat(dto.jobbsøkere).hasSize(2)
    }

    @Test
    fun `fritekst-søk filtrerer på navn`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Per"), Etternavn("Olsen"), null, null, null),
        ), treffId)

        val response = httpGet(søkePath(treffId, "fritekst=ola"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Ola")
    }

    @Test
    fun `fritekst-søk filtrerer på kommune og fylke`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null, fylke = "Vestland", kommune = "Bergen"),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null, fylke = "Oslo", kommune = "Oslo"),
        ), treffId)

        val response = httpGet(søkePath(treffId, "fritekst=bergen"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Ola")
    }

    @Test
    fun `statusfilter returnerer kun jobbsøkere med gitt status`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null),
        ), treffId)
        db.inviterJobbsøkere(listOf(personTreffIder[0]), treffId)

        val response = httpGet(søkePath(treffId, "status=INVITERT"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().status).isEqualTo(JobbsøkerStatus.INVITERT)
    }

    @Test
    fun `innsatsgruppefilter returnerer kun matchende jobbsøkere`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null, innsatsgruppe = "STANDARD_INNSATS"),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null, innsatsgruppe = "SITUASJONSBESTEMT_INNSATS"),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Per"), Etternavn("Olsen"), null, null, null, innsatsgruppe = "STANDARD_INNSATS"),
        ), treffId)

        val response = httpGet(søkePath(treffId, "innsatsgruppe=STANDARD_INNSATS"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(2)
        assertThat(dto.jobbsøkere).allMatch { it.innsatsgruppe == "STANDARD_INNSATS" }
    }

    @Test
    fun `fylkefilter returnerer kun jobbsøkere fra gitt fylke`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null, fylke = "Vestland"),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null, fylke = "Oslo"),
        ), treffId)

        val response = httpGet(søkePath(treffId, "fylke=Vestland"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fylke).isEqualTo("Vestland")
    }

    @Test
    fun `kommunefilter returnerer kun jobbsøkere fra gitt kommune`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null, kommune = "Bergen"),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null, kommune = "Oslo"),
        ), treffId)

        val response = httpGet(søkePath(treffId, "kommune=Oslo"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().kommune).isEqualTo("Oslo")
    }

    @Test
    fun `navkontorfilter returnerer kun jobbsøkere fra gitt kontor`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), Navkontor("NAV Oslo"), null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), Navkontor("NAV Bergen"), null, null),
        ), treffId)

        val response = httpGet(søkePath(treffId, "navkontor=NAV%20Oslo"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().navkontor).isEqualTo("NAV Oslo")
    }

    @Test
    fun `veilederfilter returnerer kun jobbsøkere med gitt veileder`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, VeilederNavn("Veil1"), VeilederNavIdent("NAV001")),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, VeilederNavn("Veil2"), VeilederNavIdent("NAV002")),
        ), treffId)

        val response = httpGet(søkePath(treffId, "veileder=NAV001"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().veilederNavident).isEqualTo("NAV001")
    }

    @Test
    fun `kombinerte filtre fungerer sammen`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), Navkontor("NAV Oslo"), null, null, innsatsgruppe = "STANDARD_INNSATS"),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), Navkontor("NAV Bergen"), null, null, innsatsgruppe = "STANDARD_INNSATS"),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Per"), Etternavn("Olsen"), Navkontor("NAV Oslo"), null, null, innsatsgruppe = "SITUASJONSBESTEMT_INNSATS"),
        ), treffId)
        db.inviterJobbsøkere(listOf(personTreffIder[0]), treffId)

        val response = httpGet(søkePath(treffId, "status=INVITERT&innsatsgruppe=STANDARD_INNSATS"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Ola")
    }

    @Test
    fun `paginering fungerer korrekt`() {
        val treffId = opprettTreffMedEier()
        val jobbsøkere = (1..5).map { i ->
            LeggTilJobbsøker(Fødselsnummer("${i}1111111111".take(11)), Fornavn("Person$i"), Etternavn("Etternavn$i"), null, null, null)
        }
        db.leggTilJobbsøkereMedHendelse(jobbsøkere, treffId)

        val side1 = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet("/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker?side=1&antallPerSide=2").body()
        )
        assertThat(side1.totalt).isEqualTo(5)
        assertThat(side1.side).isEqualTo(1)
        assertThat(side1.antallPerSide).isEqualTo(2)
        assertThat(side1.jobbsøkere).hasSize(2)

        val side2 = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet("/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker?side=2&antallPerSide=2").body()
        )
        assertThat(side2.jobbsøkere).hasSize(2)

        val side3 = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet("/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker?side=3&antallPerSide=2").body()
        )
        assertThat(side3.jobbsøkere).hasSize(1)
    }

    @Test
    fun `sortering på navn gir alfabetisk rekkefølge`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Charlie"), Etternavn("C"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Alice"), Etternavn("A"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Bob"), Etternavn("B"), null, null, null),
        ), treffId)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet(søkePath(treffId, "sortering=navn")).body()
        )

        assertThat(dto.jobbsøkere.map { it.fornavn }).containsExactly("Alice", "Bob", "Charlie")
    }

    @Test
    fun `sortering på status fungerer`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("A"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("B"), null, null, null),
        ), treffId)
        db.inviterJobbsøkere(listOf(personTreffIder[1]), treffId)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet(søkePath(treffId, "sortering=status")).body()
        )

        assertThat(dto.jobbsøkere).hasSize(2)
        assertThat(dto.jobbsøkere.first().status).isEqualTo(JobbsøkerStatus.INVITERT)
        assertThat(dto.jobbsøkere.last().status).isEqualTo(JobbsøkerStatus.LAGT_TIL)
    }

    @Test
    fun `tomt resultatsett returnerer tom liste og totalt 0`() {
        val treffId = opprettTreffMedEier()

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet(søkePath(treffId, "fritekst=finnesikke")).body()
        )

        assertThat(dto.totalt).isEqualTo(0)
        assertThat(dto.jobbsøkere).isEmpty()
    }

    @Test
    fun `fnr-søk returnerer treff for eksisterende jobbsøker`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), Navkontor("NAV Oslo"), null, null),
        ), treffId)

        val response = httpPost(
            "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/sok",
            """{"fodselsnummer": "11111111111"}"""
        )

        assertThat(response.statusCode()).isEqualTo(200)
        val treff = mapper.readValue<JobbsøkerSøkTreff>(response.body())
        assertThat(treff.fornavn).isEqualTo("Ola")
        assertThat(treff.navkontor).isEqualTo("NAV Oslo")
    }

    @Test
    fun `fnr-søk returnerer 404 for ukjent fødselsnummer`() {
        val treffId = opprettTreffMedEier()

        val response = httpPost(
            "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/sok",
            """{"fodselsnummer": "99999999999"}"""
        )

        assertThat(response.statusCode()).isEqualTo(404)
    }

    @Test
    fun `ikke-eier får 403 på søk`() {
        val treffId = opprettTreffMedEier("A123456")

        val response = httpGet(søkePath(treffId), navIdent = "B999999")

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `ikke-eier får 403 på fnr-søk`() {
        val treffId = opprettTreffMedEier("A123456")

        val response = httpPost(
            "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/sok",
            """{"fodselsnummer": "11111111111"}""",
            navIdent = "B999999"
        )

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `slettede jobbsøkere ekskluderes fra søkeresultat`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Aktiv"), Etternavn("Person"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Slettet"), Etternavn("Person"), null, null, null),
        ), treffId)
        db.settJobbsøkerStatus(personTreffIder[1], JobbsøkerStatus.SLETTET)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet(søkePath(treffId)).body()
        )

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Aktiv")
    }

    @Test
    fun `ikke-synlige jobbsøkere ekskluderes fra søkeresultat`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Person"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Skjult"), Etternavn("Person"), null, null, null),
        ), treffId)
        db.settSynlighet(personTreffIder[1], false)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet(søkePath(treffId)).body()
        )

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Synlig")
    }

    @Test
    fun `ugyldig antallPerSide gir 400`() {
        val treffId = opprettTreffMedEier()

        val response = httpGet(søkePath(treffId, "antallPerSide=200"))
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `manglende side gir 400`() {
        val treffId = opprettTreffMedEier()

        val response = httpGet("/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker?antallPerSide=20")

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `filterverdier returnerer distinkte navkontor innsatsgrupper og steder`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), Navkontor("Nav Grünerløkka"), VeilederNavn("Veil 1"), VeilederNavIdent("NAV001"), innsatsgruppe = "STANDARD_INNSATS", fylke = "Oslo", kommune = "Oslo"),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), Navkontor("Nav Lerkendal"), VeilederNavn("Veil 2"), VeilederNavIdent("NAV002"), innsatsgruppe = "SITUASJONSBESTEMT_INNSATS", fylke = "Trøndelag", kommune = "Trondheim"),
        ), treffId)

        val response = httpGet("/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/filterverdier")

        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue<JobbsøkerFilterverdierRespons>(response.body())
        assertThat(dto.navkontor).containsExactly("Nav Grünerløkka", "Nav Lerkendal")
        assertThat(dto.innsatsgrupper).containsExactly("SITUASJONSBESTEMT_INNSATS", "STANDARD_INNSATS")
        assertThat(dto.steder).containsExactly(
            JobbsøkerFilterverdiSted("Oslo", Stedstype.FYLKE),
            JobbsøkerFilterverdiSted("Trøndelag", Stedstype.FYLKE),
            JobbsøkerFilterverdiSted("Oslo", Stedstype.KOMMUNE),
            JobbsøkerFilterverdiSted("Trondheim", Stedstype.KOMMUNE),
        )
    }

    @Test
    fun `søk returnerer kun jobbsøkere for riktig treff`() {
        val treff1 = opprettTreffMedEier()
        val treff2 = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456", tittel = "AnnetTreff")
        eierRepository.leggTil(treff2, listOf("A123456"))

        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Treff1Person"), Etternavn("A"), null, null, null),
        ), treff1)
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Treff2Person"), Etternavn("B"), null, null, null),
        ), treff2)

        val dto1 = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet(søkePath(treff1)).body()
        )
        assertThat(dto1.totalt).isEqualTo(1)
        assertThat(dto1.jobbsøkere.first().fornavn).isEqualTo("Treff1Person")

        val dto2 = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet(søkePath(treff2)).body()
        )
        assertThat(dto2.totalt).isEqualTo(1)
        assertThat(dto2.jobbsøkere.first().fornavn).isEqualTo("Treff2Person")
    }

    @Test
    fun `søk fungerer med null-felter på eldre data`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Uten"), Etternavn("Data"), null, null, null),
        ), treffId)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpGet(søkePath(treffId)).body()
        )

        assertThat(dto.totalt).isEqualTo(1)
        val js = dto.jobbsøkere.first()
        assertThat(js.innsatsgruppe).isNull()
        assertThat(js.fylke).isNull()
        assertThat(js.kommune).isNull()
        assertThat(js.poststed).isNull()
    }
}
