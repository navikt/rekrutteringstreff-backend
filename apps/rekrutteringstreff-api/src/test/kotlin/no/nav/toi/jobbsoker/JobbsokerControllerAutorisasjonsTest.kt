package no.nav.toi.rekrutteringstreff.no.nav.toi.jobbsoker

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
import no.nav.toi.httpClient
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.Kandidatnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.Navkontor
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
import no.nav.toi.lagToken
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.eier.EierRepository
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
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsokerControllerAutorisasjonsTest {
    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
        private lateinit var gyldigJobbsøkerId: PersonTreffId
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val rekrutteringstreffRepository = RekrutteringstreffRepository(database.dataSource)
    private val eierRepository = EierRepository(database.dataSource)
    private val jobbsøkerRepository = JobbsøkerRepository(database.dataSource, JacksonConfig.mapper)

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
        rekrutteringstreffRepository.opprett(OpprettRekrutteringstreffInternalDto("Tittel", "A213456", "Kontor", ZonedDateTime.now()))
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id
        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer = Fødselsnummer("12345678902"),
            kandidatnummer = Kandidatnummer("K654321"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            navkontor = Navkontor("NAV Oslo"),
            veilederNavn = VeilederNavn("Espen Askeladd"),
            veilederNavIdent = VeilederNavIdent("NAV456")
        )
        jobbsøkerRepository.leggTil(
            jobbsøkere = listOf(leggTilJobbsøker),
            treff = gyldigRekrutteringstreff,
            opprettetAv = "NAV456"
        )
        gyldigJobbsøkerId = jobbsøkerRepository.hentJobbsøkere(gyldigRekrutteringstreff).first().personTreffId
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Endepunkt(
        val url: () -> String,
        val metode: () -> HttpRequest.Builder
    ) {
        leggTilJobbsøker({ "http://localhost:$appPort/api/rekrutteringstreff/$gyldigRekrutteringstreff/jobbsoker" }, {
            HttpRequest.newBuilder().POST(
                HttpRequest.BodyPublishers.ofString(
                """ 
                [
                    {
                        "fødselsnummer": "12345678901",
                        "kandidatnummer": "K123456",
                        "fornavn": "Ola",
                        "etternavn": "Nordmann",
                        "navkontor": "NAV Oslo",
                        "veilederNavn": "Kari Nordmann",
                        "veilederNavIdent": "NAV123"
                    }
                ]
                """.trimIndent())
            )
        }),
        hentJobbsøkere({"http://localhost:$appPort/api/rekrutteringstreff/$gyldigRekrutteringstreff/jobbsoker"}, {
            HttpRequest.newBuilder().GET()
        }),
        hentJobbsøkerMedHendelser({ "http://localhost:$appPort/api/rekrutteringstreff/$gyldigRekrutteringstreff/jobbsoker/hendelser"}, {
            HttpRequest.newBuilder().GET()
        }),
        inviterJobbsøker({ "http://localhost:$appPort/api/rekrutteringstreff/$gyldigRekrutteringstreff/jobbsoker/inviter"}, {
            HttpRequest.newBuilder().POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    { "personTreffIder": ["${gyldigJobbsøkerId.somString}"] }
                """.trimIndent()
                )
            )
        }),
        SlettJobbsøker(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/jobbsoker/${gyldigJobbsøkerId.somString}/slett" },
            {
                HttpRequest.newBuilder().DELETE()
            })
    }

    enum class Gruppe(val somStringListe: List<UUID>) {
        ModiaGenerell(listOf(modiaGenerell)),
        Arbeidsgiverrettet(listOf(arbeidsgiverrettet)),
        Utvikler(listOf(utvikler)),
        Jobbsøkerrettet(listOf(jobbsøkerrettet))
    }

    private fun autorisasjonsCaser() = listOf(
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.Utvikler, HTTP_CREATED),
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.Arbeidsgiverrettet, HTTP_CREATED),
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.Jobbsøkerrettet, HTTP_CREATED),
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.inviterJobbsøker, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.inviterJobbsøker, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.inviterJobbsøker, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.inviterJobbsøker, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
    ).stream()


    private fun autorisasjonsCaserMedEier() = listOf(
        Arguments.of(Endepunkt.inviterJobbsøker, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.inviterJobbsøker, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.inviterJobbsøker, Gruppe.Utvikler, erIkkeEier, HTTP_OK),

        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Utvikler, erIkkeEier, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Jobbsøkerrettet, erEier, HTTP_FORBIDDEN),


        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.Utvikler, erIkkeEier, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.Jobbsøkerrettet, erEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.hentJobbsøkere, Gruppe.ModiaGenerell, erIkkeEier, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Utvikler, erIkkeEier, HTTP_OK),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Jobbsøkerrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Jobbsøkerrettet, erEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.ModiaGenerell, erIkkeEier, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.Utvikler, erIkkeEier, HTTP_CREATED),
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.Arbeidsgiverrettet, erEier, HTTP_CREATED),
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_CREATED),
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.Jobbsøkerrettet, erIkkeEier, HTTP_CREATED),
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.Jobbsøkerrettet, erEier, HTTP_CREATED),
        Arguments.of(Endepunkt.leggTilJobbsøker, Gruppe.ModiaGenerell, erIkkeEier, HTTP_FORBIDDEN),

        ).stream()


    @ParameterizedTest
    @MethodSource("autorisasjonsCaser")
    fun testEndepunkter(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, expectedStatus: Int) {
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
    fun testEndepunkterMedEier(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, erEier: Boolean, expectedStatus: Int) {
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