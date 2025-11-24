package no.nav.toi.arbeidsgiver

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.JacksonConfig
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverOutboundDto
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.HttpURLConnection.HTTP_NO_CONTENT
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class ArbeidsgiverTest {

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18012
        private val db = TestDatabase()
        private val appPort = ubruktPortnr()

        private lateinit var app: App
    }

    private val eierRepository = EierRepository(dataSource = db.dataSource)

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        authServer.start(port = authPort)
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
            arbeidsgiverrettet = arbeidsgiverrettet,
            utvikler = utvikler,
            kandidatsokApiUrl = "",
            kandidatsokScope = "",
            rapidsConnection =  TestRapid(),
            accessTokenClient = accessTokenClient,
            modiaKlient = ModiaKlient(
                modiaContextHolderUrl = wmInfo.httpBaseUrl,
                modiaContextHolderScope = "",
                accessTokenClient = accessTokenClient,
                httpClient = httpClient
            ),
            pilotkontorer = listOf("1234")
        ).also { it.start() }
    }

    @BeforeEach
    fun setupStubs() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "aktivEnhet": "1234"
                            }
                            """.trimIndent()
                        )
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
    fun leggTilArbeidsgiverMedTomListeForNæringskoder() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val orgnr = Orgnr("555555555")
        val orgnavn = Orgnavn("Oooorgnavn")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A123456"))
        val requestBody = JacksonConfig.mapper.writeValueAsString(
            mapOf(
                "organisasjonsnummer" to orgnr.asString,
                "navn" to orgnavn.asString,
                "næringskoder" to emptyList<Næringskode>()
            )
        )
        assertThat(db.hentAlleArbeidsgivere()).isEmpty()

        val (_, response, result) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/arbeidsgiver")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        val næringskoder = db.hentNæringskodeForArbeidsgiverPåTreff(treffId, orgnr)

        assertStatuscodeEquals(HTTP_CREATED, response, result)

        val actualArbeidsgivere = db.hentAlleArbeidsgivere()
        assertThat(actualArbeidsgivere.size).isEqualTo(1)
        val ag = actualArbeidsgivere.first()
        assertThat(ag.treffId).isEqualTo(treffId)
        assertThat(ag.orgnr).isEqualTo(orgnr)
        assertThat(ag.orgnavn).isEqualTo(orgnavn)
        assertThat(ag.arbeidsgiverTreffId).isInstanceOf(ArbeidsgiverTreffId::class.java)
        assertThat(næringskoder).isEmpty()

        val hendelser = db.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser).hasSize(1)
        val h = hendelser.first()
        assertThat(h.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETTET)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktøridentifikasjon).isEqualTo("A123456")
    }

    @Test
    fun leggTilArbeidsgiverMedListeForNæringskoder() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val orgnr = Orgnr("555555555")
        val orgnavn = Orgnavn("Oooorgnavn")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A123456"))

        val næringskoder = listOf(Næringskode("47.111", "Detaljhandel med bredt varesortiment uten salg av drivstoff"))
        val requestBody = JacksonConfig.mapper.writeValueAsString(
            mapOf(
                "organisasjonsnummer" to orgnr.asString,
                "navn" to orgnavn.asString,
                "næringskoder" to listOf(mapOf("kode" to "47.111", "beskrivelse" to "Detaljhandel med bredt varesortiment uten salg av drivstoff"))
            )
        )
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
        assertThat(ag.arbeidsgiverTreffId).isInstanceOf(ArbeidsgiverTreffId::class.java)

        val nk = db.hentNæringskodeForArbeidsgiverPåTreff(treffId, orgnr)

        assertThat(nk).hasSize(1)
        assertThat(nk).isEqualTo(næringskoder)

        val hendelser = db.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser).hasSize(1)
        val h = hendelser.first()
        assertThat(h.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETTET)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
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
            Arbeidsgiver(ArbeidsgiverTreffId(UUID.randomUUID()), treffId1, orgnr1, orgnavn1, ArbeidsgiverStatus.AKTIV)
        )
        val arbeidsgivere2 = listOf(
            Arbeidsgiver(ArbeidsgiverTreffId(UUID.randomUUID()), treffId2, orgnr2, orgnavn2, ArbeidsgiverStatus.AKTIV),
            Arbeidsgiver(ArbeidsgiverTreffId(UUID.randomUUID()), treffId2, orgnr3, orgnavn3, ArbeidsgiverStatus.AKTIV)
        )
        val arbeidsgivere3 = listOf(
            Arbeidsgiver(ArbeidsgiverTreffId(UUID.randomUUID()), treffId3, orgnr4, orgnavn4, ArbeidsgiverStatus.AKTIV)
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

                actualArbeidsgivere.forEach { arbeidsgiver ->
                    assertThat(arbeidsgiver.hendelser).hasSize(1)
                    val hendelse = arbeidsgiver.hendelser.first()
                    assertThat(hendelse.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETTET.toString())
                    assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.name)
                    assertThat(hendelse.aktøridentifikasjon).isEqualTo("testperson")
                }

                val arbeidsgiverOrg2 = actualArbeidsgivere.find { it.organisasjonsnummer == orgnr2.asString }
                assertThat(arbeidsgiverOrg2).isNotNull
                val arbeidsgiverOrg3 = actualArbeidsgivere.find { it.organisasjonsnummer == orgnr3.asString }
                assertThat(arbeidsgiverOrg3).isNotNull
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
                assertThat(hendelse.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETTET.name)
                assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR.name)
                assertThat(hendelse.aktøridentifikasjon).isEqualTo("A123456")
                assertThat(hendelse.orgnr).isEqualTo("777777777")
                assertThat(hendelse.orgnavn).isEqualTo("HendelsesFirma")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringSlettArbeidsgiver(autentiseringstest: UautentifiserendeTestCase) {
        val leggPåToken = autentiseringstest.leggPåToken
        val (_, response, result) = Fuel.delete("http://localhost:${appPort}/api/rekrutteringstreff/${UUID.randomUUID()}/arbeidsgiver/${UUID.randomUUID()}")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, response, result)
    }

    @Test
    fun slettArbeidsgiver() {
        val token = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A123456"))
        val requestBody = """
            {
              "organisasjonsnummer": "888888888",
              "navn": "Slettefirma"
            }
        """.trimIndent()

        val (_, postResponse, postResult) = Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/arbeidsgiver")
            .body(requestBody)
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        assertStatuscodeEquals(HTTP_CREATED, postResponse, postResult)

        val id = db.hentAlleArbeidsgivere().first().arbeidsgiverTreffId.somUuid

        val (_, delResponse, delResult) = Fuel.delete("http://localhost:${appPort}/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/$id")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        assertStatuscodeEquals(HTTP_NO_CONTENT, delResponse, delResult)

        // Rad i arbeidsgiver-tabellen skal fortsatt finnes (soft delete)
        assertThat(db.hentAlleArbeidsgivere()).isNotEmpty

        // Skal ikke returneres av GET arbeidsgivere etter soft delete
        val (_, getResp, getRes) = Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<ArbeidsgiverOutboundDto>> {
                override fun deserialize(content: String): List<ArbeidsgiverOutboundDto> {
                    val mapper = JacksonConfig.mapper
                    val type = mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverOutboundDto::class.java)
                    return mapper.readValue(content, type)
                }
            })
        assertStatuscodeEquals(HTTP_OK, getResp, getRes)
        when (getRes) {
            is Failure -> throw getRes.error
            is Success -> assertThat(getRes.value).isEmpty()
        }

        // Hendelser skal inneholde SLETTET
        val (_, hendResp, hendRes) = Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/hendelser")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto>> {
                override fun deserialize(content: String): List<ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto> {
                    val type = JacksonConfig.mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto::class.java)
                    return JacksonConfig.mapper.readValue(content, type)
                }
            })
        assertStatuscodeEquals(HTTP_OK, hendResp, hendRes)
        when (hendRes) {
            is Failure -> throw hendRes.error
            is Success -> assertThat(hendRes.value.any { it.hendelsestype == ArbeidsgiverHendelsestype.SLETTET.name }).isTrue()
        }
    }
}
