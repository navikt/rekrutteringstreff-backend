package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.innlegg

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
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
import no.nav.toi.lagToken
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.innlegg.OpprettInnleggRequestDto
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
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NO_CONTENT
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.UUID
import no.nav.toi.TestInfrastructureContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class InnleggAutorisasjonsTest {

    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
        private lateinit var gyldigInnleggId: UUID
    }
    private val database = TestDatabase()
    private val erEier = true
    private val erIkkeEier = false
    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        infra = TestInfrastructureContext(dataSource = database.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl)
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
    }

    @AfterAll
    fun tearDown() {
        infra.stop()
        app.close()
    }

    @BeforeEach
    fun setup() {
        ctx.rekrutteringstreffService.opprett(
            OpprettRekrutteringstreffInternalDto(
                "Tittel",
                "A213456",
                "Kontor",
                ZonedDateTime.now()
            )
        )
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id

        ctx.innleggRepository.opprett(
            gyldigRekrutteringstreff,
            OpprettInnleggRequestDto(
                tittel = "Innlegg Tittel",
                opprettetAvPersonNavn = "Navn Navnesen",
                opprettetAvPersonBeskrivelse = "Beskrivelse",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = ""
            ),
            "A213456"
        )
        gyldigInnleggId = ctx.innleggRepository.hentForTreff(gyldigRekrutteringstreff).first().id
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Endepunkt(
        val url: () -> String,
        val metode: () -> HttpRequest.Builder,
    ) {
        HentAlleInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg" },
            { HttpRequest.newBuilder().GET() }),
        HentInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg/$gyldigInnleggId" },
            { HttpRequest.newBuilder().GET() }),
        OpprettInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg" },
            {
                HttpRequest.newBuilder().POST(
                    HttpRequest.BodyPublishers.ofString(
                        JacksonConfig.mapper.writeValueAsString(
                            OpprettInnleggRequestDto(
                                tittel = "Nytt Innlegg",
                                opprettetAvPersonNavn = "Navn Navnesen",
                                opprettetAvPersonBeskrivelse = "Beskrivelse",
                                sendesTilJobbsokerTidspunkt = null,
                                htmlContent = ""
                            )
                        )
                    )
                )
            }
        ),
        OppdaterInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg/$gyldigInnleggId" },
            {
                HttpRequest.newBuilder().PUT(
                    HttpRequest.BodyPublishers.ofString(
                        JacksonConfig.mapper.writeValueAsString(
                            OpprettInnleggRequestDto(
                                tittel = "Oppdatert Tittel",
                                opprettetAvPersonNavn = "Navn Navnesen",
                                opprettetAvPersonBeskrivelse = "Beskrivelse",
                                sendesTilJobbsokerTidspunkt = null,
                                htmlContent = ""
                            )
                        )
                    )
                )
            }),
        SlettInnlegg(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/innlegg/$gyldigInnleggId" },
            { HttpRequest.newBuilder().DELETE() }
        )
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler)),
        Jobbsøkerrettet(listOf(jobbsøkerrettet))
    }

    private fun autorisasjonsCases() = listOf(
        Arguments.of(Endepunkt.HentAlleInnlegg, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentAlleInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentAlleInnlegg, Gruppe.Jobbsøkerrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentAlleInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.HentInnlegg, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentInnlegg, Gruppe.Jobbsøkerrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Utvikler, HTTP_CREATED),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_CREATED),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Utvikler, HTTP_NO_CONTENT),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Arbeidsgiverrettet, HTTP_NO_CONTENT),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
    ).stream()

    private fun autorisasjonsCaserMedEier() = listOf(
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Utvikler, erIkkeEier, HTTP_CREATED),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Arbeidsgiverrettet, erEier, HTTP_CREATED),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OpprettInnlegg, Gruppe.Jobbsøkerrettet, erEier, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Utvikler, erIkkeEier, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OppdaterInnlegg, Gruppe.Jobbsøkerrettet, erEier, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Utvikler, erIkkeEier, HTTP_NO_CONTENT),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Arbeidsgiverrettet, erEier, HTTP_NO_CONTENT),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettInnlegg, Gruppe.Jobbsøkerrettet, erEier, HTTP_FORBIDDEN),
    ).stream()

    @ParameterizedTest
    @MethodSource("autorisasjonsCases")
    fun testEndepunkt(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, expectedStatus: Int) {
        ctx.eierRepository.leggTil(gyldigRekrutteringstreff, listOf("A000001"))

        val request = endepunkt.metode()
            .uri(URI(endepunkt.url()))
            .header(
                "Authorization",
                "Bearer ${infra.authServer.lagToken(infra.authPort, groups = gruppetilhørighet.somStringListe).serialize()}"
            )
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(expectedStatus, response.statusCode())
    }

    @ParameterizedTest
    @MethodSource("autorisasjonsCaserMedEier")
    fun testEndepunktMedEier(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, erEier: Boolean, expectedStatus: Int) {
        if (erEier) {
            ctx.eierRepository.leggTil(gyldigRekrutteringstreff, listOf("A000001"))
        }

        val request = endepunkt.metode()
            .uri(URI(endepunkt.url()))
            .header(
                "Authorization",
                "Bearer ${infra.authServer.lagToken(infra.authPort, groups = gruppetilhørighet.somStringListe).serialize()}"
            )
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(expectedStatus, response.statusCode())
    }
}