package no.nav.toi.rekrutteringstreff.no.nav.toi.arbeidsgiver

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AccessTokenClient
import no.nav.toi.App
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.JacksonConfig
import no.nav.toi.TestRapid
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Næringskode
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.arbeidsgiver.dto.LeggTilArbeidsgiverDto
import no.nav.toi.httpClient
import no.nav.toi.lagToken
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
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
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NO_CONTENT
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class ArbeidsgiverAutorisasjonsTest {

    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
        private lateinit var arbeidsgiverId: ArbeidsgiverTreffId
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val rekrutteringstreffRepository = RekrutteringstreffRepository(database.dataSource)
    private val arbeidsgiverRepository = ArbeidsgiverRepository(database.dataSource, JacksonConfig.mapper)

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
            jobbsøkerrettet = jobbsøkerrettet,
            arbeidsgiverrettet = arbeidsgiverrettet,
            utvikler = utvikler,
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
        rekrutteringstreffRepository.opprett(
            OpprettRekrutteringstreffInternalDto(
                "Tittel",
                "A000001",
                "Kontor",
                ZonedDateTime.now()
            )
        )

        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id
        arbeidsgiverRepository.leggTil(
            LeggTilArbeidsgiver(
                orgnr = Orgnr("123456789"),
                orgnavn = Orgnavn("Example Company"),
                næringskoder = listOf(
                    Næringskode(
                        kode = "47.111",
                        beskrivelse = "Detaljhandel med bredt varesortiment uten salg av drivstoff"
                    )
                ),
                gateadresse = "Gate 1",
                postnummer = "2345",
                poststed = "OSLO"
            ), gyldigRekrutteringstreff, "A000001"
        )
        arbeidsgiverId = arbeidsgiverRepository.hentArbeidsgiver(gyldigRekrutteringstreff, Orgnr("123456789"))?.arbeidsgiverTreffId!!
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Endepunkt(
        val url: () -> String,
        val metode: () -> HttpRequest.Builder
    ) {
        LeggTilArbeidsgiver({"http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/arbeidsgiver"}, {
            HttpRequest.newBuilder().POST(
                HttpRequest.BodyPublishers.ofString(
                JacksonConfig.mapper.writeValueAsString(
                    LeggTilArbeidsgiverDto(
                        organisasjonsnummer = "123456789",
                        navn = "Example Company",
                        næringskoder = listOf(
                            Næringskode(
                                kode = "47.111",
                                beskrivelse = "Detaljhandel med bredt varesortiment uten salg av drivstoff"
                            )
                        ),
                        gateadresse = "Gate 1",
                        postnummer = "2345",
                        poststed = "OSLO"
                    )
                ))
            )
        }),
        HentArbeidsgivere({"http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/arbeidsgiver"}, {
            HttpRequest.newBuilder().GET()
        }),
        HentArbeidsgiverHendelser({"http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/arbeidsgiver/hendelser"},
            {
                HttpRequest.newBuilder().GET()
        }),
        SlettArbeidsgiver({"http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/arbeidsgiver/${arbeidsgiverId.somString}"}, {
            HttpRequest.newBuilder().DELETE()
        })
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler)),
        Jobbsøkerrettet(listOf(jobbsøkerrettet))
    }

    private fun autorisasjonsCases() = listOf(
        Arguments.of(Endepunkt.LeggTilArbeidsgiver, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.LeggTilArbeidsgiver, Gruppe.Utvikler, HTTP_CREATED),
        Arguments.of(Endepunkt.LeggTilArbeidsgiver, Gruppe.Arbeidsgiverrettet, HTTP_CREATED),
        Arguments.of(Endepunkt.LeggTilArbeidsgiver, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.HentArbeidsgivere, Gruppe.Jobbsøkerrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentArbeidsgivere, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentArbeidsgivere, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentArbeidsgivere, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.HentArbeidsgiverHendelser, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.HentArbeidsgiverHendelser, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.HentArbeidsgiverHendelser, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.HentArbeidsgiverHendelser, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.SlettArbeidsgiver, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettArbeidsgiver, Gruppe.Utvikler, HTTP_NO_CONTENT),
        Arguments.of(Endepunkt.SlettArbeidsgiver, Gruppe.Arbeidsgiverrettet, HTTP_NO_CONTENT),
        Arguments.of(Endepunkt.SlettArbeidsgiver, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
    ).stream()


    @ParameterizedTest
    @MethodSource("autorisasjonsCases")
    fun testEndepunkt(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, expectedStatus: Int) {
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
}