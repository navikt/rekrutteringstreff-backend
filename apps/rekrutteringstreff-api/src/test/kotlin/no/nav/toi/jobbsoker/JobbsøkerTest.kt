package no.nav.toi.jobbsoker

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobbsøkerTest {

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18012
        private val db = TestDatabase()
        private val appPort = ubruktPortnr()

        private val app = App(
            port = appPort,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer = "http://localhost:$authPort/default",
                    jwksUri = "http://localhost:$authPort/default/jwks",
                    audience = "rekrutteringstreff-audience"
                )
            ),
            dataSource = db.dataSource,
            arbeidsgiverrettet = arbeidsgiverrettet,
            utvikler = utvikler
        )
    }

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

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringLeggTilJobbsøker(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val anyTreffId = "anyTreffID"
        val (_, response, result) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/jobbsoker")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringHentJobbsøkerr(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val anyTreffId = "anyTreffID"
        val (_, response, result) = Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/jobbsoker")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, response, result)
    }

    @Test
    fun leggTilJobbsøker() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val fnr = Fødselsnummer("55555555555")
        val kandidatnr = Kandidatnummer("Kaaaaaaandidatnummer")
        val fornavn = Fornavn("Foooornavn")
        val etternavn = Etternavn("Eeeetternavn")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val requestBody = """
            {
              "fødselsnummer" : "$fnr",
              "kandidatnummer" : "$kandidatnr",
              "fornavn" : "$fornavn",
              "etternavn" : "$etternavn"
            }
            """.trimIndent()
        assertThat(db.hentAlleJobbsøkere()).isEmpty()

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_CREATED, response, result)
        val actualJobbsøkere = db.hentAlleJobbsøkere()
        assertThat(actualJobbsøkere.size).isEqualTo(1)
        actualJobbsøkere.first().also { actual: Jobbsøker ->
            assertThat(actual.treffId).isEqualTo(treffId)
            assertThat(actual.fødselsnummer).isEqualTo(fnr)
            assertThat(actual.kandidatnummer).isEqualTo(kandidatnr)
            assertThat(actual.fornavn).isEqualTo(fornavn)
            assertThat(actual.etternavn).isEqualTo(etternavn)
        }
    }

    // TODO testcase: Hva skal skje når treffet ikke finnes?

    @Test
    fun hentJobbsøkere() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")

        val treffId1 = db.opprettRekrutteringstreffIDatabase()
        val treffId2 = db.opprettRekrutteringstreffIDatabase()
        val treffId3 = db.opprettRekrutteringstreffIDatabase()
        val fnr1 = Fødselsnummer("11111111111")
        val fnr2 = Fødselsnummer("22222222222")
        val fnr3 = Fødselsnummer("33333333333")
        val fnr4 = Fødselsnummer("44444444444")
        val anyKandidatnr = Kandidatnummer("anyKandidatnummer")
        val forventetKandidatnr2 = Kandidatnummer("Kandidatnr2")
        val forventetKandidatnr3 = Kandidatnummer("Kandidatnr3")
        val fornavn1 = Fornavn("Fornavn1")
        val fornavn2 = Fornavn("Fornavn2")
        val fornavn3 = Fornavn("Fornavn3")
        val fornavn4 = Fornavn("Fornavn4")
        val etternavn1 = Etternavn("Etternavn1")
        val etternavn2 = Etternavn("Etternavn2")
        val etternavn3 = Etternavn("Etternavn3")
        val etternavn4 = Etternavn("Etternavn4")

        val jobbsøkere1: List<Jobbsøker> = listOf(
            Jobbsøker(treffId1, fnr1, anyKandidatnr, fornavn1, etternavn1)
        )
        val jobbsøkere2: List<Jobbsøker> = listOf(
            Jobbsøker(treffId2, fnr2, forventetKandidatnr2, fornavn2, etternavn2),
            Jobbsøker(treffId2, fnr3, forventetKandidatnr3, fornavn3, etternavn3)
        )
        val jobbsøkere3: List<Jobbsøker> = listOf(
            Jobbsøker(treffId3, fnr4, anyKandidatnr, fornavn4, etternavn4),
        )
        db.leggTilJobbsøkere(jobbsøkere1)
        db.leggTilJobbsøkere(jobbsøkere2)
        db.leggTilJobbsøkere(jobbsøkere3)

        assertThat(db.hentAlleRekrutteringstreff().size).isEqualTo(3)
        assertThat(db.hentAlleJobbsøkere().size).isEqualTo(4)

        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/$treffId2/jobbsoker")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<JobbsøkerOutboundDto>> {
                override fun deserialize(content: String): List<JobbsøkerOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(
                        List::class.java,
                        JobbsøkerOutboundDto::class.java
                    )
                    return mapper.readValue(content, type)
                }
            })

        assertStatuscodeEquals(HTTP_OK, response, result)
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                val actualJobbsøkere: List<JobbsøkerOutboundDto> = result.value
                assertThat(actualJobbsøkere.size).isEqualTo(2)
                assertThat(actualJobbsøkere).containsExactlyInAnyOrder(
                    JobbsøkerOutboundDto(
                        fnr2.asString,
                        forventetKandidatnr2.asString,
                        fornavn2.asString,
                        etternavn2.asString
                    ),
                    JobbsøkerOutboundDto(
                        fnr3.asString,
                        forventetKandidatnr3.asString,
                        fornavn3.asString,
                        etternavn3.asString
                    ),
                )
            }
        }
    }

    // TODO Are: Testcase: Når kandidatnummer ikke ikke kommer inn og når ikke finnes ved henting
}
