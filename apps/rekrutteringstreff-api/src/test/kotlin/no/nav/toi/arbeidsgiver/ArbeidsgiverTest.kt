package no.nav.toi.rekrutteringstreff.arbeidsgiver

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.ArbeidsgiverOutboundDto
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArbeidsgiverTest {

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
            db.dataSource
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
    fun autentiseringLeggTilArbeidsgiver(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val anyTreffId = "anyTreffID"
        val (_, response, result) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/arbeidsgiver")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, response, result)
    }


    @Test
    fun leggTilArbeidsgiver() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val orgnr = Orgnr("555555555")
        val orgnavn = Orgnavn("Oooorgnavn")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val requestBody = """
            {
              "orgnr" : "$orgnr",
              "orgnavn" : "$orgnavn"
            }
            """.trimIndent()
        assertThat(db.hentAlleArbeidsgviere()).isEmpty()

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/arbeidsgiver")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_CREATED, response, result)
        val actualArbeidsgivere = db.hentAlleArbeidsgviere()
        assertThat(actualArbeidsgivere.size).isEqualTo(1)
        actualArbeidsgivere.first().also { actual: Arbeidsgiver ->
            assertThat(actual.treffId).isEqualTo(treffId)
            assertThat(actual.orgnr).isEqualTo(orgnr)
            assertThat(actual.orgnavn).isEqualTo(orgnavn)
        }
    }

    // TODO testcase: Hva skal skje når treffet ikke finnes?

    @Test
    fun hentArbeidsgivere() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")

        val treffId1 = db.opprettRekrutteringstreffIDatabase()
        val treffId2 = db.opprettRekrutteringstreffIDatabase()
        val treffId3 = db.opprettRekrutteringstreffIDatabase()
        val orgnr1 = Orgnr("111111111")
        val orgnr2 = Orgnr("222222222")
        val orgnr3 = Orgnr("333333333")
        val orgnr4 = Orgnr("444444444")
        val orgnavn1 = Orgnavn("Orgnavn1")
        val orgnavn2 = Orgnavn("Orgnavn2")
        val orgnavn3 = Orgnavn("Orgnavn3")
        val orgnavn4 = Orgnavn("Orgnavn4")
        val arbeidsgivere1: List<Arbeidsgiver> = listOf(
            Arbeidsgiver(treffId1, orgnr1, orgnavn1)
        )
        val arbeidsgivere2: List<Arbeidsgiver> = listOf(
            Arbeidsgiver(treffId2, orgnr2, orgnavn2),
            Arbeidsgiver(treffId2, orgnr3, orgnavn3)
        )
        val arbeidsgivere3: List<Arbeidsgiver> = listOf(
            Arbeidsgiver(treffId3, orgnr4, orgnavn4),
        )
        db.leggTilArbeidsgivere(arbeidsgivere1)
        db.leggTilArbeidsgivere(arbeidsgivere2)
        db.leggTilArbeidsgivere(arbeidsgivere3)

        assertThat(db.hentAlleRekrutteringstreff().size).isEqualTo(3)
        assertThat(db.hentAlleArbeidsgviere().size).isEqualTo(4)

        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/$treffId2/arbeidsgiver")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<ArbeidsgiverOutboundDto>> {
                override fun deserialize(content: String): List<ArbeidsgiverOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(
                        List::class.java,
                        ArbeidsgiverOutboundDto::class.java
                    )
                    return mapper.readValue(content, type)
                }
            })

        assertStatuscodeEquals(HTTP_OK, response, result)
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                val actualArbeidsgivere: List<ArbeidsgiverOutboundDto> = result.value
                assertThat(actualArbeidsgivere.size).isEqualTo(2)
                assertThat(actualArbeidsgivere).contains(
                    ArbeidsgiverOutboundDto(orgnr3.asString, orgnavn3.asString),
                    ArbeidsgiverOutboundDto(orgnr2.asString, orgnavn2.asString),
                )
            }
        }
    }

}
