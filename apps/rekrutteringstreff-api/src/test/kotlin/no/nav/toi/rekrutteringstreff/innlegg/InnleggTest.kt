package no.nav.toi.rekrutteringstreff.innlegg

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.TestDatabase
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
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()

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
    fun autentiseringOpprettInnlegg(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val (_, resp, res) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, resp, res)
    }

    @ParameterizedTest
    @MethodSource("tokenVarianter")
    fun autentiseringHentInnlegg(tc: UautentifiserendeTestCase) {
        val leggPåToken = tc.leggPåToken
        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${UUID.randomUUID()}/innlegg")
            .leggPåToken(authServer, authPort)
            .responseString()
        assertStatuscodeEquals(HTTP_UNAUTHORIZED, resp, res)
    }


    @Test
    fun leggTilInnlegg() {
        val token   = authServer.lagToken(authPort, navIdent = "A123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()

        val body = OpprettInnleggRequestDto(
            "Tittel", "A123456", "Ola Nordmann", "Veileder", null, "<p>Innhold</p>"
        )

        val (_, resp, res) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg")
            .body(JacksonConfig.mapper.writeValueAsString(body))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_CREATED, resp, res)
        assertThat(InnleggRepository(db.dataSource).hentForTreff(treffId)).hasSize(1)
    }


    @Test
    fun hentAlleInnlegg() {
        val token   = authServer.lagToken(authPort, navIdent = "B123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()

        val repo = InnleggRepository(db.dataSource)
        repo.opprett(treffId, OpprettInnleggRequestDto("T1","B","","",null,"<p/>"))
        repo.opprett(treffId, OpprettInnleggRequestDto("T2","B","","",null,"<p/>"))

        val (_, resp, res) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggListeDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> throw res.error
            is Success -> assertThat(res.value).hasSize(2)
        }
    }


    @Test
    fun oppdaterInnlegg() {
        val token   = authServer.lagToken(authPort, navIdent = "C123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()

        val repo = InnleggRepository(db.dataSource)
        val lagret = repo.opprett(treffId, OpprettInnleggRequestDto("Gammel","C","","",null,"<p/>"))

        val patch = OpprettInnleggRequestDto("Ny","C","Ola","Veileder",null,"<p>Ny</p>")

        val (_, resp, res) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/${lagret.id}")
            .body(JacksonConfig.mapper.writeValueAsString(patch))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(InnleggDtoDeserializer)

        assertStatuscodeEquals(HTTP_OK, resp, res)
        when (res) {
            is Failure -> throw res.error
            is Success -> assertThat(res.value.tittel).isEqualTo("Ny")
        }
    }


    @Test
    fun slettInnlegg() {
        val token   = authServer.lagToken(authPort, navIdent = "D123456")
        val treffId = db.opprettRekrutteringstreffIDatabase()

        val repo = InnleggRepository(db.dataSource)
        val innlegg = repo.opprett(treffId, OpprettInnleggRequestDto("Slett","D","","",null,"<p/>"))

        val (_, resp, res) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${treffId.somUuid}/innlegg/${innlegg.id}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        assertStatuscodeEquals(HTTP_NO_CONTENT, resp, res)
        assertThat(repo.hentById(innlegg.id)).isNull()
    }
}
