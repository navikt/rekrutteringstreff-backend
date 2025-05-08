package no.nav.toi.rekrutteringstreff.minside

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import java.util.concurrent.atomic.AtomicInteger
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.RekrutteringstreffDetaljOutboundDto
import no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.minside.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinsideTest {
    private val mapper = JacksonConfig.mapper

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

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
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
        val ident = "12345678910"
        val token = authServer.lagToken(authPort, pid = ident)
        val originalTittel = "Spesifikk Tittel"

        db.opprettRekrutteringstreffIDatabase(
            "A123456",
            tittel = originalTittel,
        )
        val opprettetRekrutteringstreff = db.hentAlleRekrutteringstreff().first()
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readValue(result.get(), RekrutteringstreffDetaljOutboundDto::class.java)
                assertThat(dto.tittel).isEqualTo(originalTittel)
            }
        }
    }

    @Test
    fun `401 uten token`() {
        db.opprettRekrutteringstreffIDatabase(
            "A123456",
            tittel = "Spesifikk Tittel",
        )
        val opprettetRekrutteringstreff = db.hentAlleRekrutteringstreff().first()
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(401)
            }
        }
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