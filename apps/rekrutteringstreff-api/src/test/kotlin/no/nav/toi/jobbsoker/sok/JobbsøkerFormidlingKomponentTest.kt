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
import java.util.UUID
import no.nav.toi.testApplicationContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerFormidlingKomponentTest {

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
            ctx = testApplicationContext(
                    dataSource = db.dataSource,
                    authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer = "http://localhost:$authPort/default",
                    jwksUri = "http://localhost:$authPort/default/jwks",
                    audience = "rekrutteringstreff-audience"
                )
            ),
                    modiaKlient = ModiaKlient(
                modiaContextHolderUrl = wmInfo.httpBaseUrl,
                modiaContextHolderScope = "",
                accessTokenClient = accessTokenClient,
                httpClient = httpClient
            ),
                    pilotkontorer = listOf("1234"),
            ),
            port = appPort,
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

    private fun opprettTreffMedEier(eierIdent: String): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = eierIdent, tittel = "TestTreff")
        eierRepository.leggTil(treffId, listOf(eierIdent))
        return treffId
    }

    private fun formidlingEgnePath(treffId: TreffId): String =
        "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/formidling/egne"

    private fun formidlingAllePath(treffId: TreffId): String =
        "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/formidling/alle"

    private fun formidlingBody(vararg felter: Pair<String, Any>): String {
        val map = mutableMapOf<String, Any>("side" to 1, "antallPerSide" to 25)
        felter.forEach { (k, v) -> map[k] = v }
        return mapper.writeValueAsString(map)
    }

    private fun httpPost(
        path: String,
        body: String,
        navIdent: String,
        groups: List<UUID> = listOf(AzureAdRoller.arbeidsgiverrettet),
    ): HttpResponse<String> {
        val token = authServer.lagToken(authPort, navIdent = navIdent, groups = groups)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$appPort$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `eier får alle ikke-slettede jobbsøkere, inkludert skjulte`() {
        val eierIdent = "A123456"
        val treffId = opprettTreffMedEier(eierIdent)

        val js1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, VeilederNavIdent("V100001"))
        val js2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, VeilederNavIdent("V100002"))
        val js3 = LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Per"), Etternavn("Olsen"), null, null, null)
        val ider = db.leggTilJobbsøkereMedHendelse(listOf(js1, js2, js3), treffId)

        db.settSynlighet(ider[1], false)
        db.settJobbsøkerStatus(ider[2], JobbsøkerStatus.SLETTET)

        val response = httpPost(formidlingAllePath(treffId), formidlingBody(), eierIdent)
        assertThat(response.statusCode()).isEqualTo(200)

        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(response.body())
        assertThat(resultat.totalt).isEqualTo(2)
        assertThat(resultat.side).isEqualTo(1)
        assertThat(resultat.jobbsøkere).hasSize(2)
        assertThat(resultat.jobbsøkere.map { it.fødselsnummer })
            .containsExactlyInAnyOrder("11111111111", "22222222222")
    }

    @Test
    fun `veileder med jobbsøker-rolle som ikke er eier ser kun egne jobbsøkere`() {
        val eierIdent = "A123456"
        val veilederIdent = "V200002"
        val treffId = opprettTreffMedEier(eierIdent)

        val mineJs = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, VeilederNavIdent(veilederIdent))
        val mineJsSkjult = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Eva"), Etternavn("Berg"), null, null, VeilederNavIdent(veilederIdent))
        val annenJs = LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Kari"), Etternavn("Hansen"), null, null, VeilederNavIdent("V200099"))
        val ingenVeileder = LeggTilJobbsøker(Fødselsnummer("44444444444"), Fornavn("Per"), Etternavn("Olsen"), null, null, null)
        val ider = db.leggTilJobbsøkereMedHendelse(listOf(mineJs, mineJsSkjult, annenJs, ingenVeileder), treffId)
        db.settSynlighet(ider[1], false)

        val response = httpPost(
            formidlingEgnePath(treffId),
            formidlingBody(),
            navIdent = veilederIdent,
            groups = listOf(AzureAdRoller.jobbsøkerrettet),
        )
        assertThat(response.statusCode()).isEqualTo(200)

        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(response.body())
        assertThat(resultat.totalt).isEqualTo(2)
        assertThat(resultat.jobbsøkere.map { it.fødselsnummer })
            .containsExactlyInAnyOrder("11111111111", "22222222222")
    }

    @Test
    fun `slettede jobbsøkere returneres aldri`() {
        val eierIdent = "A123456"
        val treffId = opprettTreffMedEier(eierIdent)

        val js1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        val js2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Slettet"), Etternavn("Person"), null, null, null)
        val ider = db.leggTilJobbsøkereMedHendelse(listOf(js1, js2), treffId)
        db.settJobbsøkerStatus(ider[1], JobbsøkerStatus.SLETTET)

        val response = httpPost(formidlingAllePath(treffId), formidlingBody(), eierIdent)
        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(response.body())

        assertThat(resultat.totalt).isEqualTo(1)
        assertThat(resultat.jobbsøkere.first().fødselsnummer).isEqualTo("11111111111")
    }

    @Test
    fun `bruker uten relevante roller får 403`() {
        val treffId = opprettTreffMedEier("A123456")
        val response = httpPost(
            formidlingAllePath(treffId),
            formidlingBody(),
            navIdent = "B999999",
            groups = emptyList(),
        )
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `veileder som ikke er eier får 403 på formidling-alle`() {
        val eierIdent = "A123456"
        val veilederIdent = "V200002"
        val treffId = opprettTreffMedEier(eierIdent)

        val response = httpPost(
            formidlingAllePath(treffId),
            formidlingBody(),
            navIdent = veilederIdent,
            groups = listOf(AzureAdRoller.jobbsøkerrettet),
        )
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `paginering returnerer riktig side og totalt`() {
        val eierIdent = "A123456"
        val treffId = opprettTreffMedEier(eierIdent)

        val jobbsøkere = (1..7).map { i ->
            LeggTilJobbsøker(
                Fødselsnummer("1111111111$i".take(11).padEnd(11, '0')),
                Fornavn("Fornavn$i"),
                Etternavn("Etternavn$i"),
                null, null, null,
            )
        }
        db.leggTilJobbsøkereMedHendelse(jobbsøkere, treffId)

        val side1 = mapper.readValue<JobbsøkerFormidlingRespons>(
            httpPost(formidlingAllePath(treffId), formidlingBody("antallPerSide" to 3, "side" to 1), eierIdent).body()
        )
        val side2 = mapper.readValue<JobbsøkerFormidlingRespons>(
            httpPost(formidlingAllePath(treffId), formidlingBody("antallPerSide" to 3, "side" to 2), eierIdent).body()
        )
        val side3 = mapper.readValue<JobbsøkerFormidlingRespons>(
            httpPost(formidlingAllePath(treffId), formidlingBody("antallPerSide" to 3, "side" to 3), eierIdent).body()
        )

        assertThat(side1.totalt).isEqualTo(7)
        assertThat(side1.side).isEqualTo(1)
        assertThat(side1.jobbsøkere).hasSize(3)
        assertThat(side2.jobbsøkere).hasSize(3)
        assertThat(side3.jobbsøkere).hasSize(1)

        val alle = (side1.jobbsøkere + side2.jobbsøkere + side3.jobbsøkere).map { it.personTreffId }
        assertThat(alle).doesNotHaveDuplicates().hasSize(7)
    }

    @Test
    fun `fritekst-søk filtrerer på fornavn`() {
        val eierIdent = "A123456"
        val treffId = opprettTreffMedEier(eierIdent)
        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Per"), Etternavn("Olsen"), null, null, null),
            ),
            treffId,
        )

        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(
            httpPost(formidlingAllePath(treffId), formidlingBody("fritekst" to "ola"), eierIdent).body()
        )
        assertThat(resultat.totalt).isEqualTo(1)
        assertThat(resultat.jobbsøkere.single().fornavn).isEqualTo("Ola")
    }

    @Test
    fun `fritekst-søk på 11-sifret tall matcher fødselsnummer`() {
        val eierIdent = "A123456"
        val treffId = opprettTreffMedEier(eierIdent)
        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Hansen"), null, null, null),
            ),
            treffId,
        )

        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(
            httpPost(formidlingAllePath(treffId), formidlingBody("fritekst" to "22222222222"), eierIdent).body()
        )
        assertThat(resultat.totalt).isEqualTo(1)
        assertThat(resultat.jobbsøkere.single().fødselsnummer).isEqualTo("22222222222")
    }
}
