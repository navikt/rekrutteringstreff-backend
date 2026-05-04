package no.nav.toi.arbeidsgiver

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto
import no.nav.toi.arbeidsgiver.dto.ArbeidsgiverMedBehovDto
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
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class ArbeidsgiverBehovTest {

    companion object {
        private val authServer = MockOAuth2Server()
        private val authPort = 18021
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
            jobbsøkerrettet = jobbsøkerrettet,
            arbeidsgiverrettet = arbeidsgiverrettet,
            utvikler = utvikler,
            kandidatsokApiUrl = "",
            kandidatsokScope = "",
            rapidsConnection = TestRapid(),
            accessTokenClient = accessTokenClient,
            modiaKlient = ModiaKlient(
                modiaContextHolderUrl = wmInfo.httpBaseUrl,
                modiaContextHolderScope = "",
                accessTokenClient = accessTokenClient,
                httpClient = httpClient
            ),
            pilotkontorer = listOf("1234"),
            httpClient = httpClient,
            leaderElection = LeaderElectionMock(),
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
                        .withBody("""{"aktivEnhet": "1234"}""")
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

    private fun gyldigBehovJson(antall: Int = 3): String = """
        {
          "samledeKvalifikasjoner": [
            {"label": "Kokk", "kategori": "YRKESTITTEL", "konseptId": 175819},
            {"label": "Førerkort klasse B", "kategori": "FORERKORT", "konseptId": 91501}
          ],
          "arbeidssprak": ["Norsk", "Engelsk"],
          "antall": $antall,
          "ansettelsesformer": ["Fast", "Vikariat"],
          "personligeEgenskaper": [
            {"label": "Kundebehandler", "kategori": "PERSONLIG_EGENSKAP", "konseptId": 700005}
          ]
        }
    """.trimIndent()

    private fun arbeidsgiverMedBehovBody(orgnr: String = "555555555", navn: String = "Eksempel AS", behovJson: String = gyldigBehovJson()): String = """
        {
          "organisasjonsnummer": "$orgnr",
          "navn": "$navn",
          "behov": $behovJson
        }
    """.trimIndent()

    @Test
    fun `POST arbeidsgiver-med-behov oppretter atomisk og emitter OPPRETTET + BEHOV_ENDRET`() {
        val token = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        val response = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            arbeidsgiverMedBehovBody(),
            token
        )

        assertThat(response.statusCode()).isEqualTo(HTTP_CREATED)

        val medBehov = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            token
        )
        assertThat(medBehov.statusCode()).isEqualTo(HTTP_OK)
        val mapper = JacksonConfig.mapper
        val type = mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverMedBehovDto::class.java)
        val resultat: List<ArbeidsgiverMedBehovDto> = mapper.readValue(medBehov.body(), type)
        assertThat(resultat).hasSize(1)
        val ag = resultat.first()
        assertThat(ag.organisasjonsnummer).isEqualTo("555555555")
        assertThat(ag.behov).isNotNull
        assertThat(ag.behov!!.antall).isEqualTo(3)
        assertThat(ag.behov.arbeidssprak).containsExactly("Norsk", "Engelsk")
        assertThat(ag.behov.ansettelsesformer).containsExactly("Fast", "Vikariat")
        assertThat(ag.behov.samledeKvalifikasjoner).hasSize(2)
        assertThat(ag.behov.samledeKvalifikasjoner.first().label).isEqualTo("Kokk")
        assertThat(ag.behov.personligeEgenskaper).hasSize(1)

        val hendelser = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/hendelser",
            token
        )
        assertThat(hendelser.statusCode()).isEqualTo(HTTP_OK)
        val htype = mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto::class.java)
        val hendelseListe: List<ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto> = mapper.readValue(hendelser.body(), htype)
        assertThat(hendelseListe.map { it.hendelsestype }).containsExactlyInAnyOrder(
            ArbeidsgiverHendelsestype.OPPRETTET.name,
            ArbeidsgiverHendelsestype.BEHOV_ENDRET.name,
        )
    }

    @Test
    fun `POST arbeidsgiver-med-behov med antall over 99 gir 400`() {
        val token = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        val response = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            arbeidsgiverMedBehovBody(behovJson = gyldigBehovJson(antall = 100)),
            token
        )
        assertThat(response.statusCode()).isEqualTo(HTTP_BAD_REQUEST)
        assertThat(db.hentAlleArbeidsgivere()).isEmpty()
    }

    @Test
    fun `POST arbeidsgiver-med-behov uten gyldig behov gir 400 og ingen lagring`() {
        val token = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        val ugyldigBehov = """
            {
              "samledeKvalifikasjoner": [],
              "arbeidssprak": ["Norsk"],
              "antall": 1,
              "ansettelsesformer": ["Fast"]
            }
        """.trimIndent()

        val response = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            arbeidsgiverMedBehovBody(behovJson = ugyldigBehov),
            token
        )
        assertThat(response.statusCode()).isEqualTo(HTTP_BAD_REQUEST)
        assertThat(db.hentAlleArbeidsgivere()).isEmpty()
    }

    @Test
    fun `POST arbeidsgiver-med-behov med ukjent ansettelsesform gir 400`() {
        val token = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        val ugyldigBehov = """
            {
              "samledeKvalifikasjoner": [{"label": "Kokk", "kategori": "YRKESTITTEL", "konseptId": 175819}],
              "arbeidssprak": ["Norsk"],
              "antall": 1,
              "ansettelsesformer": ["TullAnsettelse"]
            }
        """.trimIndent()

        val response = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            arbeidsgiverMedBehovBody(behovJson = ugyldigBehov),
            token
        )
        assertThat(response.statusCode()).isEqualTo(HTTP_BAD_REQUEST)
        assertThat(db.hentAlleArbeidsgivere()).isEmpty()
    }

    @Test
    fun `PUT behov upserter og emitter BEHOV_ENDRET, returnerer oppdatert DTO`() {
        val token = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        // Først legg til arbeidsgiver uten behov via gammelt POST-endepunkt for å simulere "eldre data"
        val opprettResp = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver",
            """{"organisasjonsnummer": "555555555", "navn": "Eksempel AS"}""",
            token
        )
        assertThat(opprettResp.statusCode()).isEqualTo(HTTP_CREATED)

        val medBehovFør = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            token
        )
        val mapper = JacksonConfig.mapper
        val type = mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverMedBehovDto::class.java)
        val før: List<ArbeidsgiverMedBehovDto> = mapper.readValue(medBehovFør.body(), type)
        assertThat(før).hasSize(1)
        assertThat(før.first().behov).isNull()

        val arbeidsgiverTreffId = før.first().arbeidsgiverTreffId
        val putResp = httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/$arbeidsgiverTreffId/behov",
            gyldigBehovJson(antall = 7),
            token
        )
        assertThat(putResp.statusCode()).isEqualTo(HTTP_OK)
        val oppdatert: ArbeidsgiverMedBehovDto = mapper.readValue(putResp.body(), ArbeidsgiverMedBehovDto::class.java)
        assertThat(oppdatert.behov?.antall).isEqualTo(7)

        // Andre PUT som faktisk oppdaterer
        val putResp2 = httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/$arbeidsgiverTreffId/behov",
            gyldigBehovJson(antall = 9),
            token
        )
        assertThat(putResp2.statusCode()).isEqualTo(HTTP_OK)
        val oppdatert2: ArbeidsgiverMedBehovDto = mapper.readValue(putResp2.body(), ArbeidsgiverMedBehovDto::class.java)
        assertThat(oppdatert2.behov?.antall).isEqualTo(9)

        val hendelser = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/hendelser",
            token
        )
        val htype = mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto::class.java)
        val hendelseListe: List<ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto> = mapper.readValue(hendelser.body(), htype)
        assertThat(hendelseListe.count { it.hendelsestype == ArbeidsgiverHendelsestype.BEHOV_ENDRET.name }).isEqualTo(2)
    }

    @Test
    fun `PUT behov for ukjent arbeidsgiver gir 404`() {
        val token = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        val response = httpPut(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/${UUID.randomUUID()}/behov",
            gyldigBehovJson(antall = 7),
            token
        )

        assertThat(response.statusCode()).isEqualTo(HTTP_NOT_FOUND)
    }

    @Test
    fun `GET arbeidsgiver-med-behov gir 403 for ikke-eier`() {
        val tokenEier = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val tokenAnnen = authServer.lagToken(authPort, navIdent = "B222222").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            arbeidsgiverMedBehovBody(),
            tokenEier
        )

        val response = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            tokenAnnen
        )
        assertThat(response.statusCode()).isEqualTo(HTTP_FORBIDDEN)
    }

    @Test
    fun `Soft-slettet arbeidsgiver skjuler behov, reaktivering bevarer behov`() {
        val token = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        // Oppretter med behov
        httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            arbeidsgiverMedBehovBody(),
            token
        )

        // Slett arbeidsgiver
        val mapper = JacksonConfig.mapper
        val type = mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverMedBehovDto::class.java)
        val før: List<ArbeidsgiverMedBehovDto> = mapper.readValue(
            httpGet("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov", token).body(),
            type
        )
        val arbeidsgiverTreffId = før.first().arbeidsgiverTreffId
        httpDelete(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/$arbeidsgiverTreffId",
            token
        )

        val etterSlett: List<ArbeidsgiverMedBehovDto> = mapper.readValue(
            httpGet("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov", token).body(),
            type
        )
        assertThat(etterSlett).isEmpty()

        // Reaktivering ved ny POST på samme orgnr
        val reaktiverBehov = """
            {
              "samledeKvalifikasjoner": [{"label": "Servitør", "kategori": "YRKESTITTEL", "konseptId": 175820}],
              "arbeidssprak": ["Norsk"],
              "antall": 5,
              "ansettelsesformer": ["Sesong"]
            }
        """.trimIndent()
        val reaktiverResp = httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            arbeidsgiverMedBehovBody(behovJson = reaktiverBehov),
            token
        )
        assertThat(reaktiverResp.statusCode()).isEqualTo(HTTP_CREATED)

        val etterReaktivering: List<ArbeidsgiverMedBehovDto> = mapper.readValue(
            httpGet("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov", token).body(),
            type
        )
        assertThat(etterReaktivering).hasSize(1)
        // Samme arbeidsgiverTreffId — reaktivert rad
        assertThat(etterReaktivering.first().arbeidsgiverTreffId).isEqualTo(arbeidsgiverTreffId)
        // Behov er oppdatert med ny payload
        assertThat(etterReaktivering.first().behov?.antall).isEqualTo(5)
        assertThat(etterReaktivering.first().behov?.ansettelsesformer).containsExactly("Sesong")

        // Reaktivering skal ha generert REAKTIVERT-hendelse i stedet for ny OPPRETTET-hendelse
        val hendelser = httpGet(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver/hendelser",
            token
        )
        val htype = mapper.typeFactory.constructCollectionType(List::class.java, ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto::class.java)
        val hendelseListe: List<ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto> = mapper.readValue(hendelser.body(), htype)
        val typer = hendelseListe.map { it.hendelsestype }
        assertThat(typer.count { it == ArbeidsgiverHendelsestype.OPPRETTET.name }).isEqualTo(1)
        assertThat(typer.count { it == ArbeidsgiverHendelsestype.SLETTET.name }).isEqualTo(1)
        assertThat(typer.count { it == ArbeidsgiverHendelsestype.REAKTIVERT.name }).isEqualTo(1)
        assertThat(typer.count { it == ArbeidsgiverHendelsestype.BEHOV_ENDRET.name }).isEqualTo(2)
    }

    @Test
    fun `BEHOV_ENDRET-hendelsen lagrer behov-payload som hendelse_data`() {
        val token = authServer.lagToken(authPort, navIdent = "A111111").serialize()
        val treffId = db.opprettRekrutteringstreffIDatabase()
        eierRepository.leggTil(treffId, listOf("A111111"))

        httpPost(
            "http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/arbeidsgiver-med-behov",
            arbeidsgiverMedBehovBody(),
            token
        )

        val data = db.hentHendelseDataFørste("BEHOV_ENDRET")
        assertThat(data).isNotNull()
        val ikkeNull: String = data!!
        // jsonb-utdrag fra Postgres bruker "key": value med mellomrom; vi kompakterer for sammenligning
        val kompakt = ikkeNull.replace(" ", "")
        assertThat(kompakt).contains("\"antall\":3")
        assertThat(kompakt).contains("\"Norsk\"")
        assertThat(kompakt).contains("\"Fast\"")
        assertThat(kompakt).contains("\"konseptId\":175819")
    }
}
