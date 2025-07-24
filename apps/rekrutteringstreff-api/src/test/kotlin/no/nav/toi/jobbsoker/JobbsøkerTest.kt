package no.nav.toi.jobbsoker

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.text.get


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
            [{
              "fødselsnummer" : "${fnr}",
              "kandidatnummer" : "${kandidatnr}",
              "fornavn" : "${fornavn}",
              "etternavn" : "${etternavn}",
              "navkontor" : "${navkontor}",
              "veilederNavn" : "${veilederNavn}",
              "veilederNavIdent" : "${veilederNavIdent}"
            }]
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
        val hendelser = db.hentJobbsøkerHendelser(treffId)
        assertThat(hendelser).hasSize(1)
        val h = hendelser.first()
        assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktørIdentifikasjon).isEqualTo("A123456")
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
                    val type =
                        mapper.typeFactory.constructCollectionType(List::class.java, JobbsøkerOutboundDto::class.java)
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
                    assertThat(hendelse.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT.name)
                    assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.name)
                    assertThat(hendelse.aktørIdentifikasjon).isEqualTo("testperson")
                }
            }
        }
    }

    @Test
    fun hentJobbsøkerTest() {
        val token = authServer.lagToken(authPort, navIdent = "testperson")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val requestBody = """
        [{
          "fødselsnummer" : "77777777777",
          "kandidatnummer" : "K777777",
          "fornavn" : "Test",
          "etternavn" : "Bruker",
          "navkontor" : "Oslo",
          "veilederNavn" : "Test Veileder",
          "veilederNavIdent" : "NAV007"
        }]
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
                    val type =
                        mapper.typeFactory.constructCollectionType(List::class.java, JobbsøkerOutboundDto::class.java)
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
                assertThat(hendelse.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT.name)
                assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.name)
                assertThat(hendelse.aktørIdentifikasjon).isEqualTo("testperson")
                assertThatCode { UUID.fromString(hendelse.id) }
                    .doesNotThrowAnyException()
                assertThat(hendelse.tidspunkt.toInstant())
                    .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
            }
        }
    }

    @Test
    fun hentJobbsøkerHendelserTest() {
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
        db.leggTilJobbsøkere(
            listOf(
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
            )
        )
        db.leggTilJobbsøkere(
            listOf(
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
            )
        )
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/hendelser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<JobbsøkerHendelseMedJobbsøkerDataOutboundDto>> {
                override fun deserialize(content: String): List<JobbsøkerHendelseMedJobbsøkerDataOutboundDto> {
                    val type = mapper.typeFactory.constructCollectionType(
                        List::class.java,
                        JobbsøkerHendelseMedJobbsøkerDataOutboundDto::class.java
                    )
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
                    assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT.name)
                    assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.name)
                    assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
                    assertThat(h.fødselsnummer).isIn("11111111111", "22222222222")
                    assertThat(h.kandidatnummer).isIn("K111", "K222")
                    assertThat(h.fornavn).isIn("Ola", "Kari")
                    assertThat(h.etternavn).isEqualTo("Nordmann")
                    assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
                }
            }
        }
    }

    @Test
    fun invitererJobbsøkere() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr1 = Fødselsnummer("12312312312")
        val fnr2 = Fødselsnummer("45645645645")

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(treffId, fnr1, Kandidatnummer("K1"), Fornavn("F1"), Etternavn("E1"), null, null, null),
                Jobbsøker(treffId, fnr2, Kandidatnummer("K2"), Fornavn("F2"), Etternavn("E2"), null, null, null)
            )
        )

        assertThat(db.hentJobbsøkerHendelser(treffId)).hasSize(2)

        val requestBody = """
            { "fødselsnumre": ["${fnr1.asString}", "${fnr2.asString}"] }
        """.trimIndent()

        val (_, r, res) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_OK, r, res)

        val hendelser = db.hentJobbsøkerHendelser(treffId)
        assertThat(hendelser).hasSize(4)

        val inviterHendelser = hendelser.filter { it.hendelsestype == JobbsøkerHendelsestype.INVITER }
        assertThat(inviterHendelser).hasSize(2)
        inviterHendelser.forEach {
            assertThat(it.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
            assertThat(it.aktørIdentifikasjon).isEqualTo("A123456")
        }

        val inviterteFødselsnumre =
            inviterHendelser.map { db.hentFødselsnummerForJobbsøkerHendelse(it.id)}

        assertThat(inviterteFødselsnumre)
            .containsExactlyInAnyOrder(fnr1, fnr2)
    }

    @Test
    fun `svar ja til invitasjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("12345678901")
        //TODO: verifiser at det er bruker som svarer for seg selv
        val token = authServer.lagTokenBorger(authPort, pid = fnr.asString)

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    treffId,
                    fnr,
                    Kandidatnummer("K123"),
                    Fornavn("Test"),
                    Etternavn("Person"),
                    null, null, null
                )
            )
        )

        assertThat(db.hentJobbsøkerHendelser(treffId)).hasSize(1)

        val requestBody = """
            { "fødselsnummer": "${fnr.asString}" }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/svar-ja")
            .body(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_OK, response, result)

        val hendelser = db.hentJobbsøkerHendelser(treffId)
        assertThat(hendelser).hasSize(2)

        val svarJaHendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.SVAR_JA_TIL_INVITASJON }
        assertThat(svarJaHendelse).isNotNull
        svarJaHendelse!!
        assertThat(svarJaHendelse.opprettetAvAktørType).isEqualTo(AktørType.JOBBSØKER)
        assertThat(svarJaHendelse.aktørIdentifikasjon).isEqualTo("12345678901")
        assertThat(svarJaHendelse.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))

        val svarJaFødselsnummer = db.hentFødselsnummerForJobbsøkerHendelse(svarJaHendelse.id)
        assertThat(svarJaFødselsnummer).isEqualTo(fnr)
    }

    @Test
    fun `svar nei til invitasjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("12345678901")
        val token = authServer.lagTokenBorger(authPort, pid = fnr.asString)

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    treffId,
                    fnr,
                    Kandidatnummer("K123"),
                    Fornavn("Test"),
                    Etternavn("Person"),
                    null, null, null
                )
            )
        )

        assertThat(db.hentJobbsøkerHendelser(treffId)).hasSize(1)

        val requestBody = """
            { "fødselsnummer": "${fnr.asString}" }
        """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/svar-nei")
            .body(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_OK, response, result)

        val hendelser = db.hentJobbsøkerHendelser(treffId)
        assertThat(hendelser).hasSize(2)

        val svarNeiHendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.SVAR_NEI_TIL_INVITASJON }
        assertThat(svarNeiHendelse).isNotNull
        svarNeiHendelse!!
        assertThat(svarNeiHendelse.opprettetAvAktørType).isEqualTo(AktørType.JOBBSØKER)
        assertThat(svarNeiHendelse.aktørIdentifikasjon).isEqualTo("12345678901")
        assertThat(svarNeiHendelse.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))

        val svarNeiFødselsnummer = db.hentFødselsnummerForJobbsøkerHendelse(svarNeiHendelse.id)
        assertThat(svarNeiFødselsnummer).isEqualTo(fnr)
    }

    @Test
    fun `hentEnketreffIdltJobbsøker returnerer jobbsøker med alle data`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("11111111111")
        val token = authServer.lagToken(authPort, navIdent = "testperson")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(
                    treffId,
                    fødselsnummer,
                    Kandidatnummer("K1"),
                    Fornavn("Test"),
                    Etternavn("Person"),
                    Navkontor("NAV En"),
                    VeilederNavn("Veileder En"),
                    VeilederNavIdent("V1")
                )
            )
        )

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body("""{ "fødselsnumre": ["${fødselsnummer.asString}"] }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/svar-ja")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${borgerToken.serialize()}")
            .responseString()


        val (_, _, result) = hentEnkeltJobbsøker(treffId, fødselsnummer, borgerToken)

        val jobbsøker = result.get()
        assertThat(jobbsøker.fødselsnummer).isEqualTo(fødselsnummer.asString)
        assertThat(jobbsøker.kandidatnummer).isEqualTo("K1")
        assertThat(jobbsøker.fornavn).isEqualTo("Test")
        assertThat(jobbsøker.etternavn).isEqualTo("Person")
        assertThat(jobbsøker.navkontor).isEqualTo("NAV En")
        assertThat(jobbsøker.veilederNavn).isEqualTo("Veileder En")
        assertThat(jobbsøker.veilederNavIdent).isEqualTo("V1")
        assertThat(jobbsøker.hendelser).hasSize(3)
    }

    @Test
    fun `hentEnkeltJobbsøker håndterer status påmeldt`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("11111111111")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body("""{ "fødselsnumre": ["${fødselsnummer.asString}"] }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/svar-ja")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${borgerToken.serialize()}")
            .responseString()

        val (_, _, result) = hentEnkeltJobbsøker(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().erPåmeldt).isTrue()
        assertThat(result.get().erInvitert).isTrue()
    }

    @Test
    fun `hentEnkeltJobbsøker håndterer status avmeldt`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("22222222222")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body("""{ "fødselsnumre": ["${fødselsnummer.asString}"] }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/svar-nei")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${borgerToken.serialize()}")
            .responseString()

        val (_, _, result) = hentEnkeltJobbsøker(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().erPåmeldt).isFalse()
        assertThat(result.get().erInvitert).isTrue()
    }

    @Test
    fun `hentEnkeltJobbsøker håndterer status kun invitert`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("33333333333")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body("""{ "fødselsnumre": ["${fødselsnummer.asString}"] }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        val (_, _, result) = hentEnkeltJobbsøker(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().erPåmeldt).isFalse()
        assertThat(result.get().erInvitert).isTrue()
    }

    @Test
    fun `hentEnkeltJobbsøker håndterer status ikke invitert`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("44444444444")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        val (_, _, result) = hentEnkeltJobbsøker(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().erPåmeldt).isFalse()
        assertThat(result.get().erInvitert).isFalse()
    }

    @Test
    fun `hentEnkeltJobbsøker returnerer 404 for ukjent jobbsøker`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("44444444444")
        val token = authServer.lagTokenBorger(authPort, pid = fnr.asString)

        val (_, response, _) = hentEnkeltJobbsøker(treffId, Fødselsnummer("99999999999"), token)
        assertThat(response.statusCode).isEqualTo(HTTP_NOT_FOUND)
    }

    private fun hentEnkeltJobbsøker(treffId: TreffId, fødselsnummer: Fødselsnummer, token: com.nimbusds.jwt.SignedJWT) =
        Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/${fødselsnummer.asString}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<JobbsøkerMedPåmeldingstatusOutboundDto> {
                override fun deserialize(content: String) =
                    mapper.readValue(content, JobbsøkerMedPåmeldingstatusOutboundDto::class.java)
            })



}
