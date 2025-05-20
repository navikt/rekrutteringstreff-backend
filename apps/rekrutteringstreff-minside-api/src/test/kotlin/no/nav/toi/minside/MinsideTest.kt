package no.nav.toi.minside

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.arbeid.cv.felles.token.AzureKlient
import java.util.concurrent.atomic.AtomicInteger
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.ArbeidsgiverOutboundDto
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.minside.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*

private const val rekrutteringsTreffAudience = "rekrutteringstreff-audience"
private const val minSideAudience = "rekrutteringstreff-minside-audience"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinsideTest {
    private val mapper = JacksonConfig.mapper

    companion object {
        private val rekrutteringsTreffApiPort = ubruktPortnr()
        private val authPort = ubruktPortnr()
        private val db = TestDatabase()
        private val rekrutteringsTreffApiApp = no.nav.toi.App(
            port = rekrutteringsTreffApiPort,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer = "http://localhost:$authPort/default",
                    jwksUri = "http://localhost:$authPort/default/jwks",
                    audience = rekrutteringsTreffAudience
                )
            ),
            dataSource = db.dataSource,
            arbeidsgiverrettet = UUID.randomUUID(),
            utvikler = UUID.randomUUID()
        )
        private val appPort = ubruktPortnr()
        private val app = App(
            port = appPort,
            "http://localhost:$rekrutteringsTreffApiPort",
            AzureKlient(
                "clientId",
                "secret",
                "http://localhost:$authPort/default/token",
                rekrutteringsTreffAudience
            ), listOf(no.nav.toi.minside.AuthenticationConfiguration(
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
                audience = minSideAudience
            ))
        )
        private val jobbsøkerFnr = "12345678901"
        private val authServer = MockOAuth2Server()
        val arrangørOrgNr = "123456789"
        val arrangørOrgnavn = "Arrangørs organisasjonsnavn"
        private val rekrutteringstreffMeldtPå = "Rekrutteringstreff meldt på".let { tittel ->
            db.apply {
                opprettRekrutteringstreffIDatabase(
                    navIdent = "navIdent",
                    tittel = tittel
                )
            }.apply {
                val treffId = hentAlleRekrutteringstreff().first { tittel == it.tittel }.id
                leggTilJobbsøkere(listOf(Jobbsøker(
                    treffId = treffId,
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
                leggTilArbeidsgivere(listOf(
                    Arbeidsgiver(
                        treffId = treffId,
                        orgnr = Orgnr(arrangørOrgNr),
                        orgnavn = Orgnavn(arrangørOrgnavn)
                    )
                ))
            }.hentAlleRekrutteringstreff().first { tittel == it.tittel }
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
        val ident = "12345678910"
        val token = authServer.lagToken(authPort, pid = ident)

        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${rekrutteringstreffMeldtPå.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readTree(result.get())
                assertThat(dto["tittel"].asText()).isEqualTo(rekrutteringstreffMeldtPå.tittel)
            }
        }
    }

    @Test
    fun `hent arbeidsgivere for treff`() {
        val ident = "12345678910"
        val token = authServer.lagToken(authPort, pid = ident)

        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${rekrutteringstreffMeldtPå.id}/arbeidsgiver")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readTree(result.get())
                assertThat(dto["organisasjonsnummer"].asText()).isEqualTo(arrangørOrgNr)
                assertThat(dto["navn"].asText()).isEqualTo(arrangørOrgnavn)
            }
        }
    }


    fun endepunkter() =
        listOf(
            Arguments.of("http://localhost:$appPort/api/rekrutteringstreff/${rekrutteringstreffMeldtPå.id}"),
            Arguments.of("http://localhost:$appPort/api/rekrutteringstreff/${rekrutteringstreffMeldtPå.id}/arbeidsgiver")
        )
    @ParameterizedTest
    @MethodSource("endepunkter")
    fun `401 uten token`(url: String) {
        val (_, response, result) = Fuel.get(url)
            .responseString()
        when (result) {
            is Failure -> assertThat(response.statusCode).isEqualTo(401)
            is Success -> fail { "Expected 401" }
        }
    }

    @ParameterizedTest
    @MethodSource("endepunkter")
    fun `401 med token uten pid`(url: String) {
        val token = authServer.lagToken(authPort, claims = mapOf())
        val (_, response, result) = Fuel.get(url)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> assertThat(response.statusCode).isEqualTo(401)
            is Success -> fail { "Expected 401" }

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
    audience: String = minSideAudience
) = issueToken(
    issuerId = issuerId,
    subject = "subject",
    claims = claims,
    expiry = expiry,
    audience = audience
)