package no.nav.toi.jobbsoker

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import no.nav.toi.*
import org.junit.jupiter.api.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobbsøkerTest {

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18012
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()

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
            arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
            utvikler = AzureAdRoller.utvikler
        )

        val mapper = JacksonConfig.mapper
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
        val anyTreffId = "anyTreffID"
        val leggPåToken = autentiseringstest.leggPåToken
        val (_, response, result) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/jobbsoker")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, response, result)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringHentJobbsøkerr(autentiseringstest: UautentifiserendeTestCase) {
        val anyTreffId = "anyTreffID"
        val leggPåToken = autentiseringstest.leggPåToken
        val (_, response, result) = Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/$anyTreffId/jobbsoker")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, response, result)
    }

    @Test
    fun leggTilJobbsøkerTest() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val fnr = Fødselsnummer("55555555555")
        val kandidatnr = Kandidatnummer("Kaaaaaaandidatnummer")
        val fornavn = Fornavn("Foooornavn")
        val etternavn = Etternavn("Eeeetternavn")
        val navkontor = Navkontor("Oslo")
        val veilederNavn = VeilederNavn("Test Veileder")
        val veilederNavIdent = VeilederNavIdent("NAV001")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val requestBody = """
            {
              "fødselsnummer" : "${fnr}",
              "kandidatnummer" : "${kandidatnr}",
              "fornavn" : "${fornavn}",
              "etternavn" : "${etternavn}",
              "navkontor" : "${navkontor}",
              "veilederNavn" : "${veilederNavn}",
              "veilederNavIdent" : "${veilederNavIdent}"
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
        actualJobbsøkere.first().also { actual ->
            assertThat(actual.treffId).isEqualTo(treffId)
            assertThat(actual.fødselsnummer).isEqualTo(fnr)
            assertThat(actual.kandidatnummer).isEqualTo(kandidatnr)
            assertThat(actual.fornavn).isEqualTo(fornavn)
            assertThat(actual.etternavn).isEqualTo(etternavn)
            assertThat(actual.navkontor).isEqualTo(navkontor)
            assertThat(actual.veilederNavn).isEqualTo(veilederNavn)
            assertThat(actual.veilederNavIdent).isEqualTo(veilederNavIdent)
        }
    }

    @Test
    fun hentJobbsøkereTest() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId1 = db.opprettRekrutteringstreffIDatabase()
        val treffId2 = db.opprettRekrutteringstreffIDatabase()
        val treffId3 = db.opprettRekrutteringstreffIDatabase()
        val fnr1 = Fødselsnummer("11111111111")
        val fnr2 = Fødselsnummer("22222222222")
        val fnr3 = Fødselsnummer("33333333333")
        val fnr4 = Fødselsnummer("44444444444")
        val kandidatnr1 = Kandidatnummer("Kandidatnr1")
        val kandidatnr2 = Kandidatnummer("Kandidatnr2")
        val kandidatnr3 = Kandidatnummer("Kandidatnr3")
        val fornavn1 = Fornavn("Fornavn1")
        val fornavn2 = Fornavn("Fornavn2")
        val fornavn3 = Fornavn("Fornavn3")
        val fornavn4 = Fornavn("Fornavn4")
        val etternavn1 = Etternavn("Etternavn1")
        val etternavn2 = Etternavn("Etternavn2")
        val etternavn3 = Etternavn("Etternavn3")
        val etternavn4 = Etternavn("Etternavn4")
        val navkontor1 = Navkontor("Oslo")
        val navkontor2 = Navkontor("Bergen")
        val navkontor3 = Navkontor("Trondheim")
        val veilederNavn1 = VeilederNavn("Veileder1")
        val veilederNavn2 = VeilederNavn("Veileder2")
        val veilederNavn3 = VeilederNavn("Veileder3")
        val veilederNavIdent1 = VeilederNavIdent("NAV001")
        val veilederNavIdent2 = VeilederNavIdent("NAV002")
        val veilederNavIdent3 = VeilederNavIdent("NAV003")
        val jobbsøkere1 = listOf(
            Jobbsøker(treffId1, fnr1, kandidatnr1, fornavn1, etternavn1, navkontor1, veilederNavn1, veilederNavIdent1)
        )
        val jobbsøkere2 = listOf(
            Jobbsøker(treffId2, fnr2, kandidatnr2, fornavn2, etternavn2, navkontor1, veilederNavn1, veilederNavIdent1),
            Jobbsøker(treffId2, fnr3, kandidatnr3, fornavn3, etternavn3, navkontor2, veilederNavn2, veilederNavIdent2)
        )
        val jobbsøkere3 = listOf(
            Jobbsøker(treffId3, fnr4, kandidatnr1, fornavn4, etternavn4, navkontor3, veilederNavn3, veilederNavIdent3)
        )
        db.leggTilJobbsøkere(jobbsøkere1)
        db.leggTilJobbsøkere(jobbsøkere2)
        db.leggTilJobbsøkere(jobbsøkere3)
        assertThat(db.hentAlleRekrutteringstreff().size).isEqualTo(3)
        assertThat(db.hentAlleJobbsøkere().size).isEqualTo(4)
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treffId2.somUuid}/jobbsoker")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<JobbsøkerOutboundDto>> {
                override fun deserialize(content: String): List<JobbsøkerOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(List::class.java, JobbsøkerOutboundDto::class.java)
                    return mapper.readValue(content, type)
                }
            })
        assertStatuscodeEquals(HTTP_OK, response, result)
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                val actualJobbsøkere = result.value
                assertThat(actualJobbsøkere.size).isEqualTo(2)
                actualJobbsøkere.forEach { jobbsøker ->
                    assertThat(jobbsøker.hendelser.size).isEqualTo(1)
                    val hendelse = jobbsøker.hendelser.first()
                    assertThatCode { UUID.fromString(hendelse.id) }
                        .doesNotThrowAnyException()
                    assertThat(hendelse.tidspunkt).isNotNull()
                    assertThat(hendelse.tidspunkt.toInstant())
                        .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
                    assertThat(hendelse.hendelsestype).isEqualTo(Hendelsestype.LEGG_TIL.toString())
                    assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.toString())
                    assertThat(hendelse.aktørIdentifikasjon).isEqualTo("testperson")
                }
            }
        }
    }

    @Test
    fun hentJobbsøkerHendelserTest() {
        val token = authServer.lagToken(authPort, navIdent = "testperson")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val requestBody = """
        {
          "fødselsnummer" : "77777777777",
          "kandidatnummer" : "K777777",
          "fornavn" : "Test",
          "etternavn" : "Bruker",
          "navkontor" : "Oslo",
          "veilederNavn" : "Test Veileder",
          "veilederNavIdent" : "NAV007"
        }
    """.trimIndent()
        val (_, postResponse, postResult) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        assertStatuscodeEquals(HTTP_CREATED, postResponse, postResult)
        val (_, getResponse, getResult) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<JobbsøkerOutboundDto>> {
                override fun deserialize(content: String): List<JobbsøkerOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(List::class.java, JobbsøkerOutboundDto::class.java)
                    return mapper.readValue(content, type)
                }
            })
        assertStatuscodeEquals(HTTP_OK, getResponse, getResult)
        when (getResult) {
            is Failure -> throw getResult.error
            is Success -> {
                val actualJobbsøkere = getResult.value
                assertThat(actualJobbsøkere.size).isEqualTo(1)
                val jobbsoeker = actualJobbsøkere.first()
                assertThat(jobbsoeker.hendelser.size).isEqualTo(1)
                val hendelse = jobbsoeker.hendelser.first()
                assertThat(hendelse.hendelsestype).isEqualTo(Hendelsestype.LEGG_TIL.toString())
                assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.toString())
                assertThat(hendelse.aktørIdentifikasjon).isEqualTo("testperson")
                assertThatCode { UUID.fromString(hendelse.id) }
                    .doesNotThrowAnyException()
                assertThat(hendelse.tidspunkt.toInstant())
                    .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
            }
        }
    }

    @Test
    fun hentAlleJobbsøkereHendelserTest() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val input1 = LeggTilJobbsøker(
            Fødselsnummer("11111111111"),
            Kandidatnummer("K111"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            Navkontor("NAV Oslo"),
            VeilederNavn("Veileder1"),
            VeilederNavIdent("NAV111")
        )
        val input2 = LeggTilJobbsøker(
            Fødselsnummer("22222222222"),
            Kandidatnummer("K222"),
            Fornavn("Kari"),
            Etternavn("Nordmann"),
            Navkontor("NAV Bergen"),
            VeilederNavn("Veileder2"),
            VeilederNavIdent("NAV222")
        )
        db.leggTilJobbsøkere(listOf(
            Jobbsøker(
                treffId,
                input1.fødselsnummer,
                input1.kandidatnummer,
                input1.fornavn,
                input1.etternavn,
                input1.navkontor,
                input1.veilederNavn,
                input1.veilederNavIdent
            )
        ))
        Thread.sleep(1000)
        db.leggTilJobbsøkere(listOf(
            Jobbsøker(
                treffId,
                input2.fødselsnummer,
                input2.kandidatnummer,
                input2.fornavn,
                input2.etternavn,
                input2.navkontor,
                input2.veilederNavn,
                input2.veilederNavIdent
            )
        ))
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/hendelser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<JobbsøkerHendelseOutboundDto>> {
                override fun deserialize(content: String): List<JobbsøkerHendelseOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(List::class.java, JobbsøkerHendelseOutboundDto::class.java)
                    return mapper.readValue(content, type)
                }
            })
        assertThat(response.statusCode).isEqualTo(200)
        when (result) {
            is Failure -> throw result.error
            is Success -> {
                val hendelser = result.value
                assertThat(hendelser).hasSize(2)
                assertThat(hendelser[0].tidspunkt.toInstant()).isAfterOrEqualTo(hendelser[1].tidspunkt.toInstant())
                hendelser.forEach { h ->
                    assertThatCode { UUID.fromString(h.id) }.doesNotThrowAnyException()
                    assertThat(h.hendelsestype).isEqualTo(Hendelsestype.LEGG_TIL.toString())
                    assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.toString())
                    assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
                    assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
                }
            }
        }
    }
}
