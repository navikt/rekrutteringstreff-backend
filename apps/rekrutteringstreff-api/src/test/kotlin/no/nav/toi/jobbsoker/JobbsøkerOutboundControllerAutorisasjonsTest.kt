package no.nav.toi.rekrutteringstreff.no.nav.toi.jobbsoker

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.App
import no.nav.toi.ApplicationContext
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.JacksonConfig
import no.nav.toi.httpClient
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.Navkontor
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
import no.nav.toi.lagToken
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.UUID
import no.nav.toi.TestInfrastructureContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerOutboundControllerAutorisasjonsTest {
    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
        private lateinit var gyldigJobbsøkerId: PersonTreffId
    }
    private val database = TestDatabase()
    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        infra = TestInfrastructureContext(dataSource = database.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl, kandidatsøkKlientUrl = wmInfo.httpBaseUrl)
        infra.start()
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
                        .withBody(
                            """
                            {
                                "aktivEnhet": "1234"
                            }
                            """.trimIndent()
                        )
                )
        )
        stubFor(
            post("/api/arena-kandidatnr")
                .withHeader("Authorization", matching("Bearer .*"))
                .withRequestBody(equalToJson("""{"fodselsnummer":"12345678902"}"""))
                .willReturn(
                    okJson("""{"arenaKandidatnr":"K654321"}""")
                )
        )
    }

    @AfterAll
    fun tearDown() {
        infra.stop()
        app.close()
    }

    @BeforeEach
    fun setup() {
        ctx.rekrutteringstreffService.opprett(OpprettRekrutteringstreffInternalDto("Tittel", "A213456", "Kontor", ZonedDateTime.now()))
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id
        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer = Fødselsnummer("12345678902"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("NAV Oslo"),
            veilederNavn = VeilederNavn("Espen Askeladd"),
            veilederNavIdent = VeilederNavIdent("NAV456")
        )
        ctx.jobbsøkerService.leggTilJobbsøkere(
            jobbsøkere = listOf(leggTilJobbsøker),
            treffId = gyldigRekrutteringstreff,
            navIdent = "NAV456"
        )
        gyldigJobbsøkerId = ctx.jobbsøkerRepository.hentJobbsøkere(gyldigRekrutteringstreff).first().personTreffId
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler)),
        Jobbsøkerrettet(listOf(jobbsøkerrettet))
    }

    private fun autorisasjonsCases() = listOf(
        Arguments.of(Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
    ).stream()

    @ParameterizedTest
    @MethodSource("autorisasjonsCases")
    fun `test autorisasjon for hentKandidatnummer`(gruppetilhørighet: Gruppe, forventetStatusKode: Int) {
        val request = HttpRequest.newBuilder().GET()
            .uri(URI("http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/jobbsoker/${gyldigJobbsøkerId.somString}/kandidatnummer"))
            .header(
                "Authorization",
                "Bearer ${infra.authServer.lagToken(authPort = infra.authPort, groups = gruppetilhørighet.somStringListe, navIdent = "A213456").serialize()}")
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(forventetStatusKode, response.statusCode())
    }
}