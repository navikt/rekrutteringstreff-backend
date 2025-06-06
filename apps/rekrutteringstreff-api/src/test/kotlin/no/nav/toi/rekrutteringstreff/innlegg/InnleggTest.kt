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
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.text.get


private object InnleggDeserializer : ResponseDeserializable<InnleggResponseDto> {
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

    private val auth = MockOAuth2Server()
    private val authPort = 18013
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

    @BeforeAll fun start() { auth.start(port = authPort); app.start() }
    @AfterAll  fun stop()  { auth.shutdown(); app.close() }
    @AfterEach fun clean() { db.slettAlt() }

    fun tokenVarianter() = UautentifiserendeTestCase.somStrømAvArgumenter()

    @ParameterizedTest @MethodSource("tokenVarianter")
    fun `ukorrekt token GET alle`(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val (_, resp, res) = Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg")
            .leggPåToken(auth, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, resp, res)
    }

    @ParameterizedTest @MethodSource("tokenVarianter")
    fun `ukorrekt token GET ett`(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val (_, r, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}")
            .leggPåToken(auth, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, r, res)
    }

    @ParameterizedTest @MethodSource("tokenVarianter")
    fun `ukorrekt token PUT`(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val body = OppdaterInnleggRequestDto("T", "", "", null, "")
        val (_, r, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}")
            .body(JacksonConfig.mapper.writeValueAsString(body))
            .leggPåToken(auth, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, r, res)
    }

    @ParameterizedTest @MethodSource("tokenVarianter")
    fun `ukorrekt token DELETE`(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val (_, r, res) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}")
            .leggPåToken(auth, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, r, res)
    }

    @Test
    fun `PUT oppdaterer innlegg`() {
        val token  = auth.lagToken(authPort, navIdent = "C123456")
        val treff  = db.opprettRekrutteringstreffIDatabase()
        val repo   = InnleggRepository(db.dataSource)
        val id     = repo.opprett(treff, sampleOpprett(), "C123456").id

        val body = OppdaterInnleggRequestDto(
            tittel = "Ny Tittel",
            opprettetAvPersonNavn = "Kari Oppdatert",
            opprettetAvPersonBeskrivelse = "Oppdatert Rådgiver",
            sendesTilJobbsokerTidspunkt = null,
            htmlContent = "<p>Nytt innhold</p>"
        )

        val (_, resp, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg/$id")
            .body(JacksonConfig.mapper.writeValueAsString(body))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> fail(res.error.message)
            is Success -> {
                val dto = res.value
                assertThat(dto.tittel).isEqualTo(body.tittel)
                assertThat(dto.opprettetAvPersonNavn).isEqualTo(body.opprettetAvPersonNavn)
                assertThat(repo.hentById(id)!!.tittel).isEqualTo(body.tittel)
            }
        }
    }

    @Test
    fun `GET liste returnerer innlegg for treff`() {
        val token = auth.lagToken(authPort, navIdent = "C123456")
        val treff = db.opprettRekrutteringstreffIDatabase()
        val repo = InnleggRepository(db.dataSource)
        val id = repo.opprett(treff, sampleOpprett(), "C123456").id

        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggListeDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> fail(res.error.message)
            is Success -> assertThat(res.value).anySatisfy { it.id == id }
        }
    }

    @Test
    fun `GET ett returnerer innlegg`() {
        val token = auth.lagToken(authPort, navIdent = "C123456")
        val treff = db.opprettRekrutteringstreffIDatabase()
        val repo = InnleggRepository(db.dataSource)
        val id = repo.opprett(treff, sampleOpprett(), "C123456").id

        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg/$id")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> fail(res.error.message)
            is Success -> assertThat(res.value.id).isEqualTo(id)
        }
    }

    @Test
    fun `POST oppretter innlegg`() {
        val token = auth.lagToken(authPort, navIdent = "C123456")
        val treff = db.opprettRekrutteringstreffIDatabase()
        val body = sampleOpprett()

        val (_, resp, res) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg")
            .body(JacksonConfig.mapper.writeValueAsString(body))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggDeserializer)

        assertStatuscodeEquals(HTTP_CREATED, resp, res)
        when (res) {
            is Failure -> fail(res.error.message)
            is Success -> assertThat(res.value.tittel).isEqualTo(body.tittel)
        }
    }

    @Test
    fun `DELETE fjerner innlegg`() {
        val token = auth.lagToken(authPort, navIdent = "C123456")
        val treff = db.opprettRekrutteringstreffIDatabase()
        val repo = InnleggRepository(db.dataSource)
        val id = repo.opprett(treff, sampleOpprett(), "C123456").id

        val (_, resp, res) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${treff.somUuid}/innlegg/$id")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_NO_CONTENT, resp, res)
        assertThat(repo.hentById(id)).isNull()
    }

    @Test
    fun `PUT mot ukjent treff gir 404`() {
        val token = auth.lagToken(authPort, navIdent = "C123456") // Use a valid navIdent
        val (_, r, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg/${UUID.randomUUID()}")
            .body(JacksonConfig.mapper.writeValueAsString(OppdaterInnleggRequestDto("t", "", "", null, "")))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        assertStatuscodeEquals(HTTP_NOT_FOUND, r, res)
    }

    private fun sampleOpprett() = OpprettInnleggRequestDto(
        tittel = "Tittel",
        opprettetAvPersonNavn = "Ola",
        opprettetAvPersonBeskrivelse = "Veileder",
        sendesTilJobbsokerTidspunkt = ZonedDateTime.now().plusHours(1),
        htmlContent = "<p>x</p>"
    )

    private val UautentifiserendeTestCase.leggPåToken
        get() = this.leggPåToken
}

object TestUtils {
    fun getFreePort(): Int = java.net.ServerSocket(0).use { it.localPort }
}
