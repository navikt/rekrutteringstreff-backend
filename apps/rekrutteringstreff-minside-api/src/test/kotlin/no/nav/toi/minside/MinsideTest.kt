package no.nav.toi.minside

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import java.util.concurrent.atomic.AtomicInteger
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.TestRapid
import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
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
import java.net.http.HttpClient
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
            utvikler = UUID.randomUUID(),
            azureTokenEndpoint = "",
            azureClientId = "",
            azureClientSecret = "",
            kandidatsokApiUrl = "",
            kandidatsokScope = "",
            rapidsConnection = TestRapid(),
            httpClient = HttpClient.newBuilder().build()
        )
        private val httpClient: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .version(HttpClient.Version.HTTP_1_1)
            .build()
        private val appPort = ubruktPortnr()
        private val app = App(
            port = appPort,
            "http://localhost:$rekrutteringsTreffApiPort",
            rekrutteringstreffAudience = rekrutteringsTreffAudience,
            TokenXKlient(
                "clientId",
                "{\n" +
                        "          \"kty\":\"RSA\",\n" +
                        "          \"alg\":\"RS256\",\n" +
                        "          \"use\":\"sig\",\n" +
                        "          \"p\":\"_xCPvqs85ZZVg460Qfot26rQoNRPTOVDo5p4nqH3ep6BK_5TvoU5LFXd26W-1V1Lc5fcvvftClPOT201xgat4DVtliNtoc8od_tWr190A3AzbsAVFOx0nKa5uhLBxP9SsPM84llp6PXF6QTMGFiPYuoLDaQQqL1K4BbHq3ZzF2M\",\n" +
                        "          \"q\":\"7QLqW75zkfSDrn5rMoF50WXyB_ysNx6-2SvaXKGXaOn80IR7QW5vwkleJnsdz_1kr04rJws2p4HBJjUFfSJDi1Dapj7tbIwb0a1szDs6Y2fAa3DlzgXZCkoE2TIrW6UITgs14pI_a7RasclE71FpoZ78XNBvj3NmZugkNLBvRjs\",\n" +
                        "          \"d\":\"f7aT4poed8uKdcSD95mvbfBdb6X-M86d99su0c390d6gWwYudeilDugH9PMwqUeUhY0tdaRVXr6rDDIKLSE-uEyaYKaramev0cG-J_QWYJU2Lx-4vDGNHAE7gC99o1Ee_LXqMDCBawMYyVcSWx7PxGQfzhSsARsAIbkarO1sg9zsqPS4exSMbK8wyCTPgRbnkB32_UdZSGbdSib1jSYyyoAItZ8oZHiltVsZIlA97kS4AGPtozde043NC7Ik0uEzgB5qJ_tR7vW8MfDrBj6da2NrLh0UH-q28dooBO1vEu0rvKZIescXYk9lk1ZakHhhpZaLykDOGzxCpronzP3_kQ\",\n" +
                        "          \"e\":\"AQAB\",\n" +
                        "          \"qi\":\"9kMIR6pEoiwN3M6O0n8bnh6c3KbLMoQQ1j8_Zyir7ZIlmRpWYl6HtK0VnD88zUuNKTrQa7-jfE5uAUa0PubzfRqybACb4S3HIAuSQP00_yCPzCSRrbpGRDFqq-8eWVwI9VdiN4oqkaaWcL1pd54IDcHIbfk-ZtNtZgsOlodeRMo\",\n" +
                        "          \"dp\":\"VUecSAvI2JpjDRFxg326R2_dQWi6-uLMsq67FY7hx8WnOqZWKaUxcHllLENGguAmkgd8bv1F6-YJXNUO3Z7uE8DJWyGNTkSNK1CFsy0fBOdGywi-A7jrZFT6VBRhZRRY-YDaInPyzUkfWsGX26wAhPnrqCvqxgBEQJhdOh7obDE\",\n" +
                        "          \"dq\":\"7EUfw92T8EhEjUrRKkQQYEK0iGnGdBxePLiOshEUky3PLT8kcBHbr17cUJgjHBiKqofOVNnE3i9nkOMCWcAyfUtY7KmGndL-WIP-FYplpnrjQzgEnuENgEhRlQOCXZWjNcnPKdKJDqF4WAtAgSIznz6SbSQMUoDD8IoyraPFCck\",\n" +
                        "          \"n\":\"7CU8tTANiN6W_fD9SP1dK2vQvCkf7-nwvBYe5CfANV0_Bb0ZmQb77FVVsl1beJ7EYLz3cJmL8Is1RCHKUK_4ydqihNjEWTyZiQoj1i67pkqk_zRvfQa9raZR4uZbuBxx7dWUoPC6fFH2F_psAlHW0zf90fsLvhB6Aqq3uvO7XXqo8qNl9d_JSG0Rg_2QUYVb0WKmPVbbhgwtkFu0Tyuev-VZ9IzTbbr5wmZwEUVY7YAi73pDJkcZt5r2WjOF_cuIXe-O2vwbOrRgmJfHO9--mVLdATnEyrb6q2oy_75h6JjP-R4-TD1hyoFFoE2gmj-kSS6Z_Gggljs3Aw7--Nh10Q\"\n" +
                        "        }",
                "http://localhost:$authPort/default/token",
                issuer = "http://localhost:$authPort/default",
                httpClient = httpClient

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
                    personTreffId = PersonTreffId(UUID.randomUUID()),
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
                        arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
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

    @Test
    fun `hent svarstatus for jobbsøker på ett treff`() {
        val ident = "12345678910"
        val token = authServer.lagToken(authPort, pid = ident)

        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${rekrutteringstreffMeldtPå.id}/svar")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readTree(result.get())

                // The answer is randomly generated boolean values for now
                assertThat(dto["erInvitert"].asText()).isIn("true", "false")
                assertThat(dto["erPåmeldt"].asText()).isIn("true", "false")
                assertThat(dto["harSvart"].asText()).isIn("true", "false")
            }
        }
    }

    @Test
    fun `avgi svarstatus for jobbsøker på ett treff`() {
        val ident = "12345678910"
        val token = authServer.lagToken(authPort, pid = ident)

        val (_, response, result) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${rekrutteringstreffMeldtPå.id}/svar")
            .header("Authorization", "Bearer ${token.serialize()}")
            .body("""
                {
                    "erPåmeldt": true
                }
                """.trimIndent()
            )
            .responseString()
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readTree(result.get())

                assertThat(dto["rekrutteringstreffId"].asText()).isEqualTo(rekrutteringstreffMeldtPå.id.toString())
                assertThat(dto["erPåmeldt"].asText()).isEqualTo("true")
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
