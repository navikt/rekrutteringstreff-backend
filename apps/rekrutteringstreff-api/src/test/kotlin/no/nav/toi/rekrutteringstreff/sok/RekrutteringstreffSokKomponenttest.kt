package no.nav.toi.rekrutteringstreff.sok

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class RekrutteringstreffSokKomponenttest {

    companion object {
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()
        private val mapper = JacksonConfig.mapper

        private lateinit var infra: TestInfrastructureContext
        private lateinit var app: App
    }

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        infra = TestInfrastructureContext(dataSource = db.dataSource, pilotkontorer = listOf("0315"), modiaKlientUrl = wmInfo.httpBaseUrl).also { it.start() }
        app = App(ctx = ApplicationContext(infra), port = appPort).also { it.start() }
    }

    @BeforeEach
    fun setupStubs() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"aktivEnhet": "0315"}""")
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

    private fun sokGet(
        queryParams: String = "",
        navIdent: String = "A123456",
        grupper: List<UUID> = listOf(AzureAdRoller.arbeidsgiverrettet),
    ): HttpResponse<String> {
        val token = infra.authServer.lagToken(infra.authPort, navIdent = navIdent, groups = grupper)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$appPort/api/rekrutteringstreff/sok$queryParams"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun fritekstSøk(fritekst: String, ekstra: String = "", navIdent: String = "A123456") =
        sokGet("?fritekst=${URLEncoder.encode(fritekst, StandardCharsets.UTF_8)}$ekstra", navIdent = navIdent)

    private fun HttpResponse<String>.tilRespons(): RekrutteringstreffSokRespons =
        mapper.readValue(body())

    private fun leggTilArbeidsgiver(treffId: no.nav.toi.rekrutteringstreff.TreffId, orgnavn: String) =
        db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(
                orgnr = Orgnr("912345678"),
                orgnavn = Orgnavn(orgnavn),
                næringskoder = emptyList(),
                gateadresse = "Fyrstikkalleen 1",
                postnummer = "0661",
                poststed = "Oslo",
            ),
            treffId,
        )

    private fun opprettTreffMedEier(
        navIdent: String = "A123456",
        tittel: String = "TestTreff",
        status: RekrutteringstreffStatus = RekrutteringstreffStatus.PUBLISERT,
        kontorId: String = "0315",
    ): no.nav.toi.rekrutteringstreff.TreffId =
        db.opprettRekrutteringstreffMedEierOgKontor(
            navIdent = navIdent,
            tittel = tittel,
            status = status,
            kontorId = kontorId,
        )

    private fun settTidspunkter(
        treffId: no.nav.toi.rekrutteringstreff.TreffId,
        opprettetAvTidspunkt: Instant,
        sistEndret: Instant,
    ) {
        db.dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE rekrutteringstreff SET opprettet_av_tidspunkt = ?, sist_endret = ? WHERE id = ?"
            ).apply {
                setTimestamp(1, Timestamp.from(opprettetAvTidspunkt))
                setTimestamp(2, Timestamp.from(sistEndret))
                setObject(3, treffId.somUuid)
            }.executeUpdate()
        }
    }

    @Test
    fun `sok returnerer tomme resultater når det ikke finnes treff`() {
        val response = sokGet()
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).isEmpty()
        assertThat(respons.antallTotalt).isEqualTo(0)
        assertThat(respons.statusaggregering).isEmpty()
    }

    @Test
    fun `visning ALLE returnerer treff fra alle kontorer og eiere`() {
        opprettTreffMedEier(navIdent = "A123456", tittel = "Mitt Oslo-treff", kontorId = "0315")
        opprettTreffMedEier(navIdent = "B654321", tittel = "Andres Bergen-treff", kontorId = "1201")

        val response = sokGet("?visning=alle")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(2)
        assertThat(respons.treff.map { it.tittel }).containsExactlyInAnyOrder("Mitt Oslo-treff", "Andres Bergen-treff")
    }

    @Test
    fun `visning ALLE for utvikler leser ikke kontor fra Modia`() {
        opprettTreffMedEier(navIdent = "A123456", tittel = "Treff", kontorId = "0315")

        val response = sokGet(
            queryParams = "?visning=alle",
            grupper = listOf(AzureAdRoller.utvikler),
        )

        assertThat(response.statusCode()).isEqualTo(200)
        verify(0, getRequestedFor(urlPathEqualTo("/api/context/v2/aktivenhet")))
    }

    @Test
    fun `sok med visning MINE returnerer kun treff der bruker er eier`() {
        opprettTreffMedEier(navIdent = "A123456", tittel = "Mitt treff")
        opprettTreffMedEier(navIdent = "B654321", tittel = "Andres treff")

        val response = sokGet("?visning=mine")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().tittel).isEqualTo("Mitt treff")
    }

    @Test
    fun `sok med statusfilter returnerer kun treff med valgt status`() {
        opprettTreffMedEier(tittel = "Publisert treff", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreffMedEier(tittel = "Utkast treff", status = RekrutteringstreffStatus.UTKAST)

        val response = sokGet("?statuser=PUBLISERT")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().tittel).isEqualTo("Publisert treff")
    }

    @Test
    fun `sok med kontorfilter returnerer kun treff fra valgte kontorer`() {
        opprettTreffMedEier(tittel = "Kontor A", kontorId = "0315")
        opprettTreffMedEier(tittel = "Kontor B", kontorId = "0502")

        val response = sokGet("?kontorer=0315")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().tittel).isEqualTo("Kontor A")
    }

    @Test
    fun `fritekst filtrerer trefflisten`() {
        opprettTreffMedEier(tittel = "Rekrutteringstreff for sveisere")
        opprettTreffMedEier(tittel = "Jobbmesse for kokker")

        val response = fritekstSøk("sveisere")

        assertThat(response.statusCode()).isEqualTo(200)
        val respons = response.tilRespons()
        assertThat(respons.antallTotalt).isEqualTo(1)
        assertThat(respons.treff.single().tittel).isEqualTo("Rekrutteringstreff for sveisere")
    }

    @Test
    fun `fritekst matcher arbeidsgivernavn`() {
        val treffAId = opprettTreffMedEier(tittel = "Treff A")
        opprettTreffMedEier(tittel = "Treff B")
        leggTilArbeidsgiver(treffAId, orgnavn = "Nordsjø Sveiseservice")

        val respons = fritekstSøk("Nordsjø").tilRespons()

        assertThat(respons.treff).extracting<String> { it.tittel }.containsExactly("Treff A")
    }

    @Test
    fun `blank fritekst gir samme resultat som ingen fritekst`() {
        opprettTreffMedEier(tittel = "Treff A")
        opprettTreffMedEier(tittel = "Treff B")

        assertThat(fritekstSøk("   ").tilRespons().antallTotalt).isEqualTo(2)
        assertThat(sokGet().tilRespons().antallTotalt).isEqualTo(2)
    }

    @Test
    fun `for lang fritekst gir 400`() {
        val response = fritekstSøk("a".repeat(201))
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `fritekst på lengde-grensen gir 200`() {
        val response = fritekstSøk("a".repeat(200))
        assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `fritekst med spesialtegn gir 200 og ikke 500`() {
        opprettTreffMedEier(tittel = "Treff A")

        listOf("\"kokk", "kokk & sveiser", "!()|&:*", "-kokk", "or", "'; DROP TABLE rekrutteringstreff; --")
            .forEach { fritekst ->
                assertThat(fritekstSøk(fritekst).statusCode())
                    .describedAs("fritekst=%s", fritekst)
                    .isEqualTo(200)
            }
    }

    @Test
    fun `fritekst kombinert med statusfilter`() {
        opprettTreffMedEier(tittel = "Sveiser publisert", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreffMedEier(tittel = "Sveiser avlyst", status = RekrutteringstreffStatus.AVLYST)

        val respons = fritekstSøk("sveiser", ekstra = "&statuser=PUBLISERT").tilRespons()

        assertThat(respons.treff).extracting<String> { it.tittel }.containsExactly("Sveiser publisert")
    }

    @Test
    fun `aggregeringene summerer til antallTotalt ved fritekstsok`() {
        opprettTreffMedEier(tittel = "Sveiser 1", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreffMedEier(tittel = "Sveiser 2", status = RekrutteringstreffStatus.AVLYST)
        opprettTreffMedEier(tittel = "Noe annet")

        val respons = fritekstSøk("sveiser").tilRespons()

        assertThat(respons.antallTotalt).isEqualTo(2)
        assertThat(respons.statusaggregering.sumOf { it.antall }).isEqualTo(respons.antallTotalt)
    }

    @Test
    fun `fritekst avslører ikke andres utkast`() {
        opprettTreffMedEier(navIdent = "B654321", tittel = "Hemmelig treff for sveiser", status = RekrutteringstreffStatus.UTKAST)

        assertThat(fritekstSøk("sveise", navIdent = "A123456").tilRespons().antallTotalt).isEqualTo(0)
        assertThat(fritekstSøk("sveise", navIdent = "B654321").tilRespons().antallTotalt).isEqualTo(1)
    }

    @Test
    fun `fritekst respekterer sortering nyeste`() {
        val eldst = opprettTreffMedEier(tittel = "Sveiser eldst")
        val nyest = opprettTreffMedEier(tittel = "Sveiser nyest")
        settTidspunkter(eldst, Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T10:00:00Z"))
        settTidspunkter(nyest, Instant.parse("2026-06-01T10:00:00Z"), Instant.parse("2026-06-01T10:00:00Z"))

        val respons = fritekstSøk("sveiser", ekstra = "&sortering=nyeste").tilRespons()

        assertThat(respons.treff).extracting<String> { it.tittel }
            .containsExactly("Sveiser nyest", "Sveiser eldst")
    }

    @Test
    fun `paginering er stabil når sist_endret er identisk`() {
        val identisk = Instant.parse("2026-01-01T12:00:00Z")
        (1..25).forEach { i ->
            settTidspunkter(opprettTreffMedEier(tittel = "Treff $i"), identisk, identisk)
        }

        val alleIder = (1..3).flatMap { side ->
            sokGet("?side=$side&antallPerSide=10").tilRespons().treff.map { it.id }
        }

        assertThat(alleIder).doesNotHaveDuplicates()
        assertThat(alleIder).hasSize(25)
    }

    @Test
    fun `statusaggregering teller riktig per status`() {
        opprettTreffMedEier(tittel = "Pub1", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreffMedEier(tittel = "Pub2", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreffMedEier(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST)

        val response = sokGet()
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        val publisert = respons.statusaggregering.find { it.verdi == SokStatus.PUBLISERT.name }
        val utkast = respons.statusaggregering.find { it.verdi == SokStatus.UTKAST.name }
        assertThat(publisert?.antall).isEqualTo(2)
        assertThat(utkast?.antall).isEqualTo(1)
    }

    @Test
    fun `paginering fungerer korrekt`() {
        repeat(5) { i ->
            opprettTreffMedEier(tittel = "Treff $i")
        }

        val responseSide0 = sokGet("?side=1&antallPerSide=2")
        val respons0 = mapper.readValue<RekrutteringstreffSokRespons>(responseSide0.body())
        assertThat(respons0.treff).hasSize(2)
        assertThat(respons0.antallTotalt).isEqualTo(5)
        assertThat(respons0.side).isEqualTo(1)
        assertThat(respons0.antallPerSide).isEqualTo(2)

        val responseSide1 = sokGet("?side=2&antallPerSide=2")
        val respons1 = mapper.readValue<RekrutteringstreffSokRespons>(responseSide1.body())
        assertThat(respons1.treff).hasSize(2)

        val responseSide2 = sokGet("?side=3&antallPerSide=2")
        val respons2 = mapper.readValue<RekrutteringstreffSokRespons>(responseSide2.body())
        assertThat(respons2.treff).hasSize(1)
    }

    @Test
    fun `slettede treff vises ikke i søk`() {
        opprettTreffMedEier(tittel = "Synlig", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreffMedEier(tittel = "Slettet", status = RekrutteringstreffStatus.SLETTET)

        val response = sokGet()
        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().tittel).isEqualTo("Synlig")
    }

    @Test
    fun `sok med flere statusfiltre returnerer treff med alle valgte statuser`() {
        opprettTreffMedEier(tittel = "Pub", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreffMedEier(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST)
        opprettTreffMedEier(tittel = "Avlyst", status = RekrutteringstreffStatus.AVLYST)

        val response = sokGet("?statuser=PUBLISERT,UTKAST")
        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(2)
    }

    @Test
    fun `ugyldig visning returnerer 400`() {
        val response = sokGet("?visning=UGYLDIG")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `ugyldig status returnerer 400`() {
        val response = sokGet("?statuser=UGYLDIG_STATUS")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `ugyldig side returnerer 400`() {
        val response = sokGet("?side=ugyldig")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `antallPerSide lik 0 returnerer 400`() {
        val response = sokGet("?antallPerSide=0")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `antallPerSide over maksgrense returnerer 400`() {
        val response = sokGet("?antallPerSide=101")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `negativ antallPerSide returnerer 400`() {
        val response = sokGet("?antallPerSide=-1")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `visning MITT_KONTOR uten aktiv enhet returnerer 400`() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(500))
        )

        val response = sokGet(
            queryParams = "?visning=mitt_kontor",
            grupper = listOf(AzureAdRoller.utvikler),
        )

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `statusaggregering ekskluderer statusfilteret men inkluderer kontorfilteret`() {
        opprettTreffMedEier(tittel = "Oslo pub", status = RekrutteringstreffStatus.PUBLISERT, kontorId = "0315")
        opprettTreffMedEier(tittel = "Bergen pub", status = RekrutteringstreffStatus.PUBLISERT, kontorId = "1201")
        opprettTreffMedEier(tittel = "Oslo utkast", status = RekrutteringstreffStatus.UTKAST, kontorId = "0315")

        val response = sokGet("?statuser=PUBLISERT&kontorer=0315")
        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())

        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().tittel).isEqualTo("Oslo pub")

        val statusAgg = respons.statusaggregering
        val publisert = statusAgg.find { it.verdi == SokStatus.PUBLISERT.name }
        val utkast = statusAgg.find { it.verdi == SokStatus.UTKAST.name }
        assertThat(publisert?.antall).isEqualTo(1)
        assertThat(utkast?.antall).isEqualTo(1)
    }

    @Test
    fun `visning MITT_KONTOR returnerer kun treff fra veileders kontor`() {
        opprettTreffMedEier(navIdent = "B654321", tittel = "Oslo-treff", kontorId = "0315")
        opprettTreffMedEier(navIdent = "C999999", tittel = "Bergen-treff", kontorId = "1201")

        val response = sokGet(
            queryParams = "?visning=mitt_kontor",
            grupper = listOf(AzureAdRoller.utvikler),
        )

        assertThat(response.statusCode()).isEqualTo(200)
        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().tittel).isEqualTo("Oslo-treff")
    }

    @Test
    fun `visning VALGTE_KONTORER med kontorer-param returnerer kun treff fra valgte kontorer`() {
        opprettTreffMedEier(tittel = "Oslo-treff", kontorId = "0315")
        opprettTreffMedEier(tittel = "Bergen-treff", kontorId = "1201")
        opprettTreffMedEier(tittel = "Tromsø-treff", kontorId = "1902")

        val response = sokGet("?visning=valgte_kontorer&kontorer=0315,1201")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(2)
        assertThat(respons.treff.map { it.tittel }).containsExactlyInAnyOrder("Oslo-treff", "Bergen-treff")
    }

    @Test
    fun `visning VALGTE_KONTORER uten kontorer-param returnerer alle treff`() {
        opprettTreffMedEier(tittel = "Oslo-treff", kontorId = "0315")
        opprettTreffMedEier(tittel = "Bergen-treff", kontorId = "1201")

        val response = sokGet("?visning=valgte_kontorer")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(2)
    }

    @Test
    fun `visning MITT_KONTOR ignorerer kontorer-param og returnerer kun treff fra veileders kontor`() {
        opprettTreffMedEier(navIdent = "B654321", tittel = "Oslo-treff", kontorId = "0315")
        opprettTreffMedEier(navIdent = "C999999", tittel = "Bergen-treff", kontorId = "1201")

        val response = sokGet(
            queryParams = "?visning=mitt_kontor&kontorer=1201",
            grupper = listOf(AzureAdRoller.utvikler),
        )

        assertThat(response.statusCode()).isEqualTo(200)
        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().tittel).isEqualTo("Oslo-treff")
    }

    @Test
    fun `sortering SIST_OPPDATERTE returnerer treff sortert etter sist endret dato synkende`() {
        val treff1 = opprettTreffMedEier(tittel = "Gammelt oppdatert")
        val treff2 = opprettTreffMedEier(tittel = "Nylig oppdatert")
        val treff3 = opprettTreffMedEier(tittel = "Sist oppdatert")

        settTidspunkter(treff1, Instant.parse("2025-01-01T10:00:00Z"), Instant.parse("2025-03-01T10:00:00Z"))
        settTidspunkter(treff2, Instant.parse("2025-02-01T10:00:00Z"), Instant.parse("2025-04-01T10:00:00Z"))
        settTidspunkter(treff3, Instant.parse("2025-03-01T10:00:00Z"), Instant.parse("2025-05-01T10:00:00Z"))

        val response = sokGet("?sortering=sist_oppdaterte")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff.map { it.tittel }).containsExactly(
            "Sist oppdatert", "Nylig oppdatert", "Gammelt oppdatert"
        )
    }

    @Test
    fun `sortering NYESTE returnerer treff sortert etter opprettet dato synkende`() {
        val treff1 = opprettTreffMedEier(tittel = "Eldst")
        val treff2 = opprettTreffMedEier(tittel = "Midterst")
        val treff3 = opprettTreffMedEier(tittel = "Nyest")

        settTidspunkter(treff1, Instant.parse("2025-01-01T10:00:00Z"), Instant.parse("2025-06-01T10:00:00Z"))
        settTidspunkter(treff2, Instant.parse("2025-03-01T10:00:00Z"), Instant.parse("2025-04-01T10:00:00Z"))
        settTidspunkter(treff3, Instant.parse("2025-05-01T10:00:00Z"), Instant.parse("2025-02-01T10:00:00Z"))

        val response = sokGet("?sortering=nyeste")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff.map { it.tittel }).containsExactly(
            "Nyest", "Midterst", "Eldst"
        )
    }

    @Test
    fun `sortering ELDSTE returnerer treff sortert etter opprettet dato stigende`() {
        val treff1 = opprettTreffMedEier(tittel = "Eldst")
        val treff2 = opprettTreffMedEier(tittel = "Midterst")
        val treff3 = opprettTreffMedEier(tittel = "Nyest")

        settTidspunkter(treff1, Instant.parse("2025-01-01T10:00:00Z"), Instant.parse("2025-06-01T10:00:00Z"))
        settTidspunkter(treff2, Instant.parse("2025-03-01T10:00:00Z"), Instant.parse("2025-04-01T10:00:00Z"))
        settTidspunkter(treff3, Instant.parse("2025-05-01T10:00:00Z"), Instant.parse("2025-02-01T10:00:00Z"))

        val response = sokGet("?sortering=eldste")
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff.map { it.tittel }).containsExactly(
            "Eldst", "Midterst", "Nyest"
        )
    }

    @Test
    fun `standard sortering er SIST_OPPDATERTE når sortering ikke er angitt`() {
        val treff1 = opprettTreffMedEier(tittel = "Eldre oppdatering")
        val treff2 = opprettTreffMedEier(tittel = "Nyere oppdatering")

        settTidspunkter(treff1, Instant.parse("2025-05-01T10:00:00Z"), Instant.parse("2025-01-01T10:00:00Z"))
        settTidspunkter(treff2, Instant.parse("2025-01-01T10:00:00Z"), Instant.parse("2025-06-01T10:00:00Z"))

        val response = sokGet()
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff.map { it.tittel }).containsExactly(
            "Nyere oppdatering", "Eldre oppdatering"
        )
    }

    @Test
    fun `sok returnerer antall arbeidsgivere og jobbsokere per treff`() {
        val treffId = opprettTreffMedEier(tittel = "Treff med deltakere")

        db.dataSource.connection.use { conn ->
            val internId = conn.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?").use { s ->
                s.setObject(1, treffId.somUuid)
                s.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
            conn.prepareStatement("INSERT INTO arbeidsgiver (rekrutteringstreff_id, orgnr, orgnavn, id) VALUES (?, ?, ?, ?)").use { s ->
                repeat(3) {
                    s.setLong(1, internId)
                    s.setString(2, "99900000$it")
                    s.setString(3, "Bedrift $it")
                    s.setObject(4, UUID.randomUUID())
                    s.addBatch()
                }
                s.executeBatch()
            }
            conn.prepareStatement("INSERT INTO jobbsoker (rekrutteringstreff_id, fodselsnummer, id) VALUES (?, ?, ?)").use { s ->
                repeat(7) {
                    s.setLong(1, internId)
                    s.setString(2, "1234560000$it")
                    s.setObject(3, UUID.randomUUID())
                    s.addBatch()
                }
                s.executeBatch()
            }
        }

        val response = sokGet()
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().antallArbeidsgivere).isEqualTo(3)
        assertThat(respons.treff.first().antallJobbsøkere).isEqualTo(7)
    }

    @Test
    fun `sok returnerer 0 arbeidsgivere og jobbsokere for treff uten deltakere`() {
        opprettTreffMedEier(tittel = "Tomt treff")

        val response = sokGet()
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().antallArbeidsgivere).isEqualTo(0)
        assertThat(respons.treff.first().antallJobbsøkere).isEqualTo(0)
    }

    @Test
    fun `søk returnerer riktig antall jobbsøkere`() {
        val treffId = opprettTreffMedEier(tittel = "Tomt treff")

        val jobbsøkerInvitert = Jobbsøker(
            PersonTreffId(UUID.randomUUID()), treffId, Fødselsnummer("12345678901"),
            Fornavn("Ola"), Etternavn("Nordmann"), null, null, null,
            JobbsøkerStatus.INVITERT
        )

        val jobbsøkerSlettet = Jobbsøker(
            PersonTreffId(UUID.randomUUID()), treffId, Fødselsnummer("12345678902"),
            Fornavn("Ola 2"), Etternavn("Nordmann"), null, null, null,
            JobbsøkerStatus.SLETTET
        )

        val jobbsøkerSvartJaIkkeSynlig = Jobbsøker(
            PersonTreffId(UUID.randomUUID()), treffId, Fødselsnummer("12345678902"),
            Fornavn("Ola 3"), Etternavn("Nordmann"), null, null, null,
            JobbsøkerStatus.SVART_JA
        )

        db.leggTilJobbsøkere(listOf(jobbsøkerInvitert, jobbsøkerSlettet, jobbsøkerSvartJaIkkeSynlig))

        db.settSynlighet(jobbsøkerSvartJaIkkeSynlig.personTreffId, false)

        val response = sokGet()
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().antallJobbsøkere).isEqualTo(1)
    }


    @Test
    fun `søk returnerer 0 antall jobbsøkere som har svart ja for treff uten deltakere`() {
        opprettTreffMedEier(tittel = "Tomt treff")

        val response = sokGet()
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().antallJobbsøkereSvartJa).isEqualTo(0)
    }

    @Test
    fun `søk returnerer riktig antall jobbsøkere som har svart ja`() {
        val treffId = opprettTreffMedEier(tittel = "Tomt treff")

        val jobbsøkerInvitert = Jobbsøker(
            PersonTreffId(UUID.randomUUID()), treffId, Fødselsnummer("12345678901"),
            Fornavn("Ola"), Etternavn("Nordmann"), null, null, null,
            JobbsøkerStatus.INVITERT
        )

        val jobbsøkerSvartJa = Jobbsøker(
            PersonTreffId(UUID.randomUUID()), treffId, Fødselsnummer("12345678902"),
            Fornavn("Ola 2"), Etternavn("Nordmann"), null, null, null,
            JobbsøkerStatus.SVART_JA
        )

        val jobbsøkerSvartJaIkkeSynlig = Jobbsøker(
            PersonTreffId(UUID.randomUUID()), treffId, Fødselsnummer("12345678902"),
            Fornavn("Ola 3"), Etternavn("Nordmann"), null, null, null,
            JobbsøkerStatus.SVART_JA
        )

        db.leggTilJobbsøkere(listOf(jobbsøkerInvitert, jobbsøkerSvartJa, jobbsøkerSvartJaIkkeSynlig))

        db.settSynlighet(jobbsøkerSvartJaIkkeSynlig.personTreffId, false)

        val response = sokGet()
        assertThat(response.statusCode()).isEqualTo(200)

        val respons = mapper.readValue<RekrutteringstreffSokRespons>(response.body())
        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().antallJobbsøkereSvartJa).isEqualTo(1)
    }

    @Test
    fun `ugyldig sortering returnerer 400`() {
        val response = sokGet("?sortering=UGYLDIG")
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `valideringsfeil returnerer ProblemDetails-format`() {
        val response = sokGet("?visning=UGYLDIG")
        assertThat(response.statusCode()).isEqualTo(400)

        val body = mapper.readTree(response.body())
        assertThat(body.has("type")).isTrue()
        assertThat(body.has("title")).isTrue()
        assertThat(body.get("status").asInt()).isEqualTo(400)
        assertThat(body.get("feil").asText()).contains("Ugyldig visning")
    }
}
