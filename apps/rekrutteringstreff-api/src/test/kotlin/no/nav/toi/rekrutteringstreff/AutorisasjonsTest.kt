package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Request
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.modiaGenerell
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.*
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.*
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class AutorisasjonsTest {

    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
    }

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val jobbsøkerRepository = no.nav.toi.jobbsoker.JobbsøkerRepository(database.dataSource, JacksonConfig.mapper)
    private val repo = RekrutteringstreffRepository(database.dataSource)

    private val app = App(
        port = appPort,
        authConfigs = listOf(
            AuthenticationConfiguration(
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
                audience = "rekrutteringstreff-audience"
            )
        ),
        database.dataSource,
        arbeidsgiverrettet,
        utvikler,
        kandidatsokApiUrl = "",
        kandidatsokScope = "",
        azureClientId = "",
        azureClientSecret = "",
        azureTokenEndpoint = "",
        TestRapid(),
        httpClient = httpClient
    )

    @BeforeAll
    fun setUp() {
        authServer.start(port = authPort)
        app.start()
    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @BeforeEach
    fun setup() {
        repo.opprett(OpprettRekrutteringstreffInternalDto("Tittel", "A213456", "Kontor", ZonedDateTime.now()))
        gyldigRekrutteringstreff = database.hentAlleRekrutteringstreff()[0].id
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    enum class Endepunkt(
        val url: () -> String,
        val metode: Method,
        val requestBygger: Request.() -> Request = { this }
    ) {
        OpprettRekrutteringstreff({ "http://localhost:$appPort/api/rekrutteringstreff" }, Method.POST, {
            body(
                """
                {
                    "opprettetAvNavkontorEnhetId": "NAV-kontor"
                }
                """.trimIndent()
            )
        }),
        HentAlleRekrutteringstreff({ "http://localhost:$appPort/api/rekrutteringstreff" }, Method.GET),
        HentRekrutteringstreff(
            { "http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}" },
            Method.GET
        ),
        OppdaterRekrutteringstreff(
            { "http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}" },
            Method.PUT,
            {
                body(
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
            }),
        SlettRekrutteringstreff(
            { "http://localhost:$appPort/api/rekrutteringstreff/${gyldigRekrutteringstreff.somString}" },
            Method.DELETE
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

    @ParameterizedTest
    @MethodSource("autorisasjonsCases")
    fun testEndepunkt(endepunkt: Endepunkt, gruppetilhørighet: Gruppe, expectedStatus: Int) {
        val byggRequest = endepunkt.requestBygger
        val (_, response, result) = Fuel.request(endepunkt.metode, endepunkt.url())
            .byggRequest()
            .header(
                "Authorization",
                "Bearer ${authServer.lagToken(authPort, groups = gruppetilhørighet.somStringListe).serialize()}"
            )
            .responseString()
        assertStatuscodeEquals(expectedStatus, response, result)
    }
}
