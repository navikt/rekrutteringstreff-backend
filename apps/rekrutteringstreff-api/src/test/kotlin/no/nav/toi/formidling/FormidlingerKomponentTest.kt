package no.nav.toi.formidling

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.mockk.every
import io.mockk.mockk
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.formidling.dto.FormidlingDto
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Kontor
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
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
class FormidlingerKomponentTest {

    companion object {
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()
        private val mapper = JacksonConfig.mapper

        private val stillingKlient = mockk<StillingKlient>()
        private val kandidatKlient = mockk<KandidatKlient>(relaxed = true)

        private lateinit var infra: TestInfrastructureContext
        private lateinit var ctx: ApplicationContext
        private lateinit var app: App
    }

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        infra = TestInfrastructureContext(
            dataSource = db.dataSource,
            modiaKlientUrl = wmInfo.httpBaseUrl,
            stillingKlient = stillingKlient,
            kandidatKlient = kandidatKlient,
        ).also { it.start() }
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

    private fun stubMineEnheter(vararg enheter: String) {
        val enheterJson = enheter.joinToString(",") { """{"enhetId":"$it","navn":"Nav Test $it"}""" }
        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"enheter":[$enheterJson]}""")
                )
        )
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

    private fun leggTilArbeidsgiver(treffId: TreffId) = db.leggTilArbeidsgiverMedHendelse(
        LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Testbedrift AS"), emptyList(), null, null, null),
        treffId, "testperson",
    )

    private fun formidlingListeAllePath(treffId: TreffId): String =
        "/api/rekrutteringstreff/${treffId.somUuid}/formidling/liste/alle"

    private fun formidlingListeEgnePath(treffId: TreffId): String =
        "/api/rekrutteringstreff/${treffId.somUuid}/formidling/liste/egne"

    private fun httpGet(
        path: String,
        navIdent: String,
        groups: List<UUID>,
    ): HttpResponse<String> {
        val token = infra.authServer.lagToken(infra.authPort, navIdent = navIdent, groups = groups)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$appPort$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun httpPost(
        path: String,
        body: String,
        navIdent: String,
        groups: List<UUID>,
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

    private fun formidlingPath(treffId: TreffId): String =
        "/api/rekrutteringstreff/${treffId.somUuid}/formidling"

    private fun opprettFormidlingBody(orgnr: String, fødselsnummer: String): String =
        """
        {
            "eierNavKontorEnhetId": "1234",
            "orgnr": "$orgnr",
            "fødselsnumre": ["$fødselsnummer"],
            "stilling": {
                "employer": {
                    "name": "Testbedrift AS",
                    "orgnr": "$orgnr",
                    "publicName": "Testbedrift AS"
                }
            },
            "yrkestittel": "Kokk",
            "janzzKonseptId": "12345"
        }
        """.trimIndent()


    @Test
    fun `veileder ser egne formidlinger via brukertilgang (veileder_navident)`() {
        val eierIdent = "A123456"
        val veilederIdent = "Z111111"
        val treffId = opprettTreffMedEier(eierIdent)
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Egen"), Etternavn("Bruker"), Kontor("1000", "Nav Test"), VeilederNavn("Min Veil"), VeilederNavIdent(veilederIdent)),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Annen"), Etternavn("Bruker"), Kontor("2000", "Nav Andre"), VeilederNavn("Annen Veil"), VeilederNavIdent("Z999999")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, UUID.randomUUID())

        stubMineEnheter()

        val response = httpGet(formidlingListeEgnePath(treffId), veilederIdent, listOf(AzureAdRoller.jobbsøkerrettet))
        assertThat(response.statusCode()).isEqualTo(200)

        val linjer = mapper.readValue<List<FormidlingDto>>(response.body())
        assertThat(linjer).hasSize(1)
        assertThat(linjer.single().fødselsnummer).isEqualTo("11111111111")
    }

    @Test
    fun `veileder ser egne formidlinger via kontortilgang`() {
        val eierIdent = "A123456"
        val veilederIdent = "Z222222"
        val veiledersKontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent)
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Samme"), Etternavn("Kontor"), Kontor(veiledersKontor, "Nav Test"), VeilederNavn("Annen Veil"), VeilederNavIdent("Z999999")),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Annet"), Etternavn("Kontor"), Kontor("9999", "Nav Andre"), VeilederNavn("Annen Veil"), VeilederNavIdent("Z888888")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, UUID.randomUUID())

        stubMineEnheter(veiledersKontor)

        val response = httpGet(formidlingListeEgnePath(treffId), veilederIdent, listOf(AzureAdRoller.jobbsøkerrettet))
        assertThat(response.statusCode()).isEqualTo(200)

        val linjer = mapper.readValue<List<FormidlingDto>>(response.body())
        assertThat(linjer).hasSize(1)
        assertThat(linjer.single().fødselsnummer).isEqualTo("11111111111")
    }

    @Test
    fun `veileder uten egne brukere får tom liste`() {
        val eierIdent = "A123456"
        val veilederIdent = "Z333333"
        val treffId = opprettTreffMedEier(eierIdent)
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Annen"), Etternavn("Bruker"), Kontor("2000", "Nav Andre"), VeilederNavn("Annen Veil"), VeilederNavIdent("Z999999")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID())

        stubMineEnheter()

        val response = httpGet(formidlingListeEgnePath(treffId), veilederIdent, listOf(AzureAdRoller.jobbsøkerrettet))
        assertThat(response.statusCode()).isEqualTo(200)

        val linjer = mapper.readValue<List<FormidlingDto>>(response.body())
        assertThat(linjer).isEmpty()
    }

    @Test
    fun `markedskontakt som tilhører treffets kontor ser alle formidlinger`() {
        val eierIdent = "A123456"
        val markedskontaktIdent = "B200002"
        val felleskontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = felleskontor)
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Aase"), Etternavn("Testesen"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("V999998")),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Bo"), Etternavn("Testesen"), Kontor("1000", "Nav Test"), VeilederNavn("Veil B"), VeilederNavIdent("V999997")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, UUID.randomUUID())

        stubMineEnheter(felleskontor)

        val response = httpGet(formidlingListeAllePath(treffId), markedskontaktIdent, listOf(AzureAdRoller.arbeidsgiverrettet))
        assertThat(response.statusCode()).isEqualTo(200)

        val linjer = mapper.readValue<List<FormidlingDto>>(response.body())
        assertThat(linjer).hasSize(2)
        assertThat(linjer.map { it.fødselsnummer })
            .containsExactlyInAnyOrder("11111111111", "22222222222")
    }

    @Test
    fun `formidlingslisten returnerer yrkestittel og skjuler fødselsnummer for usynlig jobbsøker`() {
        val eierIdent = "A123456"
        val markedskontaktIdent = "B200002"
        val felleskontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = felleskontor)
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Aase"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("V999998")),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Usynlig"), Etternavn("Bø"), Kontor("1000", "Nav Test"), VeilederNavn("Veil B"), VeilederNavIdent("V999997")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID(), yrkestittel = "Utvikler (dataspill)", janzzKonseptId = "19989")
        db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, UUID.randomUUID(), yrkestittel = "Kokk", janzzKonseptId = "12345")

        db.settSynlighet(personTreffIder[1], false)

        stubMineEnheter(felleskontor)

        val response = httpGet(formidlingListeAllePath(treffId), markedskontaktIdent, listOf(AzureAdRoller.arbeidsgiverrettet))
        assertThat(response.statusCode()).isEqualTo(200)

        val linjer = mapper.readValue<List<FormidlingDto>>(response.body())
        assertThat(linjer).hasSize(2)

        val synlig = linjer.single { it.etternavn == "Aase" }
        assertThat(synlig.fødselsnummer).isEqualTo("11111111111")
        assertThat(synlig.yrkestittel).isEqualTo("Utvikler (dataspill)")

        val usynlig = linjer.single { it.etternavn == "Bø" }
        assertThat(usynlig.fødselsnummer).isNull()
        assertThat(usynlig.yrkestittel).isEqualTo("Kokk")
    }

    @Test
    fun `formidlingslisten anonymiserer navn og fødselsnummer for sperret jobbsøker`() {
        val eierIdent = "A123456"
        val markedskontaktIdent = "B200002"
        val felleskontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = felleskontor)
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Sperret"), Etternavn("Aase"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("V999998")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID(), yrkestittel = "Kokk", janzzKonseptId = "12345")

        db.settSperret(personTreffIder[0], true)

        stubMineEnheter(felleskontor)

        val response = httpGet(formidlingListeAllePath(treffId), markedskontaktIdent, listOf(AzureAdRoller.arbeidsgiverrettet))
        assertThat(response.statusCode()).isEqualTo(200)

        val linjer = mapper.readValue<List<FormidlingDto>>(response.body())
        assertThat(linjer).hasSize(1)
        val linje = linjer.single()
        assertThat(linje.sperret).isTrue()
        assertThat(linje.fødselsnummer).isNull()
        assertThat(linje.fornavn).isNull()
        assertThat(linje.etternavn).isNull()
        assertThat(linje.yrkestittel).isEqualTo("Kokk")
    }

    @Test
    fun `formidlingslisten anonymiserer jobbsøker som både er usynlig og sperret`() {
        val eierIdent = "A123456"
        val markedskontaktIdent = "B200002"
        val felleskontor = "0314"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = felleskontor)
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Sperret"), Etternavn("Aase"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("V999998")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID(), yrkestittel = "Kokk", janzzKonseptId = "12345")

        db.settSynlighet(personTreffIder[0], false)
        db.settSperret(personTreffIder[0], true)

        stubMineEnheter(felleskontor)

        val response = httpGet(formidlingListeAllePath(treffId), markedskontaktIdent, listOf(AzureAdRoller.arbeidsgiverrettet))
        assertThat(response.statusCode()).isEqualTo(200)

        val linjer = mapper.readValue<List<FormidlingDto>>(response.body())
        assertThat(linjer).hasSize(1)
        val linje = linjer.single()
        assertThat(linje.sperret).isTrue()
        assertThat(linje.fødselsnummer).isNull()
        assertThat(linje.fornavn).isNull()
        assertThat(linje.etternavn).isNull()
        assertThat(linje.yrkestittel).isEqualTo("Kokk")
    }

    @Test
    fun `opprett formidling lagrer formidling og returnerer 201`() {
        val eierIdent = "A123456"
        val orgnr = "123456789"
        val treffId = opprettTreffMedEier(eierIdent)
        leggTilArbeidsgiver(treffId)
        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Aase"), Etternavn("Testesen"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("V999998")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        val kandidatlisteId = UUID.randomUUID()
        every {
            stillingKlient.opprettFormidlingStillingOgKandidatliste(any(), any())
        } returns OpprettFormidlingStillingRespons(stillingsId = stillingId, kandidatlisteId = kandidatlisteId)

        val response = httpPost(
            formidlingPath(treffId),
            opprettFormidlingBody(orgnr, "11111111111"),
            eierIdent,
            listOf(AzureAdRoller.arbeidsgiverrettet),
        )

        assertThat(response.statusCode()).isEqualTo(201)
        val lagrede = ctx.formidlingService.hentAlleFormidlingerForTreff(treffId)
        assertThat(lagrede).hasSize(1)
        val lagret = lagrede.single()
        assertThat(lagret.fødselsnummer).isEqualTo("11111111111")
        assertThat(lagret.yrkestittel).isEqualTo("Kokk")
    }

    @Test
    fun `opprett formidling for sperret jobbsøker gir 403 med hint`() {
        val eierIdent = "A123456"
        val orgnr = "123456789"
        val treffId = opprettTreffMedEier(eierIdent)
        leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Sperret"), Etternavn("Aase"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("V999998")),
            ),
            treffId, "testperson",
        )
        db.settSperret(personTreffIder[0], true)

        val response = httpPost(
            formidlingPath(treffId),
            opprettFormidlingBody(orgnr, "11111111111"),
            eierIdent,
            listOf(AzureAdRoller.arbeidsgiverrettet),
        )

        assertThat(response.statusCode()).isEqualTo(403)
        val problem = mapper.readTree(response.body())
        assertThat(problem.get("status").asInt()).isEqualTo(403)
        assertThat(problem.get("hint").asText()).isEqualTo("Jobbsøkeren har adressebeskyttelse og kan ikke formidles.")
        assertThat(problem.get("feil").asText()).isEqualTo("Jobbsøker med adressebeskyttelse kan ikke formidles.")
        assertThat(ctx.formidlingService.hentAlleFormidlingerForTreff(treffId)).isEmpty()
    }

    @Test
    fun `opprett formidling for skjult jobbsøker lagrer formidling og returnerer 201`() {
        val eierIdent = "A123456"
        val orgnr = "123456789"
        val treffId = opprettTreffMedEier(eierIdent)
        leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Skjult"), Etternavn("Aase"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("V999998")),
            ),
            treffId, "testperson",
        )
        db.settSynlighet(personTreffIder[0], false)
        val stillingId = UUID.randomUUID()
        val kandidatlisteId = UUID.randomUUID()
        every {
            stillingKlient.opprettFormidlingStillingOgKandidatliste(any(), any())
        } returns OpprettFormidlingStillingRespons(stillingsId = stillingId, kandidatlisteId = kandidatlisteId)

        val response = httpPost(
            formidlingPath(treffId),
            opprettFormidlingBody(orgnr, "11111111111"),
            eierIdent,
            listOf(AzureAdRoller.arbeidsgiverrettet),
        )

        assertThat(response.statusCode()).isEqualTo(201)
        assertThat(ctx.formidlingService.hentAlleFormidlingerForTreff(treffId)).hasSize(1)
    }

    @Test
    fun `markedskontakt som ikke tilhører treffets kontor får 403`() {
        val eierIdent = "A123456"
        val markedskontaktIdent = "B200002"
        val treffId = opprettTreffMedEier(eierIdent, opprettetAvKontor = "0101")
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Aase"), Etternavn("Testesen"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("V999998")),
            ),
            treffId, "testperson",
        )
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, UUID.randomUUID(), UUID.randomUUID())

        stubMineEnheter("0202")

        val response = httpGet(formidlingListeAllePath(treffId), markedskontaktIdent, listOf(AzureAdRoller.arbeidsgiverrettet))
        assertThat(response.statusCode()).isEqualTo(403)
    }
}
