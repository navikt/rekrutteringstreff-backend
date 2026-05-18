package no.nav.toi.rekrutteringstreff

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.toi.App
import no.nav.toi.ApplicationContext
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.JacksonConfig
import no.nav.toi.httpClient
import no.nav.toi.lagToken
import no.nav.toi.lagTokenBorger
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import no.nav.toi.TestInfrastructureContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PilotkontorTest {
    companion object {
        private val appPort = ubruktPortnr()
        private lateinit var gyldigRekrutteringstreff: TreffId
    }

    private val modiaKlient = mockk<ModiaKlient>()
    private val database = TestDatabase()
    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    @BeforeAll
    fun setUp() {
        infra = TestInfrastructureContext(dataSource = database.dataSource, modiaKlient = modiaKlient)
        infra.start()
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
    }

    @BeforeEach
    fun setup() {
        gyldigRekrutteringstreff = ctx.rekrutteringstreffService.opprett(OpprettRekrutteringstreffInternalDto("Tittel", "A213456", "Kontor", ZonedDateTime.now()))
        clearMocks(modiaKlient)
    }

    @AfterEach
    fun reset() {
        database.slettAlt()
    }

    @AfterAll
    fun tearDown() {
        infra.stop()
        app.close()
    }

    @Test
    fun `Person med innlogget kontor som er et pilotkontor får lov til å kalle rekrutteringstreff-endepunkter`() {
        every { modiaKlient.hentVeiledersAktivEnhet(any())} returns "1234"

        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$appPort/api/rekrutteringstreff/sok"))
            .header("Authorization", "Bearer ${infra.authServer.lagToken(infra.authPort, groups = listOf(arbeidsgiverrettet)).serialize()}")
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
            .header("Authorization", "Bearer ${infra.authServer.lagToken(infra.authPort, groups = listOf(arbeidsgiverrettet)).serialize()}")
            .GET().build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(403)

        verify(exactly = 1) { modiaKlient.hentVeiledersAktivEnhet(any()) }
    }

    @Test
    fun `ModiaKlient blir bare kalt når token er et veileder-token`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$appPort/api/rekrutteringstreff/$gyldigRekrutteringstreff"))
            .header("Authorization", "Bearer ${infra.authServer.lagTokenBorger(authPort = infra.authPort).serialize()}")
            .GET().build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        verify(exactly = 0) { modiaKlient.hentVeiledersAktivEnhet(any()) }
    }
}