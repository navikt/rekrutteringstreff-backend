package no.nav.toi.jobbsoker.synlighet

import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelseMedJobbsøkerDataOutboundDto
import no.nav.toi.jobbsoker.sok.JobbsøkerSøkRespons
import no.nav.toi.rekrutteringstreff.HendelseRessurs
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.FellesHendelseOutboundDto
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * Komponenttest som verifiserer at synlighetsfiltrering fungerer korrekt
 * når data eksponeres via REST API.
 * 
 * Synlighet styres av kode 6/7, KVP, død, osv. og oppdateres via toi-synlighetsmotor.
 * Jobbsøkere som ikke er synlige skal filtreres ut før data returneres til frontend.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class SynlighetsKomponentTest {

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18015
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()
        private val mapper = JacksonConfig.mapper
        
        private lateinit var app: App
        private lateinit var jobbsøkerService: JobbsøkerService
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
        
        jobbsøkerService = JobbsøkerService(db.dataSource, JobbsøkerRepository(db.dataSource, mapper), JobbsøkerSokRepository(db.dataSource))
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

    private fun httpGet(path: String): HttpResponse<String> {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$appPort$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun jobbsøkerPath(treffId: TreffId): String =
        "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker"

    private fun opprettTreffMedEier(): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456", tittel = "TestTreff")
        eierRepository.leggTil(treffId, listOf("A123456"))
        return treffId
    }

    @Test
    fun `GET jobbsøkere filtrerer ut ikke-synlige jobbsøkere`() {
        val treffId = opprettTreffMedEier()
        
        val synligJobbsøker = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Person"), null, null, null)
        val ikkeSynligJobbsøker = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("IkkeSynlig"), Etternavn("Person"), null, null, null)
        
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(synligJobbsøker, ikkeSynligJobbsøker), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], true)
        db.settSynlighet(personTreffIder[1], false)
        
        val response = httpGet(jobbsøkerPath(treffId))
        
        assertThat(response.statusCode()).isEqualTo(200)
        val jobbsøkere = mapper.readValue<JobbsøkerSøkRespons>(response.body()).jobbsøkere
        assertThat(jobbsøkere).hasSize(1)
        assertThat(jobbsøkere.first().fornavn).isEqualTo("Synlig")
    }

    @Test
    fun `jobbsøker som blir ikke-synlig via synlighetsmotor forsvinner fra API`() {
        val treffId = opprettTreffMedEier()
        
        val fnr = Fødselsnummer("33333333333")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Test"), Etternavn("Person"), null, null, null)
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "A123456")
        
        // Jobbsøker er synlig som default (null = synlig)
        val responseFør = httpGet(jobbsøkerPath(treffId))
        assertThat(responseFør.statusCode()).isEqualTo(200)
        val jobbsøkereFør = mapper.readValue<JobbsøkerSøkRespons>(responseFør.body()).jobbsøkere
        assertThat(jobbsøkereFør).hasSize(1)
        
        // Simuler at synlighetsmotor sender event om at person er kode 6/7
        jobbsøkerService.oppdaterSynlighetFraEvent(fnr.asString, false, Instant.now())
        
        // Jobbsøker skal nå være filtrert ut fra API
        val responseEtter = httpGet(jobbsøkerPath(treffId))
        assertThat(responseEtter.statusCode()).isEqualTo(200)
        val jobbsøkereEtter = mapper.readValue<JobbsøkerSøkRespons>(responseEtter.body()).jobbsøkere
        assertThat(jobbsøkereEtter).isEmpty()
    }

    @Test
    fun `jobbsøker som blir synlig igjen via synlighetsmotor dukker opp i API`() {
        val treffId = opprettTreffMedEier()
        
        val fnr = Fødselsnummer("44444444444")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Test"), Etternavn("Person"), null, null, null)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], false)
        
        // Jobbsøker er ikke synlig
        val responseFør = httpGet(jobbsøkerPath(treffId))
        val jobbsøkereFør = mapper.readValue<JobbsøkerSøkRespons>(responseFør.body()).jobbsøkere
        assertThat(jobbsøkereFør).isEmpty()
        
        // Simuler at synlighetsmotor sender event om at person er synlig igjen (f.eks. KVP avsluttet)
        jobbsøkerService.oppdaterSynlighetFraEvent(fnr.asString, true, Instant.now())
        
        // Jobbsøker skal nå være med i API-responsen
        val responseEtter = httpGet(jobbsøkerPath(treffId))
        val jobbsøkereEtter = mapper.readValue<JobbsøkerSøkRespons>(responseEtter.body()).jobbsøkere
        assertThat(jobbsøkereEtter).hasSize(1)
        assertThat(jobbsøkereEtter.first().fornavn).isEqualTo("Test")
    }

    @Test
    fun `synlighetsoppdatering påvirker alle treff der personen er jobbsøker`() {
        val treff1 = opprettTreffMedEier()
        val treff2 = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456", tittel = "Treff2")
        eierRepository.leggTil(treff2, listOf("A123456"))
        
        val fnr = Fødselsnummer("55555555555")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Test"), Etternavn("Person"), null, null, null)
        
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treff1, "A123456")
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treff2, "A123456")
        
        // Begge treff har jobbsøkeren synlig
        assertThat(mapper.readValue<JobbsøkerSøkRespons>(httpGet(jobbsøkerPath(treff1)).body()).jobbsøkere).hasSize(1)
        assertThat(mapper.readValue<JobbsøkerSøkRespons>(httpGet(jobbsøkerPath(treff2)).body()).jobbsøkere).hasSize(1)
        
        // Synlighetsmotor markerer person som ikke-synlig
        jobbsøkerService.oppdaterSynlighetFraEvent(fnr.asString, false, Instant.now())
        
        // Begge treff skal nå filtrere ut jobbsøkeren
        assertThat(mapper.readValue<JobbsøkerSøkRespons>(httpGet(jobbsøkerPath(treff1)).body()).jobbsøkere).isEmpty()
        assertThat(mapper.readValue<JobbsøkerSøkRespons>(httpGet(jobbsøkerPath(treff2)).body()).jobbsøkere).isEmpty()
    }

    @Test
    fun `GET jobbsøkere returnerer kun synlige jobbsøkere i totalt og liste`() {
        val treffId = opprettTreffMedEier()
        
        val synlig1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig1"), Etternavn("Person"), null, null, null)
        val synlig2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Synlig2"), Etternavn("Person"), null, null, null)
        val skjult1 = LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Skjult1"), Etternavn("Person"), null, null, null)
        val skjult2 = LeggTilJobbsøker(Fødselsnummer("44444444444"), Fornavn("Skjult2"), Etternavn("Person"), null, null, null)
        val skjult3 = LeggTilJobbsøker(Fødselsnummer("55555555555"), Fornavn("Skjult3"), Etternavn("Person"), null, null, null)
        
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(synlig1, synlig2, skjult1, skjult2, skjult3), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], true)
        db.settSynlighet(personTreffIder[1], true)
        db.settSynlighet(personTreffIder[2], false)
        db.settSynlighet(personTreffIder[3], false)
        db.settSynlighet(personTreffIder[4], false)
        
        val response = httpGet(jobbsøkerPath(treffId))
        
        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        
        assertThat(dto.jobbsøkere).hasSize(2)
        assertThat(dto.totalt).isEqualTo(2)
    }

    @Test
    fun `GET jobbsøkere ekskluderer slettede jobbsøkere fra totalt`() {
        val treffId = opprettTreffMedEier()
        
        val synlig = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Person"), null, null, null)
        val skjult = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Skjult"), Etternavn("Person"), null, null, null)
        val skalSlettes = LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Slettet"), Etternavn("Person"), null, null, null)
        
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(synlig, skjult, skalSlettes), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], true)
        db.settSynlighet(personTreffIder[1], false)
        db.settSynlighet(personTreffIder[2], true)
        
        // Slett jobbsøkeren via service
        jobbsøkerService.markerSlettet(personTreffIder[2], treffId, "A123456")
        
        val response = httpGet(jobbsøkerPath(treffId))
        
        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        
        assertThat(dto.jobbsøkere).hasSize(1)
        assertThat(dto.totalt).isEqualTo(1)
    }

    @Test
    fun `GET jobbsøkere - slettet jobbsøker forblir usynlig uavhengig av senere synlighetsendring`() {
        val treffId = opprettTreffMedEier()
        
        // Opprett to synlige jobbsøkere som skal slettes
        val fnr1 = Fødselsnummer("11111111111")
        val fnr2 = Fødselsnummer("22222222222")
        val slettet1 = LeggTilJobbsøker(fnr1, Fornavn("Slettet1"), Etternavn("Person"), null, null, null)
        val slettet2 = LeggTilJobbsøker(fnr2, Fornavn("Slettet2"), Etternavn("Person"), null, null, null)
        
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(slettet1, slettet2), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], true)
        db.settSynlighet(personTreffIder[1], true)
        
        // Slett begge jobbsøkerne mens de er synlige
        jobbsøkerService.markerSlettet(personTreffIder[0], treffId, "A123456")
        jobbsøkerService.markerSlettet(personTreffIder[1], treffId, "A123456")
        
        // Endre synlighet til false for én av dem (simulerer synlighetsmotor-event)
        jobbsøkerService.oppdaterSynlighetFraEvent(fnr1.asString, false, Instant.now())
        
        val response = httpGet(jobbsøkerPath(treffId))
        
        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        
        assertThat(dto.jobbsøkere).isEmpty()
        assertThat(dto.totalt).isEqualTo(0)
    }

    @Test
    fun `skjulte jobbsøkere kan ikke slettes via API - returnerer IKKE_FUNNET`() {
        val treffId = opprettTreffMedEier()
        
        // Opprett en skjult jobbsøker
        val skjultJobbsøker = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Skjult"), Etternavn("Person"), null, null, null)
        
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(skjultJobbsøker), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], false)
        
        // Forsøk å slette - skal returnere IKKE_FUNNET fordi skjulte jobbsøkere ikke er tilgjengelige
        val resultat = jobbsøkerService.markerSlettet(personTreffIder[0], treffId, "A123456")
        
        assertThat(resultat).isEqualTo(MarkerSlettetResultat.IKKE_FUNNET)
        
        // Jobbsøkeren er skjult så den skal ikke vises
        val response = httpGet(jobbsøkerPath(treffId))
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        
        assertThat(dto.totalt).isEqualTo(0)
        assertThat(dto.jobbsøkere).isEmpty()
    }

    @Test
    fun `GET jobbsøkere - kun synlige ikke-slettede returneres med korrekt totalt`() {
        val treffId = opprettTreffMedEier()
        
        val synlig1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig1"), Etternavn("Person"), null, null, null)
        val synlig2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Synlig2"), Etternavn("Person"), null, null, null)
        val skjult = LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Skjult"), Etternavn("Person"), null, null, null)
        val slettet1 = LeggTilJobbsøker(Fødselsnummer("44444444444"), Fornavn("Slettet1"), Etternavn("Person"), null, null, null)
        val slettet2 = LeggTilJobbsøker(Fødselsnummer("55555555555"), Fornavn("Slettet2"), Etternavn("Person"), null, null, null)
        
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(synlig1, synlig2, skjult, slettet1, slettet2), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], true)
        db.settSynlighet(personTreffIder[1], true)
        db.settSynlighet(personTreffIder[2], false)
        db.settSynlighet(personTreffIder[3], true)
        db.settSynlighet(personTreffIder[4], true)
        
        jobbsøkerService.markerSlettet(personTreffIder[3], treffId, "A123456")
        jobbsøkerService.markerSlettet(personTreffIder[4], treffId, "A123456")
        
        val response = httpGet(jobbsøkerPath(treffId))
        
        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        
        assertThat(dto.jobbsøkere).hasSize(2)
        assertThat(dto.totalt).isEqualTo(2)
    }

    @Test
    fun `GET jobbsøkere returnerer tomt når treffet er tomt`() {
        val treffId = opprettTreffMedEier()
        
        val response = httpGet(jobbsøkerPath(treffId))
        
        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        
        assertThat(dto.jobbsøkere).isEmpty()
        assertThat(dto.totalt).isEqualTo(0)
    }

    @Test
    fun `GET jobbsøker-hendelser filtrerer ut hendelser for ikke-synlige jobbsøkere`() {
        val treffId = opprettTreffMedEier()
        
        val synligJobbsøker = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Person"), null, null, null)
        val ikkeSynligJobbsøker = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("IkkeSynlig"), Etternavn("Person"), null, null, null)
        
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(synligJobbsøker, ikkeSynligJobbsøker), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], true)
        db.settSynlighet(personTreffIder[1], false)
        
        val response = httpGet("/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/hendelser")
        
        assertThat(response.statusCode()).isEqualTo(200)
        val hendelser: List<JobbsøkerHendelseMedJobbsøkerDataOutboundDto> = mapper.readValue(response.body())
        assertThat(hendelser).hasSize(1)
        assertThat(hendelser.first().fornavn).isEqualTo("Synlig")
    }

    @Test
    fun `GET alle hendelser filtrerer ut jobbsøker-hendelser for ikke-synlige jobbsøkere`() {
        val treffId = opprettTreffMedEier()
        
        val synligJobbsøker = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Person"), null, null, null)
        val ikkeSynligJobbsøker = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("IkkeSynlig"), Etternavn("Person"), null, null, null)
        
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(synligJobbsøker, ikkeSynligJobbsøker), treffId, "A123456")
        db.settSynlighet(personTreffIder[0], true)
        db.settSynlighet(personTreffIder[1], false)
        
        val response = httpGet("/api/rekrutteringstreff/${treffId.somUuid}/allehendelser")
        
        assertThat(response.statusCode()).isEqualTo(200)
        val hendelser: List<FellesHendelseOutboundDto> = mapper.readValue(response.body())
        
        // Det skal være 1 rekrutteringstreff-hendelse (OPPRETTET) + 1 jobbsøker-hendelse (synlig)
        val jobbsøkerHendelser = hendelser.filter { it.ressurs == HendelseRessurs.JOBBSØKER }
        val treffHendelser = hendelser.filter { it.ressurs == HendelseRessurs.REKRUTTERINGSTREFF }
        
        assertThat(jobbsøkerHendelser).hasSize(1)
        assertThat(jobbsøkerHendelser.first().subjektNavn).isEqualTo("Synlig Person")
        assertThat(treffHendelser).isNotEmpty()
    }
}

