package no.nav.toi.rekrutteringstreff.no.nav.toi.jobbsoker

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.jobbsoker.*
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
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
class JobbsokerControllerAutorisasjonsTest {
    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
        private lateinit var gyldigJobbsøkerId: PersonTreffId
    }
    private val database = TestDatabase()
    private val erEier = true
    private val erIkkeEier = false
    private val kandidatsøkKlient = mockk<KandidatsøkKlient>()

    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        every { kandidatsøkKlient.erKonfigurert() } returns true
        every { kandidatsøkKlient.hentJobbsokerInfo(any(), any()) } returns emptyMap()
        infra = TestInfrastructureContext(
            dataSource = database.dataSource,
            modiaKlientUrl = wmInfo.httpBaseUrl,
            kandidatsøkKlient = kandidatsøkKlient,
        ).also { it.start() }
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
    }

    @BeforeEach
    fun setupStubs() {
        clearMocks(kandidatsøkKlient, answers = false)
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
        gyldigRekrutteringstreff = ctx.rekrutteringstreffService.opprett(OpprettRekrutteringstreffInternalDto("Tittel",
            RekrutteringstreffKategori.REKRUTTERINGSTREFF,"A213456", "Kontor", ZonedDateTime.now()))
        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer = Fødselsnummer("12345678902"),
            fornavn = Fornavn("Kari"),
            etternavn = Etternavn("Nordmann"),
            kontor = Kontor(kontornummer = "1000", kontornavn = "NAV Oslo"),
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
                        "fornavn": "Ola",
                        "etternavn": "Nordmann",
                        "kontornavn": "NAV Oslo",
                        "veilederNavn": "Kari Nordmann",
                        "veilederNavIdent": "NAV123"
                    }
                ]
                """.trimIndent())
            )
        }),
        søkJobbsøkere({"http://localhost:$appPort/api/rekrutteringstreff/$gyldigRekrutteringstreff/jobbsoker/sok"}, {
            HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
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
            }),
        svarForJobbsøker(
            { "http://localhost:${appPort}/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}/jobbsoker/${gyldigJobbsøkerId.somString}/svar" },
            {
                HttpRequest.newBuilder().POST(
                    HttpRequest.BodyPublishers.ofString(
                        """{ "personTreffId": "${gyldigJobbsøkerId.somString}", "svar": true }"""
                    )
                )
            }
        ),
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

        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.hentJobbsøkerMedHendelser, Gruppe.ModiaGenerell, HTTP_FORBIDDEN),

        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Jobbsøkerrettet, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Endepunkt.SlettJobbsøker, Gruppe.Arbeidsgiverrettet, HTTP_OK),
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


        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.Utvikler, erIkkeEier, HTTP_OK),
        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.Jobbsøkerrettet, erEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.søkJobbsøkere, Gruppe.ModiaGenerell, erIkkeEier, HTTP_FORBIDDEN),

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

        Arguments.of(Endepunkt.svarForJobbsøker, Gruppe.Utvikler, erIkkeEier, HTTP_OK),
        Arguments.of(Endepunkt.svarForJobbsøker, Gruppe.Arbeidsgiverrettet, erEier, HTTP_OK),
        Arguments.of(Endepunkt.svarForJobbsøker, Gruppe.Arbeidsgiverrettet, erIkkeEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.svarForJobbsøker, Gruppe.Jobbsøkerrettet, erEier, HTTP_FORBIDDEN),
        Arguments.of(Endepunkt.svarForJobbsøker, Gruppe.ModiaGenerell, erIkkeEier, HTTP_FORBIDDEN),

        ).stream()

    private fun leggTilCaserPerKategori() = RekrutteringstreffKategori.entries.flatMap { kategori ->
        Gruppe.entries.flatMap { gruppe ->
            listOf(false, true).map { eier ->
                val tillatt = gruppe != Gruppe.ModiaGenerell &&
                    (kategori != RekrutteringstreffKategori.WORKOP || eier || gruppe == Gruppe.Utvikler)
                Arguments.of(kategori, gruppe, eier, if (tillatt) HTTP_CREATED else HTTP_FORBIDDEN)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("leggTilCaserPerKategori")
    fun `WorkOp krever eier eller utvikler ved tillegg og forslag mens vanlige treff beholder rollekrav`(
        kategori: RekrutteringstreffKategori,
        gruppe: Gruppe,
        eier: Boolean,
        forventetStatus: Int,
    ) {
        val ident = "SYNTETISK-MEDARBEIDER"
        val treffId = ctx.rekrutteringstreffService.opprett(
            OpprettRekrutteringstreffInternalDto(
                "Syntetisk tilgangstest", kategori, "SYNTETISK-OPPRETTER", "SYNTETISK-KONTOR", ZonedDateTime.now()
            )
        )
        if (eier) ctx.eierRepository.leggTil(treffId, ident, "SYNTETISK-KONTOR")
        val token = infra.authServer.lagToken(infra.authPort, navIdent = ident, groups = gruppe.somStringListe).serialize()
        val request = HttpRequest.newBuilder(URI("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                """[{"fødselsnummer":"00000000000","fornavn":"Syntetisk","etternavn":"Testjobbsøker"}]"""
            ))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(forventetStatus, response.statusCode(), response.body())
        io.mockk.verify(exactly = if (forventetStatus == HTTP_CREATED) 1 else 0) {
            kandidatsøkKlient.hentJobbsokerInfo(any(), any())
        }
        val jobbsøkere = ctx.jobbsøkerRepository.hentJobbsøkere(treffId)
        assertEquals(if (forventetStatus == HTTP_CREATED) 1 else 0, jobbsøkere.size)
    }


    @ParameterizedTest
    @MethodSource("autorisasjonsCaser")
    fun testEndepunkter(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, expectedStatus: Int) {
        ctx.eierRepository.leggTil(gyldigRekrutteringstreff, "A000001", "0315")

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
    fun testEndepunkterMedEier(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, erEier: Boolean, expectedStatus: Int) {
        if (erEier) {
            ctx.eierRepository.leggTil(gyldigRekrutteringstreff, "A000001", "0315")
        }

        val request = endepunkt.metode()
            .uri(URI(endepunkt.url()))
            .header(
                "Authorization",
                "Bearer ${infra.authServer.lagToken(infra.authPort, groups = gruppetilhørighet.somStringListe).serialize()}")
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(expectedStatus, response.statusCode())
    }
}
