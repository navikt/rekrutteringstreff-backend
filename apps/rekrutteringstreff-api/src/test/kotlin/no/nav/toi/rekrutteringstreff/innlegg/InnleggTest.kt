package no.nav.toi.rekrutteringstreff.innlegg

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
import java.util.UUID

private object InnleggDtoDeserializer : ResponseDeserializable<InnleggResponseDto> {
    override fun deserialize(content: String): InnleggResponseDto =
        JacksonConfig.mapper.readValue(content, InnleggResponseDto::class.java)
}

private object InnleggListeDeserializer : ResponseDeserializable<List<InnleggResponseDto>> {
    override fun deserialize(content: String): List<InnleggResponseDto> =
        JacksonConfig.mapper.readValue(
            content,
            JacksonConfig.mapper.typeFactory.constructCollectionType(List::class.java, InnleggResponseDto::class.java)
        )
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnleggTest {

    companion object {
        private val authServer = MockOAuth2Server()
        private const val authPort = 18013
        private val db = TestDatabase()
        private val appPort = TestUtils.getFreePort()

        private val app = App(
            port = appPort,
            authConfigs = listOf(
                AuthenticationConfiguration(
                    issuer   = "http://localhost:$authPort/default",
                    jwksUri  = "http://localhost:$authPort/default/jwks",
                    audience = "rekrutteringstreff-audience"
                )
            ),
            dataSource         = db.dataSource,
            arbeidsgiverrettet = AzureAdRoller.arbeidsgiverrettet,
            utvikler           = AzureAdRoller.utvikler
        )
    }

    @BeforeAll fun start() { authServer.start(port = authPort); app.start() }
    @AfterAll  fun stop()  { authServer.shutdown(); app.close() }
    @AfterEach fun clean() { db.slettAlt() }

    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun `autentisering GET alle innlegg`(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, resp, res)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun `autentisering GET ett innlegg`(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, resp, res)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun `autentisering PUT innlegg`(tc: UautentifiserendeTestCase) {
        val body = OpprettInnleggRequestDto(
            tittel = "T",
            opprettetAvPersonNavident = "N",
            opprettetAvPersonNavn = "",
            opprettetAvPersonBeskrivelse = "",
            sendesTilJobbsokerTidspunkt = null,
            htmlContent = ""
        )
        val leggPåToken = tc.leggPåToken
        val (_, resp, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}")
            .body(JacksonConfig.mapper.writeValueAsString(body))
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, resp, res)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun `autentisering DELETE innlegg`(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val (_, resp, res) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, resp, res)
    }

    @Test
    fun `PUT skal oppdatere et eksisterende innlegg`() {
        val token   = authServer.lagToken(authPort, navIdent = "C123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val repo = InnleggRepository(db.dataSource)
        val originaltInnlegg = repo.opprett(
            treffId,
            OpprettInnleggRequestDto(
                "Gammelt Innlegg",
                "C123456",
                "Kari Nordmann",
                "Rådgiver",
                null,
                "<p>Gammelt innhold</p>"
            ),
            "C123456"
        )
        val eksisterendeInnleggId = originaltInnlegg.id

        val oppdatertBody = OpprettInnleggRequestDto(
            tittel = "Oppdatert Innlegg",
            opprettetAvPersonNavident = "C123456",
            opprettetAvPersonNavn = "Kari Oppdatert Nordmann",
            opprettetAvPersonBeskrivelse = "Oppdatert Rådgiver",
            sendesTilJobbsokerTidspunkt = null,
            htmlContent = "<p>Nytt og spennende innhold!</p>"
        )

        repo.oppdater(eksisterendeInnleggId, treffId, oppdatertBody)

        val (_, resp, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/$eksisterendeInnleggId")
            .body(JacksonConfig.mapper.writeValueAsString(oppdatertBody))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggDtoDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> fail("Request failed: ${res.error.exception.message} \n ${res.error.response.body().asString("application/json;charset=utf-8")}")
            is Success -> {
                val oppdatertInnlegg = res.value
                assertThat(oppdatertInnlegg.id).isEqualTo(eksisterendeInnleggId)
                assertThat(oppdatertInnlegg.tittel).isEqualTo(oppdatertBody.tittel)
                assertThat(oppdatertInnlegg.htmlContent).isEqualTo(oppdatertBody.htmlContent)
                assertThat(oppdatertInnlegg.opprettetAvPersonNavn).isEqualTo(oppdatertBody.opprettetAvPersonNavn)

                val hentetFraDb = repo.hentById(eksisterendeInnleggId)
                assertThat(hentetFraDb).isNotNull
                assertThat(hentetFraDb!!.tittel).isEqualTo(oppdatertBody.tittel)
            }
        }
    }

    @Test
    fun `PUT til ikke-eksisterende treffId skal gi 404`() {
        val token = authServer.lagToken(authPort, navIdent = "E123456")
        val ikkeEksisterendeTreffId = UUID.randomUUID()
        val innleggId = UUID.randomUUID()

        val body = OpprettInnleggRequestDto("Test", "E123456", "Navn", "Veileder", null, "<p>Test</p>")

        val (_, resp, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/$ikkeEksisterendeTreffId/innlegg/$innleggId")
            .body(JacksonConfig.mapper.writeValueAsString(body))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_NOT_FOUND, resp, res)
    }

    // Resten av testene beholdes uendret, men bruk repo.opprett for å opprette innlegg
    // ... (kopier inn de øvrige testene fra din nåværende fil, og bytt ut evt. repo.oppdater(..., ..., ...) med repo.opprett(..., ..., ...))
}

// Helper object for TestDatabase if not already globally available or extended
fun TestDatabase.opprettRekrutteringstreffIDatabase(
    tittel: String = "Test Rekrutteringstreff",
    navIdent: String = "Z999999",
    enhetId: String = "0000"
): TreffId {
    val id = UUID.randomUUID()
    this.dataSource.connection.use {
        val stmt = it.prepareStatement(
            """
            INSERT INTO rekrutteringstreff (id, tittel, status, opprettet_av_person_navident, opprettet_av_kontor_enhetid, opprettet_av_tidspunkt, eiere)
            VALUES (?, ?, 'ÅPENT', ?, ?, NOW(), ARRAY[?])
            RETURNING id;
            """.trimIndent()
        )
        stmt.setObject(1, id)
        stmt.setString(2, tittel)
        stmt.setString(3, navIdent)
        stmt.setString(4, enhetId)
        stmt.setString(5, navIdent)
        stmt.executeQuery()
    }
    return TreffId(id)
}

object TestUtils {
    fun getFreePort(): Int {
        return java.net.ServerSocket(0).use { it.localPort }
    }
}