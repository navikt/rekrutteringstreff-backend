package no.nav.toi.jobbsoker

import com.github.kittinunf.fuel.Fuel
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import java.net.HttpURLConnection.*
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tester for feilhåndtering ved invitasjon av jobbsøkere.
 * 
 * Dekker:
 * - TRELLO-2: Dobbel invitasjon-beskyttelse (race condition)
 * - Invitasjon av jobbsøker som nettopp ble ikke-synlig
 * 
 * MERK: Noen tester er markert som @Disabled fordi de tester funksjonalitet som 
 * ennå ikke er implementert. Disse testene dokumenterer forventet oppførsel.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class InvitasjonFeilhåndteringTest {

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18016
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()

        private lateinit var app: App

        val mapper = JacksonConfig.mapper
    }

    private val eierRepository = EierRepository(db.dataSource)

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        val accessTokenClient = AccessTokenClient(
            clientId = "clientId",
            secret = "clientSecret",
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
            dataSource = db.dataSource,
            jobbsøkerrettet = jobbsøkerrettet,
            arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
            utvikler = AzureAdRoller.utvikler,
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
        authServer.start(port = authPort)
    }

    @BeforeEach
    fun setupStubs() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{ "aktivEnhet": "1234" }""")
                )
        )
    }

    @AfterAll
    fun tearDown() {
        authServer.shutdown()
        app.close()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    /**
     * TRELLO-2: Test 5.4.1 - To samtidige invitasjoner registrerer kun én invitasjon (idempotent)
     * 
     * Verifiserer at systemet håndterer race conditions ved samtidige invitasjoner.
     * Kun én INVITERT-hendelse skal registreres selv om to kall kommer samtidig.
     * 
     * MERK: Denne testen er @Disabled fordi idempotens for invitasjon ikke er implementert.
     * Fjern @Disabled når funksjonaliteten er på plass.
     */
    @Test
    @Disabled("Idempotens for invitasjon er ikke implementert ennå - fjern når funksjonalitet er på plass")
    fun `samtidige invitasjoner registrerer kun én INVITERT-hendelse`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("12345678901")

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    PersonTreffId(UUID.randomUUID()),
                    treffId,
                    fnr,
                    Fornavn("Test"),
                    Etternavn("Person"),
                    null, null, null,
                    JobbsøkerStatus.LAGT_TIL
                )
            )
        )

        val jobbsøkere = db.hentAlleJobbsøkere()
        val personTreffId = jobbsøkere.first().personTreffId
        eierRepository.leggTil(treffId, listOf("A123456"))

        val requestBody = """{ "personTreffIder": ["$personTreffId"] }"""

        // Start samtidige invitasjons-kall
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val successfulResponses = AtomicInteger(0)

        repeat(2) {
            executor.submit {
                try {
                    val (_, response, _) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
                        .body(requestBody)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer ${token.serialize()}")
                        .responseString()

                    if (response.statusCode == HTTP_OK) {
                        successfulResponses.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Verifiser at kun én INVITERT-hendelse ble registrert
        val hendelser = db.hentJobbsøkerHendelser(treffId)
        val invitasjonsHendelser = hendelser.filter { it.hendelsestype == JobbsøkerHendelsestype.INVITERT }

        assertThat(invitasjonsHendelser).hasSize(1)
        assertThat(invitasjonsHendelser.first().aktørIdentifikasjon).isEqualTo("A123456")
    }

    /**
     * TRELLO-2: Test 5.4.2 - Invitasjon av jobbsøker som nettopp ble ikke-synlig
     * 
     * Verifiserer at systemet håndterer forsøk på å invitere en jobbsøker som 
     * har blitt ikke-synlig (CV ikke lenger delt).
     */
    @Test
    fun `invitasjon av ikke-synlig jobbsøker håndteres korrekt`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("12345678901")
        val personTreffId = PersonTreffId(UUID.randomUUID())

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    personTreffId,
                    treffId,
                    fnr,
                    Fornavn("Test"),
                    Etternavn("Person"),
                    null, null, null,
                    JobbsøkerStatus.LAGT_TIL
                )
            )
        )

        // Sett jobbsøker til ikke-synlig (simulerer at CV ikke lenger er delt)
        db.settSynlighet(personTreffId, erSynlig = false)

        eierRepository.leggTil(treffId, listOf("A123456"))

        val requestBody = """{ "personTreffIder": ["$personTreffId"] }"""

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        // Systemet skal enten:
        // 1. Returnere feilmelding (400/409) hvis invitasjon av ikke-synlig jobbsøker ikke tillates
        // 2. Tillate invitasjon men logge warning
        // Begge er gyldige implementasjoner
        
        // For nå verifiserer vi at kallet ikke feiler med 500
        assertThat(response.statusCode).isNotEqualTo(HTTP_INTERNAL_ERROR)
        
        // Sjekk at vi har en passende respons
        assertThat(response.statusCode).isIn(HTTP_OK, HTTP_BAD_REQUEST, HTTP_CONFLICT)
    }

    /**
     * Test at re-invitasjon av allerede invitert jobbsøker håndteres idempotent.
     * 
     * MERK: Denne testen er @Disabled fordi idempotens for invitasjon ikke er implementert.
     * Fjern @Disabled når funksjonaliteten er på plass.
     */
    @Test
    @Disabled("Idempotens for re-invitasjon er ikke implementert ennå - fjern når funksjonalitet er på plass")
    fun `re-invitasjon av allerede invitert jobbsøker håndteres idempotent`() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("12345678901")
        val personTreffId = PersonTreffId(UUID.randomUUID())

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    personTreffId,
                    treffId,
                    fnr,
                    Fornavn("Test"),
                    Etternavn("Person"),
                    null, null, null,
                    JobbsøkerStatus.LAGT_TIL
                )
            )
        )

        eierRepository.leggTil(treffId, listOf("A123456"))

        val requestBody = """{ "personTreffIder": ["$personTreffId"] }"""

        // Første invitasjon
        val (_, response1, _) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        assertThat(response1.statusCode).isEqualTo(HTTP_OK)

        // Verifiser at det finnes én INVITERT-hendelse
        val hendelserEtterFørste = db.hentJobbsøkerHendelser(treffId)
        val invitasjonsHendelserFørste = hendelserEtterFørste.filter { it.hendelsestype == JobbsøkerHendelsestype.INVITERT }
        assertThat(invitasjonsHendelserFørste).hasSize(1)

        // Andre invitasjon (re-invitasjon)
        val (_, response2, _) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        // Re-invitasjon bør ikke feile
        assertThat(response2.statusCode).isNotEqualTo(HTTP_INTERNAL_ERROR)

        // Verifiser at antall INVITERT-hendelser ikke økte (idempotent)
        val hendelserEtterAndre = db.hentJobbsøkerHendelser(treffId)
        val invitasjonsHendelserAndre = hendelserEtterAndre.filter { it.hendelsestype == JobbsøkerHendelsestype.INVITERT }
        
        // Idempotent: Antall INVITERT-hendelser bør fortsatt være 1
        assertThat(invitasjonsHendelserAndre).hasSize(1)
    }
}
