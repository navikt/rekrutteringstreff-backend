package no.nav.toi.jobbsoker.sok

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerFormidlingKomponentTest {

    companion object {
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()
        private val mapper = JacksonConfig.mapper

        private lateinit var infra: TestInfrastructureContext

        private lateinit var ctx: ApplicationContext
        private lateinit var app: App
    }

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        infra = TestInfrastructureContext(dataSource = db.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl).also { it.start() }
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
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
        infra.stop()
        app.close()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    private fun opprettTreffMedEier(eierIdent: String, opprettetAvKontor: String = "Original Kontor"): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(
            navIdent = eierIdent,
            tittel = "TestTreff",
            opprettetAvNavkontorEnhetId = opprettetAvKontor,
        )
        ctx.eierRepository.leggTil(treffId, listOf(eierIdent))
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
        val token = infra.authServer.lagToken(infra.authPort, navIdent = navIdent, groups = groups)
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
    fun `veileder finner egne jobbsøkere selv om ident er lagret med lowercase`() {
        val eierIdent = "A123456"
        val veilederIdent = "Z993798"
        val treffId = opprettTreffMedEier(eierIdent)

        val personTreffId = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    Fødselsnummer("11111111111"),
                    Fornavn("Ola"),
                    Etternavn("Nordmann"),
                    null,
                    null,
                    VeilederNavIdent(veilederIdent),
                )
            ),
            treffId,
        ).single()

        db.dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE jobbsoker SET veileder_navident = ? WHERE id = ?").use { stmt ->
                stmt.setString(1, veilederIdent.lowercase())
                stmt.setObject(2, personTreffId.somUuid)
                stmt.executeUpdate()
            }
        }

        val response = httpPost(
            formidlingEgnePath(treffId),
            formidlingBody(),
            navIdent = veilederIdent,
            groups = listOf(AzureAdRoller.jobbsøkerrettet),
        )
        assertThat(response.statusCode()).isEqualTo(200)

        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(response.body())
        assertThat(resultat.totalt).isEqualTo(1)
        assertThat(resultat.jobbsøkere.single().fødselsnummer).isEqualTo("11111111111")
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
    fun `arbeidsgiverrettet ikke-eier får alle dersom treffet er tilknyttet veileders kontor`() {
        val eierIdent = "A123456"
        val ikkeEierIdent = "B200002"
        val felleskontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = felleskontor)

        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"enheter":[{"enhetId":"$felleskontor","navn":"Nav Testkontor"}]}""")
                )
        )

        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Aase"), Etternavn("Testesen"), null, null, VeilederNavIdent("V999998")),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Bo"), Etternavn("Testesen"), null, null, VeilederNavIdent("V999997")),
            ),
            treffId,
        )

        val response = httpPost(
            formidlingAllePath(treffId),
            formidlingBody(),
            navIdent = ikkeEierIdent,
            groups = listOf(AzureAdRoller.arbeidsgiverrettet),
        )
        assertThat(response.statusCode()).isEqualTo(200)

        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(response.body())
        assertThat(resultat.totalt).isEqualTo(2)
        assertThat(resultat.jobbsøkere.map { it.fødselsnummer })
            .containsExactlyInAnyOrder("11111111111", "22222222222")
    }

    @Test
    fun `arbeidsgiverrettet ikke-eier får 403 dersom veileders kontor ikke er tilknyttet treffet`() {
        val eierIdent = "A123456"
        val ikkeEierIdent = "B200002"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = "0101")

        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"enheter":[{"enhetId":"0202","navn":"Nav Annet Kontor"}]}""")
                )
        )

        val response = httpPost(
            formidlingAllePath(treffId),
            formidlingBody(),
            navIdent = ikkeEierIdent,
            groups = listOf(AzureAdRoller.arbeidsgiverrettet),
        )
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `arbeidsgiverrettet ikke-eier får ikke tilgang via jobbsøkers kontor`() {
        val eierIdent = "A123456"
        val ikkeEierIdent = "B200002"
        val jobbsøkersKontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = "0101")

        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"enheter":[{"enhetId":"$jobbsøkersKontor","navn":"Nav Testkontor"}]}""")
                )
        )

        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = Fødselsnummer("11111111111"),
                    fornavn = Fornavn("Aase"),
                    etternavn = Etternavn("Testesen"),
                    veilederNavIdent = VeilederNavIdent("V999998"),
                    kontor = Kontor(kontornummer = jobbsøkersKontor, kontornavn = null),
                ),
            ),
            treffId,
        )

        val response = httpPost(
            formidlingAllePath(treffId),
            formidlingBody(),
            navIdent = ikkeEierIdent,
            groups = listOf(AzureAdRoller.arbeidsgiverrettet),
        )

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `arbeidsgiverrettet får ikke bruke formidling-egne selv om jobbsøkers kontor matcher`() {
        val eierIdent = "A123456"
        val arbeidsgiverrettetIdent = "B200002"
        val jobbsøkersKontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent)

        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"enheter":[{"enhetId":"$jobbsøkersKontor","navn":"Nav Testkontor"}]}""")
                )
        )
        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = Fødselsnummer("11111111111"),
                    fornavn = Fornavn("Aase"),
                    etternavn = Etternavn("Testesen"),
                    veilederNavIdent = VeilederNavIdent("V999998"),
                    kontor = Kontor(kontornummer = jobbsøkersKontor, kontornavn = null),
                ),
            ),
            treffId,
        )

        val response = httpPost(
            formidlingEgnePath(treffId),
            formidlingBody(),
            navIdent = arbeidsgiverrettetIdent,
            groups = listOf(AzureAdRoller.arbeidsgiverrettet),
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

    @Test
    fun `formidling-egne returnerer jobbsøkere som matcher veilederident eller veileders kontor`() {
        val eierIdent = "TESTEIER"
        val veilederIdent = "TESTVEILEDER"
        val annenVeilederIdent = "ANNENVEILEDER"
        val veiledersKontor = "KONTOR-A"
        val annetKontor = "KONTOR-B"

        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"enheter":[{"enhetId":"$veiledersKontor","navn":"Kontor Test"}]}""")
                )
        )

        fun jobbsøker(fødselsnummer: String, veilederNavIdent: String, kontornummer: String) =
            LeggTilJobbsøker(
                fødselsnummer = Fødselsnummer(fødselsnummer),
                fornavn = Fornavn("Filter"),
                etternavn = Etternavn("Rad${fødselsnummer.takeLast(2)}"),
                veilederNavIdent = VeilederNavIdent(veilederNavIdent),
                kontor = Kontor(kontornummer = kontornummer, kontornavn = null),
            )

        fun hentEgneFødselsnumre(treffId: TreffId): List<String> {
            val response = httpPost(
                formidlingEgnePath(treffId),
                formidlingBody(),
                navIdent = veilederIdent,
                groups = listOf(AzureAdRoller.jobbsøkerrettet),
            )
            assertThat(response.statusCode()).isEqualTo(200)
            return mapper.readValue<JobbsøkerFormidlingRespons>(response.body())
                .jobbsøkere
                .map { it.fødselsnummer }
        }

        val treffUtenMatch = opprettTreffMedEier(eierIdent)
        db.leggTilJobbsøkereMedHendelse(
            listOf(jobbsøker("11111111111", annenVeilederIdent, annetKontor)),
            treffUtenMatch,
        )
        assertThat(hentEgneFødselsnumre(treffUtenMatch)).isEmpty()

        val treffMedIdentmatch = opprettTreffMedEier(eierIdent)
        db.leggTilJobbsøkereMedHendelse(
            listOf(jobbsøker("22222222222", veilederIdent, annetKontor)),
            treffMedIdentmatch,
        )
        assertThat(hentEgneFødselsnumre(treffMedIdentmatch)).containsExactly("22222222222")

        val treffMedKontormatch = opprettTreffMedEier(eierIdent)
        db.leggTilJobbsøkereMedHendelse(
            listOf(jobbsøker("33333333333", annenVeilederIdent, veiledersKontor)),
            treffMedKontormatch,
        )
        assertThat(hentEgneFødselsnumre(treffMedKontormatch)).containsExactly("33333333333")

        val treffMedIdentOgKontormatch = opprettTreffMedEier(eierIdent)
        db.leggTilJobbsøkereMedHendelse(
            listOf(
                jobbsøker("44444444444", annenVeilederIdent, veiledersKontor),
                jobbsøker("55555555555", veilederIdent, annetKontor),
                jobbsøker("66666666666", annenVeilederIdent, annetKontor),
            ),
            treffMedIdentOgKontormatch,
        )
        assertThat(hentEgneFødselsnumre(treffMedIdentOgKontormatch))
            .containsExactlyInAnyOrder("44444444444", "55555555555")
    }

    @Test
    fun `ikke-eier får se jobbsøker tilknyttet samme kontornummer som veileder`() {
        val eierIdent = "A123456"
        val veilederIdent = "V300003"
        val felleskontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent)

        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"enheter":[{"enhetId":"$felleskontor","navn":"Nav Testkontor"}]}"""
                        )
                )
        )

        val sammeKontor = LeggTilJobbsøker(
            fødselsnummer = Fødselsnummer("11111111111"),
            fornavn = Fornavn("Aase"),
            etternavn = Etternavn("Testesen"),
            veilederNavIdent = VeilederNavIdent("V999998"),
            kontor = Kontor(kontornummer = felleskontor, kontornavn = null),
        )
        val annetKontor = LeggTilJobbsøker(
            fødselsnummer = Fødselsnummer("22222222222"),
            fornavn = Fornavn("Bo"),
            etternavn = Etternavn("Testesen"),
            veilederNavIdent = VeilederNavIdent("V999997"),
            kontor = Kontor(kontornummer = "9999", kontornavn = null),
        )
        val utenKontor = LeggTilJobbsøker(
            Fødselsnummer("33333333333"),
            Fornavn("Ce"),
            Etternavn("Testesen"),
            null,
            null,
            VeilederNavIdent("V999996"),
        )
        db.leggTilJobbsøkereMedHendelse(listOf(sammeKontor, annetKontor, utenKontor), treffId)

        val response = httpPost(
            formidlingEgnePath(treffId),
            formidlingBody(),
            navIdent = veilederIdent,
            groups = listOf(AzureAdRoller.jobbsøkerrettet),
        )
        assertThat(response.statusCode()).isEqualTo(200)

        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(response.body())
        assertThat(resultat.jobbsøkere.map { it.fødselsnummer })
            .containsExactly("11111111111")
    }

    @Test
    fun `utvikler som ikke er eier får alle jobbsøkere uten å sjekke treffkontor`() {
        val eierIdent = "A123456"
        val utviklerIdent = "U900001"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = "0101")

        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Aase"), Etternavn("Testesen"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Bo"), Etternavn("Testesen"), null, null, null),
            ),
            treffId,
        )

        val response = httpPost(
            formidlingAllePath(treffId),
            formidlingBody(),
            navIdent = utviklerIdent,
            groups = listOf(AzureAdRoller.arbeidsgiverrettet, AzureAdRoller.utvikler),
        )
        assertThat(response.statusCode()).isEqualTo(200)

        val resultat = mapper.readValue<JobbsøkerFormidlingRespons>(response.body())
        assertThat(resultat.totalt).isEqualTo(2)

        verify(0, getRequestedFor(urlPathEqualTo("/api/decorator")))
    }

    @Test
    fun `arbeidsgiverrettet ikke-eier får 502 dersom Modia-decorator feiler`() {
        val eierIdent = "A123456"
        val ikkeEierIdent = "B200002"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = "0101")

        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(aResponse().withStatus(500).withBody("Modia ute"))
        )

        val response = httpPost(
            formidlingAllePath(treffId),
            formidlingBody(),
            navIdent = ikkeEierIdent,
            groups = listOf(AzureAdRoller.arbeidsgiverrettet),
        )
        assertThat(response.statusCode()).isEqualTo(502)

        val problemDetails = mapper.readValue<ProblemDetails>(response.body())
        assertThat(problemDetails.feil).isEqualTo("Klarte ikke å hente Modia-enheter. Prøv igjen senere.")
    }

}
