package no.nav.toi.arbeidsgiver

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.JacksonConfig
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

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
            db.dataSource,
            arbeidsgiverrettet,
            utvikler
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
        val leggPåToken = autentiseringstest.leggPåToken  // Brukes til å sette token
        val anyTreffId = "anyTreffID"
        val (_, response, result) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/arbeidsgiver")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringHentArbeidsgivere(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val anyTreffId = "anyTreffID"
        val (_, response, result) = Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/arbeidsgiver")
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
              "organisasjonsnummer": "$orgnr",
              "navn": "$orgnavn"
            }
        """.trimIndent()
        assertThat(db.hentAlleArbeidsgivere()).isEmpty()

        val (_, response, result) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/arbeidsgiver")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_CREATED, response, result)

        val actualArbeidsgivere = db.hentAlleArbeidsgivere()
        assertThat(actualArbeidsgivere.size).isEqualTo(1)
        val ag = actualArbeidsgivere.first()
        assertThat(ag.treffId).isEqualTo(treffId)
        assertThat(ag.orgnr).isEqualTo(orgnr)
        assertThat(ag.orgnavn).isEqualTo(orgnavn)

        val hendelser = db.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser).hasSize(1)
        val h = hendelser.first()
        assertThat(h.hendelsestype.toString()).isEqualTo(Hendelsestype.LEGG_TIL.toString())
        assertThat(h.opprettetAvAktørType.toString()).isEqualTo(AktørType.ARRANGØR.toString())
        assertThat(h.aktøridentifikasjon).isEqualTo("A123456")
    }

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

        val arbeidsgivere1 = listOf(
            Arbeidsgiver(treffId1, orgnr1, orgnavn1)
        )
        val arbeidsgivere2 = listOf(
            Arbeidsgiver(treffId2, orgnr2, orgnavn2),
            Arbeidsgiver(treffId2, orgnr3, orgnavn3)
        )
        val arbeidsgivere3 = listOf(
            Arbeidsgiver(treffId3, orgnr4, orgnavn4)
        )
        db.leggTilArbeidsgivere(arbeidsgivere1)
        db.leggTilArbeidsgivere(arbeidsgivere2)
        db.leggTilArbeidsgivere(arbeidsgivere3)

        assertThat(db.hentAlleRekrutteringstreff().size).isEqualTo(3)
        assertThat(db.hentAlleArbeidsgivere().size).isEqualTo(4)

        val (_, response, result) = Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/${treffId2.somUuid}/arbeidsgiver")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<ArbeidsgiverOutboundDto>> {
                override fun deserialize(content: String): List<ArbeidsgiverOutboundDto> {
                    val mapper = JacksonConfig.mapper
                    val type = mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverOutboundDto::class.java)
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
                    ArbeidsgiverOutboundDto(orgnr2.asString, orgnavn2.asString)
                )
            }
        }
    }

    @Test
    fun hentArbeidsgiverHendelser() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456", tittel = "TestTreffHendelser")
        val requestBody = """
            {
              "organisasjonsnummer": "777777777",
              "navn": "HendelsesFirma"
            }
        """.trimIndent()
        val (_, postResponse, postResult) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        assertStatuscodeEquals(HTTP_CREATED, postResponse, postResult)

        val (_, response, result) = Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/hendelser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto>> {
                override fun deserialize(content: String): List<ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto> {
                    val type = JacksonConfig.mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto::class.java)
                    return JacksonConfig.mapper.readValue(content, type)
                }
            })
        assertStatuscodeEquals(HTTP_OK, response, result)
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                val hendelser = result.value
                assertThat(hendelser).hasSize(1)
                val hendelse = hendelser.first()
                assertThat(hendelse.hendelsestype).isEqualTo(Hendelsestype.LEGG_TIL.toString())
                assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.toString())
                assertThat(hendelse.aktøridentifikasjon).isEqualTo("A123456")
                assertThat(hendelse.orgnr).isEqualTo("777777777")
                assertThat(hendelse.orgnavn).isEqualTo("HendelsesFirma")
            }
        }
    }
}
