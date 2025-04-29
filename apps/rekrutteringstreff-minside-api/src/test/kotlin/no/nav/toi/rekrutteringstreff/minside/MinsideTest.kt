package no.nav.toi.rekrutteringstreff.minside

import java.util.concurrent.atomic.AtomicInteger
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.minside.ubruktPortnrFra10000.ubruktPortnr
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinsideTest {

    companion object {
        private val appPort = ubruktPortnr()
        private val app = App(port = appPort)
        private val rekrutteringsTreffApiPort = ubruktPortnr()
        private val authPort = ubruktPortnr()
        private val db = TestDatabase()
        private val rekrutteringsTreffApiApp = no.nav.toi.App(
            port = rekrutteringsTreffApiPort,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer = "http://localhost:$authPort/default",
                    jwksUri = "http://localhost:$authPort/default/jwks",
                    audience = "rekrutteringstreff-audience"
                )
            ),
            dataSource = db.dataSource,
            arbeidsgiverrettet = UUID.randomUUID(),
            utvikler = UUID.randomUUID()
        )
        private val jobbsøkerFnr = "12345678901"
        private val authServer = MockOAuth2Server()
        private val rekrutteringstreffMeldtPå = "Rekrutteringstreff meldt på".let { tittel ->
            db.apply {
                opprettRekrutteringstreffIDatabase(
                    navIdent = "navIdent",
                    tittel = tittel
                )
            }.apply {
                leggTilJobbsøkere(listOf(Jobbsøker(
                    treffId = hentAlleRekrutteringstreff().first { tittel == it.tittel }.id,
                    fødselsnummer = Fødselsnummer(jobbsøkerFnr),
                    kandidatnummer = Kandidatnummer("123456"),
                    fornavn = Fornavn("Fornavn"),
                    etternavn = Etternavn("Etternavn"),
                    navkontor = Navkontor("NAV Kontor"),
                    veilederNavn = VeilederNavn("Veileder"),
                    veilederNavIdent = VeilederNavIdent("navIdent"),
                    hendelser = emptyList(),
                )
                ))
            }
        }
        private val rekrutteringstreffIkkeMeldtPå = "Rekrutteringstreff ikke meldt på".let { tittel ->
            db.apply {
                opprettRekrutteringstreffIDatabase(
                    navIdent = "navIdent",
                    tittel = tittel
                )
            }.hentAlleRekrutteringstreff().first { tittel == it.tittel }
        }
    }

    @BeforeAll
    fun setUp() {
        authServer.start(port = authPort)
        rekrutteringsTreffApiApp.start()
        app.start()
    }

    @AfterAll
    fun tearDown() {
        app.close()
        rekrutteringsTreffApiApp.close()
        authServer.shutdown()
    }

    @Test
    fun `hent treff`() {
        authServer.lagToken(authPort)
    }
}

object ubruktPortnrFra10000 {
    private val portnr = AtomicInteger(10000)
    fun ubruktPortnr(): Int = portnr.andIncrement
}

fun MockOAuth2Server.lagToken(
    authPort: Int,
    issuerId: String = "http://localhost:$authPort/default",
    pid: String = "11111111111",
    claims: Map<String, Any> = mapOf("pid" to pid),
    expiry: Long = 3600,
    audience: String = "rekrutteringstreff-audience"
) = issueToken(
    issuerId = issuerId,
    subject = "subject",
    claims = claims,
    expiry = expiry,
    audience = audience
)