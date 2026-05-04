package no.nav.toi.rekrutteringstreff

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.App
import no.nav.toi.JacksonConfig
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.config.testKoinApplication
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import no.nav.toi.lagToken
import no.nav.toi.lagTokenBorger
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.httpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.KoinApplication
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PilotkontorTest {
    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
    }

    private val modiaKlient = mockk<ModiaKlient>()

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val repo = RekrutteringstreffRepository(database.dataSource)
    private val jobbsøkerRepository = JobbsøkerRepository(database.dataSource, JacksonConfig.mapper)
    private val arbeidsgiverRepository = ArbeidsgiverRepository(database.dataSource, JacksonConfig.mapper)
    private val jobbsøkerService = JobbsøkerService(database.dataSource, jobbsøkerRepository, JobbsøkerSokRepository(database.dataSource))
    private val service = RekrutteringstreffService(database.dataSource, repo, jobbsøkerRepository, arbeidsgiverRepository, jobbsøkerService, EierService(EierRepository(database.dataSource), repo, database.dataSource))

    private lateinit var koinApp: KoinApplication
    private lateinit var app: App

    @BeforeAll
    fun setUp() {
        authServer.start(port = authPort)
        koinApp = testKoinApplication(
            dataSource = database.dataSource,
            authServer = authServer,
            port = appPort,
            authPort = authPort,
            modiaKlientOverride = modiaKlient,
        )
        app = App(koinApp.koin)
        app.start()
    }

    @BeforeEach
    fun setup() {
        gyldigRekrutteringstreff = service.opprett(OpprettRekrutteringstreffInternalDto("Tittel", "A213456", "Kontor", ZonedDateTime.now()))
        clearMocks(modiaKlient)
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @Test
    fun `Person med innlogget kontor som er et pilotkontor får lov til å kalle rekrutteringstreff-endepunkter`() {
        every { modiaKlient.hentVeiledersAktivEnhet(any())} returns "1234"

        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$appPort/api/rekrutteringstreff/sok"))
            .header("Authorization", "Bearer ${authServer.lagToken(authPort, groups = listOf(arbeidsgiverrettet)).serialize()}")
            .GET().build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        verify(exactly = 1) { modiaKlient.hentVeiledersAktivEnhet(any()) }

    }

    @Test
    fun `Person uten innlogget pilotkontor får ikke lov til å kalle rekrutteringstreff-endepunkter`() {
        every { modiaKlient.hentVeiledersAktivEnhet(any()) } returns "5678"

        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$appPort/api/rekrutteringstreff/sok"))
            .header("Authorization", "Bearer ${authServer.lagToken(authPort, groups = listOf(arbeidsgiverrettet)).serialize()}")
            .GET().build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(403)

        verify(exactly = 1) { modiaKlient.hentVeiledersAktivEnhet(any()) }
    }

    @Test
    fun `ModiaKlient blir bare kalt når token er et veileder-token`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$appPort/api/rekrutteringstreff/$gyldigRekrutteringstreff"))
            .header("Authorization", "Bearer ${authServer.lagTokenBorger(authPort = authPort).serialize()}")
            .GET().build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        verify(exactly = 0) { modiaKlient.hentVeiledersAktivEnhet(any()) }
    }
}
