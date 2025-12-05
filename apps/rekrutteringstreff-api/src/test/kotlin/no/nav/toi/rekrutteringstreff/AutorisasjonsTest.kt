package no.nav.toi.rekrutteringstreff

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
private class AutorisasjonsTest {

    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val rekrutteringstreffRepository = RekrutteringstreffRepository(database.dataSource)
    private val eierRepository = EierRepository(database.dataSource)

    private val erEier = true
    private val erIkkeEier = false

    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        authServer.start(port = authPort)
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
            dataSource = database.dataSource,
            arbeidsgiverrettet = arbeidsgiverrettet,
            utvikler = utvikler,
            kandidatsokApiUrl = "",
            kandidatsokScope = "",
            rapidsConnection = TestRapid(),
            isRunning = {true},
            isReady = {true},
            accessTokenClient = accessTokenClient,
            modiaKlient = ModiaKlient(
                modiaContextHolderUrl = wmInfo.httpBaseUrl,
                modiaContextHolderScope = "",
                accessTokenClient = accessTokenClient,
                httpClient = httpClient
            ),
            pilotkontorer = listOf("1234")
        ).also { it.start() }
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

    @BeforeEach
    fun setup() {
        rekrutteringstreffRepository.opprett(OpprettRekrutteringstreffInternalDto("Tittel", "A213456", "Kontor", ZonedDateTime.now()))
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Endepunkt(
        val url: () -> String,
        val metode: () -> HttpRequest.Builder,
    ) {
        OpprettRekrutteringstreff({ "http://localhost:$appPort/api/rekrutteringstreff" }, {HttpRequest.newBuilder().POST(
            HttpRequest.BodyPublishers.ofString("""
                {
                    "opprettetAvNavkontorEnhetId": "NAV-kontor"
                }
                """.trimIndent())

        )}),
        HentAlleRekrutteringstreff({ "http://localhost:$appPort/api/rekrutteringstreff" }, {HttpRequest.newBuilder().GET()}),
        HentRekrutteringstreff(
            { "http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}" },
            {HttpRequest.newBuilder().GET()}
        ),
        OppdaterRekrutteringstreff(
            { "http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}" },
            {
                HttpRequest.newBuilder().PUT(
                    HttpRequest.BodyPublishers.ofString(
                        JacksonConfig.mapper.writeValueAsString(
                            OppdaterRekrutteringstreffDto(
                                tittel = "Oppdatert Tittel",
                                beskrivelse = "Oppdatert beskrivelse",
                                fraTid = nowOslo().minusHours(2),
                                tilTid = nowOslo().plusHours(3),
                                svarfrist = nowOslo().minusDays(1),
                                gateadresse = "Oppdatert gateadresse",
                                postnummer = "1234",
                                poststed = "Oppdatert poststed",
                                kommune = "Oppdatert kommune",
                                kommunenummer = "0301",
                                fylke = "Oppdatert fylke",
                                fylkesnummer = "01",
                            )
                        )
                    )
                )
            }),
        SlettRekrutteringstreff(
            { "http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}" },
            {HttpRequest.newBuilder().DELETE()}
        )
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler))
    }

    private fun autorisasjonsCases() = listOf(
        Arguments.of(Endepunkt.OpprettRekrutteringstreff, Gruppe.Utvikler, HTTP_CREATED),
        Arguments.of(Endepunkt.OpprettRekrutteringstreff, Gruppe.Arbeidsgiverrettet, HTTP_CREATED),
        Arguments.of(Endepunkt.OpprettRekrutteringstreff, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.HentAlleRekrutteringstreff, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentAlleRekrutteringstreff, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentAlleRekrutteringstreff, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.HentRekrutteringstreff, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentRekrutteringstreff, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentRekrutteringstreff, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.OppdaterRekrutteringstreff, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterRekrutteringstreff, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterRekrutteringstreff, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettRekrutteringstreff, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.SlettRekrutteringstreff, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.SlettRekrutteringstreff, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
    ).stream()

    private fun autorisasjonsCaserMedEier() = listOf(
        Arguments.of(Endepunkt.OppdaterRekrutteringstreff, Gruppe.Utvikler, erIkkeEier, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterRekrutteringstreff, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.OppdaterRekrutteringstreff, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettRekrutteringstreff, Gruppe.Utvikler, erIkkeEier, HTTP_OK),
        Arguments.of(Endepunkt.SlettRekrutteringstreff, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.SlettRekrutteringstreff, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
    ).stream()

    @ParameterizedTest
    @MethodSource("autorisasjonsCases")
    fun testEndepunkt(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, expectedStatus: Int) {
        eierRepository.leggTil(gyldigRekrutteringstreff, listOf("A000001"))

        val request = endepunkt.metode()
            .uri(URI(endepunkt.url()))
            .header(
                "Authorization",
                "Bearer ${authServer.lagToken(authPort, groups = gruppetilhørighet.somStringListe).serialize()}"
            )
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(expectedStatus, response.statusCode())
    }

    @ParameterizedTest
    @MethodSource("autorisasjonsCaserMedEier")
    fun testEndepunktMedEier(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, erEier: Boolean, expectedStatus: Int) {
        if (erEier) {
            eierRepository.leggTil(gyldigRekrutteringstreff, listOf("A000001"))
        }

        val request = endepunkt.metode()
            .uri(URI(endepunkt.url()))
            .header(
                "Authorization",
                "Bearer ${authServer.lagToken(authPort, groups = gruppetilhørighet.somStringListe).serialize()}")
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(expectedStatus, response.statusCode())
    }
}
