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
import java.sql.Timestamp
import java.time.Instant

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

    private fun søkPath(treffId: TreffId): String =
        "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/sok"

    private fun søkBody(vararg felter: Pair<String, Any>): String {
        val map = mutableMapOf<String, Any>(
            "side" to 1,
            "antallPerSide" to 20,
        )
        felter.forEach { (key, value) -> map[key] = value }
        return mapper.writeValueAsString(map)
    }

    private fun oppdaterLagtTilDato(personTreffId: PersonTreffId, lagtTilDato: Instant) {
        db.dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE jobbsoker
                SET lagt_til_dato = ?
                WHERE id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(lagtTilDato))
                stmt.setObject(2, personTreffId.somUuid)
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `søk uten filtre returnerer paginert respons`() {
        val treffId = opprettTreffMedEier()
        val js1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), Navkontor("NAV Oslo"), VeilederNavn("Veil1"), VeilederNavIdent("NAV001"))
        val js2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), Navkontor("NAV Bergen"), VeilederNavn("Veil2"), VeilederNavIdent("NAV002"))
        db.leggTilJobbsøkereMedHendelse(listOf(js1, js2), treffId)

        val response = httpPost(søkPath(treffId), søkBody())
        assertThat(response.statusCode()).isEqualTo(200)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        assertThat(dto.totalt).isEqualTo(2)
        assertThat(dto.side).isEqualTo(1)
        assertThat(dto.antallPerSide).isEqualTo(20)
        assertThat(dto.jobbsøkere).hasSize(2)
        assertThat(dto.jobbsøkere.map { it.fodselsnummer }).containsExactlyInAnyOrder("11111111111", "22222222222")
    }

    @Test
    fun `fritekst-søk filtrerer på navn`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Per"), Etternavn("Olsen"), null, null, null),
        ), treffId)

        val response = httpPost(søkPath(treffId), søkBody("fritekst" to "ola"))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Ola")
    }

    @Test
    fun `fritekst-søk filtrerer kun på navn`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(
                Fødselsnummer("11111111111"),
                Fornavn("Ola"),
                Etternavn("Nordmann"),
                Navkontor("KontorAlpha"),
                VeilederNavn("Vera Veileder"),
                VeilederNavIdent("NAV001"),
            ),
            LeggTilJobbsøker(
                Fødselsnummer("22222222222"),
                Fornavn("Kari"),
                Etternavn("Hansen"),
                Navkontor("KontorBeta"),
                VeilederNavn("Per Person"),
                VeilederNavIdent("NAV002"),
            ),
        ), treffId)

        val kontorTreff = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("fritekst" to "alpha")).body()
        )
        val veilederTreff = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("fritekst" to "nav002")).body()
        )

        assertThat(kontorTreff.jobbsøkere).isEmpty()
        assertThat(veilederTreff.jobbsøkere).isEmpty()
    }

    @Test
    fun `statusfilter returnerer kun jobbsøkere med gitt status`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null),
        ), treffId)
        db.inviterJobbsøkere(listOf(personTreffIder[0]), treffId)

        val response = httpPost(søkPath(treffId), søkBody("status" to listOf("INVITERT")))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().status).isEqualTo(JobbsøkerStatus.INVITERT)
    }

    @Test
    fun `kombinerte filtre fungerer sammen`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), Navkontor("Nav Oslo"), null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), Navkontor("Nav Bergen"), null, null),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Per"), Etternavn("Olsen"), Navkontor("Nav Oslo"), null, null),
        ), treffId)
        db.inviterJobbsøkere(listOf(personTreffIder[0]), treffId)

        val response = httpPost(søkPath(treffId), søkBody("status" to listOf("INVITERT")))
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
            httpPost(søkPath(treffId), søkBody("side" to 1, "antallPerSide" to 2)).body()
        )
        assertThat(side1.totalt).isEqualTo(5)
        assertThat(side1.side).isEqualTo(1)
        assertThat(side1.antallPerSide).isEqualTo(2)
        assertThat(side1.jobbsøkere).hasSize(2)

        val side2 = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("side" to 2, "antallPerSide" to 2)).body()
        )
        assertThat(side2.jobbsøkere).hasSize(2)

        val side3 = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("side" to 3, "antallPerSide" to 2)).body()
        )
        assertThat(side3.jobbsøkere).hasSize(1)
    }

    @Test
    fun `ugyldig høy side clampes til siste gyldige side`() {
        val treffId = opprettTreffMedEier()
        val jobbsøkere = (1..5).map { i ->
            LeggTilJobbsøker(Fødselsnummer("${i}2222222222".take(11)), Fornavn("Person$i"), Etternavn("Etternavn$i"), null, null, null)
        }
        db.leggTilJobbsøkereMedHendelse(jobbsøkere, treffId)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("side" to 4, "antallPerSide" to 2)).body()
        )

        assertThat(dto.totalt).isEqualTo(5)
        assertThat(dto.side).isEqualTo(3)
        assertThat(dto.jobbsøkere).hasSize(1)
        assertThat(dto.jobbsøkere.single().fornavn).isEqualTo("Person5")
    }

    @Test
    fun `ugyldig høy side clampes etter at skjulte og slettede er filtrert bort`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            (1..5).map { i ->
                LeggTilJobbsøker(
                    Fødselsnummer("${i}3333333333".take(11)),
                    Fornavn("Person$i"),
                    Etternavn("Etternavn$i"),
                    null,
                    null,
                    null,
                )
            },
            treffId,
        )
        db.settSynlighet(personTreffIder[3], false)
        db.settJobbsøkerStatus(personTreffIder[4], JobbsøkerStatus.SLETTET)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("side" to 3, "antallPerSide" to 2)).body()
        )

        assertThat(dto.totalt).isEqualTo(3)
        assertThat(dto.antallSkjulte).isEqualTo(1)
        assertThat(dto.antallSlettede).isEqualTo(1)
        assertThat(dto.side).isEqualTo(2)
        assertThat(dto.jobbsøkere).hasSize(1)
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
            httpPost(søkPath(treffId), søkBody("sortering" to "navn")).body()
        )

        assertThat(dto.jobbsøkere.map { it.fornavn }).containsExactly("Alice", "Bob", "Charlie")
    }

    @Test
    fun `sortering på navn støtter synkende rekkefølge`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Charlie"), Etternavn("C"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Alice"), Etternavn("A"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Bob"), Etternavn("B"), null, null, null),
        ), treffId)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("sortering" to "navn", "retning" to "desc")).body()
        )

        assertThat(dto.jobbsøkere.map { it.fornavn }).containsExactly("Charlie", "Bob", "Alice")
    }

    @Test
    fun `sortering på lagt til støtter stigende og synkende rekkefølge`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Charlie"), Etternavn("C"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Alice"), Etternavn("A"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Bob"), Etternavn("B"), null, null, null),
        ), treffId)

        oppdaterLagtTilDato(personTreffIder[0], Instant.parse("2026-03-01T12:00:00Z"))
        oppdaterLagtTilDato(personTreffIder[1], Instant.parse("2026-01-01T12:00:00Z"))
        oppdaterLagtTilDato(personTreffIder[2], Instant.parse("2026-02-01T12:00:00Z"))

        val stigendeDto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("sortering" to "lagt-til", "retning" to "asc")).body()
        )
        val synkendeDto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("sortering" to "lagt-til", "retning" to "desc")).body()
        )

        assertThat(stigendeDto.jobbsøkere.map { it.fornavn }).containsExactly("Alice", "Bob", "Charlie")
        assertThat(synkendeDto.jobbsøkere.map { it.fornavn }).containsExactly("Charlie", "Bob", "Alice")
    }

    @Test
    fun `sortering på lagt til bruker klokkeslett innenfor samme dato`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("44444444444"), Fornavn("Charlie"), Etternavn("C"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("55555555555"), Fornavn("Alice"), Etternavn("A"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("66666666666"), Fornavn("Bob"), Etternavn("B"), null, null, null),
        ), treffId)

        oppdaterLagtTilDato(personTreffIder[0], Instant.parse("2026-03-01T12:00:03Z"))
        oppdaterLagtTilDato(personTreffIder[1], Instant.parse("2026-03-01T12:00:01Z"))
        oppdaterLagtTilDato(personTreffIder[2], Instant.parse("2026-03-01T12:00:02Z"))

        val stigendeDto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("sortering" to "lagt-til", "retning" to "asc")).body()
        )
        val synkendeDto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("sortering" to "lagt-til", "retning" to "desc")).body()
        )

        assertThat(stigendeDto.jobbsøkere.map { it.fornavn }).containsExactly("Alice", "Bob", "Charlie")
        assertThat(synkendeDto.jobbsøkere.map { it.fornavn }).containsExactly("Charlie", "Bob", "Alice")
    }

    @Test
    fun `tomt resultatsett returnerer tom liste og totalt 0`() {
        val treffId = opprettTreffMedEier()

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("fritekst" to "finnesikke")).body()
        )

        assertThat(dto.totalt).isEqualTo(0)
        assertThat(dto.jobbsøkere).isEmpty()
    }

    @Test
    fun `fritekst-søk med fødselsnummer returnerer treff`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), Navkontor("NAV Oslo"), null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null),
        ), treffId)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("fritekst" to "11111111111")).body()
        )

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fodselsnummer).isEqualTo("11111111111")
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Ola")
        assertThat(dto.jobbsøkere.first().navkontor).isEqualTo("NAV Oslo")
    }

    @Test
    fun `fritekst-søk med ukjent fødselsnummer returnerer tomt resultat`() {
        val treffId = opprettTreffMedEier()

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("fritekst" to "99999999999")).body()
        )

        assertThat(dto.totalt).isEqualTo(0)
        assertThat(dto.jobbsøkere).isEmpty()
    }

    @Test
    fun `ikke-eier får 403 på søk`() {
        val treffId = opprettTreffMedEier("A123456")

        val response = httpPost(søkPath(treffId), søkBody(), navIdent = "B999999")

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
            httpPost(søkPath(treffId), søkBody()).body()
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
            httpPost(søkPath(treffId), søkBody()).body()
        )

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Synlig")
    }

    @Test
    fun `ugyldig antallPerSide gir 400`() {
        val treffId = opprettTreffMedEier()

        val response = httpPost(søkPath(treffId), søkBody("antallPerSide" to 200))
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `manglende side og antallPerSide bruker standard paginering`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
        ), treffId)

        val response = httpPost(søkPath(treffId), søkBody())

        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        assertThat(dto.side).isEqualTo(1)
        assertThat(dto.antallPerSide).isEqualTo(20)
        assertThat(dto.totalt).isEqualTo(1)
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
            httpPost(søkPath(treff1), søkBody()).body()
        )
        assertThat(dto1.totalt).isEqualTo(1)
        assertThat(dto1.jobbsøkere.first().fornavn).isEqualTo("Treff1Person")

        val dto2 = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treff2), søkBody()).body()
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
            httpPost(søkPath(treffId), søkBody()).body()
        )

        assertThat(dto.totalt).isEqualTo(1)
    }

    @Test
    fun `søk inkluderer minsideHendelser for jobbsøkere med MOTTATT_SVAR_FRA_MINSIDE`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Med"), Etternavn("Hendelse"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Uten"), Etternavn("Hendelse"), null, null, null),
        ), treffId)

        db.leggTilMinsideHendelse(personTreffIder[0], """{"varselId":"v1","eksternKanal":"SMS","eksternStatus":"SENDT","minsideStatus":"OPPRETTET"}""")

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("sortering" to "navn")).body()
        )

        assertThat(dto.jobbsøkere).hasSize(2)
        val medHendelse = dto.jobbsøkere.first { it.fornavn == "Med" }
        val utenHendelse = dto.jobbsøkere.first { it.fornavn == "Uten" }

        assertThat(medHendelse.minsideHendelser).hasSize(1)
        assertThat(medHendelse.minsideHendelser.first().hendelsestype).isEqualTo("MOTTATT_SVAR_FRA_MINSIDE")

        assertThat(utenHendelse.minsideHendelser).isEmpty()
    }

    @Test
    fun `søk inkluderer flere minsideHendelser per jobbsøker`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
        ), treffId)

        db.leggTilMinsideHendelse(personTreffIder[0], """{"varselId":"v1","eksternKanal":"SMS","eksternStatus":"SENDT"}""")
        db.leggTilMinsideHendelse(personTreffIder[0], """{"varselId":"v2","eksternKanal":null,"eksternStatus":"SENDT","minsideStatus":"OPPRETTET"}""")

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody()).body()
        )

        assertThat(dto.jobbsøkere).hasSize(1)
        assertThat(dto.jobbsøkere.first().minsideHendelser).hasSize(2)
    }

    @Test
    fun `minsideHendelser inkluderer hendelseData som json`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
        ), treffId)

        val hendelseJson = """{"varselId":"v1","eksternKanal":"SMS","eksternStatus":"SENDT","minsideStatus":"OPPRETTET","eksternFeilmelding":null}"""
        db.leggTilMinsideHendelse(personTreffIder[0], hendelseJson)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody()).body()
        )

        val hendelse = dto.jobbsøkere.first().minsideHendelser.first()
        val hendelseDataNode = mapper.readTree(mapper.writeValueAsString(hendelse)).get("hendelseData")
        assertThat(hendelseDataNode.get("varselId").asText()).isEqualTo("v1")
        assertThat(hendelseDataNode.get("eksternKanal").asText()).isEqualTo("SMS")
        assertThat(hendelseDataNode.get("eksternStatus").asText()).isEqualTo("SENDT")
    }

    @Test
    fun `minsideHendelser returneres ikke for andre hendelsetyper`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
        ), treffId)
        db.inviterJobbsøkere(listOf(personTreffIder[0]), treffId)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody()).body()
        )

        assertThat(dto.jobbsøkere.first().minsideHendelser).isEmpty()
    }

    @Test
    fun `minsideHendelser isoleres per treff ved paginering`() {
        val treffId = opprettTreffMedEier()
        val jobbsøkere = (1..3).map { i ->
            LeggTilJobbsøker(Fødselsnummer("${i}1111111111".take(11)), Fornavn("Person$i"), Etternavn("Etternavn$i"), null, null, null)
        }
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(jobbsøkere, treffId)

        db.leggTilMinsideHendelse(personTreffIder[0], """{"varselId":"v1","eksternKanal":"SMS","eksternStatus":"SENDT"}""")
        db.leggTilMinsideHendelse(personTreffIder[2], """{"varselId":"v2","eksternKanal":null,"eksternStatus":"SENDT","minsideStatus":"OPPRETTET"}""")

        val side1 = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("side" to 1, "antallPerSide" to 2, "sortering" to "navn")).body()
        )
        assertThat(side1.jobbsøkere).hasSize(2)

        val side2 = mapper.readValue<JobbsøkerSøkRespons>(
            httpPost(søkPath(treffId), søkBody("side" to 2, "antallPerSide" to 2, "sortering" to "navn")).body()
        )
        assertThat(side2.jobbsøkere).hasSize(1)

        val alleMedHendelser = (side1.jobbsøkere + side2.jobbsøkere).filter { it.minsideHendelser.isNotEmpty() }
        assertThat(alleMedHendelser).hasSize(2)
    }
}
