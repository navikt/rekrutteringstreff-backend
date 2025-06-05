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
        val leggPåToken = tc.leggPåToken
        val body = OpprettInnleggRequestDto(
            tittel = "T",
            opprettetAvPersonNavident = "N",
            opprettetAvPersonNavn = "",
            opprettetAvPersonBeskrivelse = "",
            sendesTilJobbsokerTidspunkt = null,
            htmlContent = ""
        )
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
    fun `PUT skal opprette nytt innlegg hvis det ikke finnes`() {
        val token   = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val nyttInnleggId = UUID.randomUUID()

        val body = OpprettInnleggRequestDto(
            tittel = "Nytt Innlegg via PUT",
            opprettetAvPersonNavident = "A123456",
            opprettetAvPersonNavn = "Ola Nordmann",
            opprettetAvPersonBeskrivelse = "Veileder",
            sendesTilJobbsokerTidspunkt = null,
            htmlContent = "<p>Innhold for nytt innlegg</p>"
        )

        val (_, resp, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/$nyttInnleggId")
            .body(JacksonConfig.mapper.writeValueAsString(body))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggDtoDeserializer)

        assertStatuscodeEquals(HTTP_CREATED, resp, res)
        when (res) {
            is Failure -> fail("Request failed: ${res.error.exception.message} \n ${res.error.response.body().asString("application/json;charset=utf-8")}")
            is Success -> {
                val opprettetInnlegg = res.value
                assertThat(opprettetInnlegg.id).isEqualTo(nyttInnleggId)
                assertThat(opprettetInnlegg.tittel).isEqualTo(body.tittel)
                assertThat(opprettetInnlegg.treffId).isEqualTo(treffId.somUuid)

                val hentetFraDb = InnleggRepository(db.dataSource).hentById(nyttInnleggId)
                assertThat(hentetFraDb).isNotNull
                assertThat(hentetFraDb!!.id).isEqualTo(nyttInnleggId)
                assertThat(hentetFraDb.tittel).isEqualTo(body.tittel)
            }
        }
    }


    @Test
    fun `hentAlleInnlegg skal returnere innlegg for treffet`() {
        val token   = authServer.lagToken(authPort, navIdent = "B123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()

        val repo = InnleggRepository(db.dataSource)
        // Use PUT to create innlegg
        val innlegg1Id = UUID.randomUUID()
        val innlegg2Id = UUID.randomUUID()

        repo.oppdater(innlegg1Id, treffId, OpprettInnleggRequestDto("T1","B123456","Navn","Veileder",null,"<p1/>")) // Upsert
        repo.oppdater(innlegg2Id, treffId, OpprettInnleggRequestDto("T2","B123456","Navn","Veileder",null,"<p2/>")) // Upsert

        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggListeDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> fail("Request failed: ${res.error.exception.message}")
            is Success -> {
                assertThat(res.value).hasSize(2)
                assertThat(res.value.map { it.id }).containsExactlyInAnyOrder(innlegg1Id, innlegg2Id)
            }
        }
    }


    @Test
    fun `PUT skal oppdatere et eksisterende innlegg`() {
        val token   = authServer.lagToken(authPort, navIdent = "C123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()

        val repo = InnleggRepository(db.dataSource)
        val eksisterendeInnleggId = UUID.randomUUID()
        val (originaltInnlegg, _) = repo.oppdater(
            eksisterendeInnleggId,
            treffId,
            OpprettInnleggRequestDto("Gammelt Innlegg","C123456","Kari Nordmann","Rådgiver",null,"<p>Gammelt innhold</p>")
        )

        val oppdatertBody = OpprettInnleggRequestDto(
            tittel = "Oppdatert Innlegg",
            opprettetAvPersonNavident = "C123456", // Kan være samme eller annen
            opprettetAvPersonNavn = "Kari Oppdatert Nordmann",
            opprettetAvPersonBeskrivelse = "Oppdatert Rådgiver",
            sendesTilJobbsokerTidspunkt = null, // Kan endres
            htmlContent = "<p>Nytt og spennende innhold!</p>"
        )

        val (_, resp, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/${originaltInnlegg.id}")
            .body(JacksonConfig.mapper.writeValueAsString(oppdatertBody))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggDtoDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> fail("Request failed: ${res.error.exception.message} \n ${res.error.response.body().asString("application/json;charset=utf-8")}")
            is Success -> {
                val oppdatertInnlegg = res.value
                assertThat(oppdatertInnlegg.id).isEqualTo(originaltInnlegg.id)
                assertThat(oppdatertInnlegg.tittel).isEqualTo(oppdatertBody.tittel)
                assertThat(oppdatertInnlegg.htmlContent).isEqualTo(oppdatertBody.htmlContent)
                assertThat(oppdatertInnlegg.opprettetAvPersonNavn).isEqualTo(oppdatertBody.opprettetAvPersonNavn)
                assertThat(oppdatertInnlegg.sistOppdatertTidspunkt).isAfter(originaltInnlegg.sistOppdatertTidspunkt)

                val hentetFraDb = InnleggRepository(db.dataSource).hentById(originaltInnlegg.id)
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


    @Test
    fun `slettInnlegg skal fjerne innlegget`() {
        val token   = authServer.lagToken(authPort, navIdent = "D123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()

        val repo = InnleggRepository(db.dataSource)
        val innleggId = UUID.randomUUID()
        // Create innlegg using PUT via repository's upsert
        val (innleggSomSkalSlettes, _) = repo.oppdater(
            innleggId,
            treffId,
            OpprettInnleggRequestDto("Innlegg for sletting","D123456","Slette Meg","Test",null,"<p>Slettes snart</p>")
        )

        val (_, resp, res) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/${innleggSomSkalSlettes.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_NO_CONTENT, resp, res)
        assertThat(repo.hentById(innleggSomSkalSlettes.id)).isNull()
    }

    @Test
    fun `slettInnlegg for ikke-eksisterende innleggId skal gi 404`() {
        val token = authServer.lagToken(authPort, navIdent = "F123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val ikkeEksisterendeInnleggId = UUID.randomUUID()

        val (_, resp, res) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/$ikkeEksisterendeInnleggId")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_NOT_FOUND, resp, res)
    }

    @Test
    fun `hentEttInnlegg skal returnere korrekt innlegg`() {
        val token = authServer.lagToken(authPort, navIdent = "G123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val repo = InnleggRepository(db.dataSource)
        val innleggId = UUID.randomUUID()
        val (opprettetInnlegg, _) = repo.oppdater(
            innleggId,
            treffId,
            OpprettInnleggRequestDto("Hent meg", "G123456", "Test Person", "Tester", null, "<p>Hentbart innhold</p>")
        )

        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/${opprettetInnlegg.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggDtoDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> fail("Request failed: ${res.error.exception.message}")
            is Success -> {
                val hentetInnlegg = res.value
                assertThat(hentetInnlegg.id).isEqualTo(opprettetInnlegg.id)
                assertThat(hentetInnlegg.tittel).isEqualTo(opprettetInnlegg.tittel)
            }
        }
    }

    @Test
    fun `hentEttInnlegg for ikke-eksisterende innleggId skal gi 404`() {
        val token = authServer.lagToken(authPort, navIdent = "H123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val ikkeEksisterendeInnleggId = UUID.randomUUID()

        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/$ikkeEksisterendeInnleggId")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_NOT_FOUND, resp, res)
    }
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
        stmt.setString(5, navIdent) // Assuming navIdent is an owner
        stmt.executeQuery()
    }
    return TreffId(id)
}

object TestUtils {
    fun getFreePort(): Int {
        return java.net.ServerSocket(0).use { it.localPort }
    }
}