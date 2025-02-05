package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.result.Result
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.rekrutteringstreff.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffTest {

    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val appPort = 10000
    private val repo = RekrutteringstreffRepository(database.dataSource) // hmmm

    private val app = App(
        port = appPort,
        repo = RekrutteringstreffRepository(database.dataSource),
        authConfigs = listOf(
            AuthenticationConfiguration(
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
                audience = "rekrutteringstreff-audience"
            )
        )
    )

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
        database.slettAlt()
    }

    @Test
    fun opprettRekrutteringstreff() {
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val gyldigTittelfelt = "Tittelfeltet"
        val gyldigKontorfelt = "Gyldig NAV Kontor"
        val gyldigStatus = Status.Utkast
        val gyldigFraTid = nowOslo().minusDays(1)
        val gyldigTilTid = nowOslo().plusDays(1).plusHours(2)
        val gyldigSted = "Gyldig Sted"
        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff")
            .body(
                """
                {
                    "tittel": "$gyldigTittelfelt",
                    "opprettetAvNavkontorEnhetId": "$gyldigKontorfelt",
                    "fraTid": "$gyldigFraTid",
                    "tilTid": "$gyldigTilTid",
                    "sted": "$gyldigSted"
                }
                """.trimIndent()
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> {
                assertThat(response.statusCode).isEqualTo(201)
                val rekrutteringstreff: List<Rekrutteringstreff> = database.hentAlleRekrutteringstreff()
                assertThat(rekrutteringstreff).hasSize(1)
                rekrutteringstreff[0].apply {
                    assertThat(tittel).isEqualTo(gyldigTittelfelt)
                    assertThat(fraTid).isEqualTo(gyldigFraTid)
                    assertThat(tilTid).isEqualTo(gyldigTilTid)
                    assertThat(sted).isEqualTo(gyldigSted)
                    assertThat(status).isEqualTo(gyldigStatus.name)
                    assertThat(opprettetAvNavkontorEnhetId).isEqualTo(gyldigKontorfelt)
                    assertThat(opprettetAvPersonNavident).isEqualTo(navIdent)
                }
            }
        }
    }


    @Test
    fun hentAlleRekrutteringstreff() {

        val treff1 = OpprettRekrutteringstreffDto(
            tittel = "Tittel1111111",
            fraTid = nowOslo(),
            tilTid = nowOslo(),
            sted = "anySted",
            opprettetAvNavkontorEnhetId = "anyEnhetId"
        )
        val treff2 = OpprettRekrutteringstreffDto(
            tittel = "Tittel222222222222",
            fraTid = nowOslo(),
            tilTid = nowOslo(),
            sted = "anySted",
            opprettetAvNavkontorEnhetId = "anyEnhetId"
        )

        repo.opprett(treff1, "navident1111111")
        repo.opprett(treff2, "navident222222222")

        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)


        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/hentalle")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<List<Rekrutteringstreff>> {
                override fun deserialize(content: String): List<Rekrutteringstreff> =
                    jacksonObjectMapper().readValue(content)
            })

        when (result) {
            is Result.Failure<*> -> throw result.error
            is Result.Success<List<Rekrutteringstreff>> -> {
                assertThat(response.statusCode).isEqualTo(200)
                val liste = result.get()
                assertThat(liste).hasSize(2)
                val returnertTreff1 = liste.first()
                val returnertTreff2 = liste.last()
                assertThat(returnertTreff1.tittel).isEqualTo(treff1.tittel)
                assertThat(returnertTreff2.tittel).isEqualTo(treff2.tittel)
            }
        }
    }


    private fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        navIdent: String = "A000001",
        claims: Map<String, Any> = mapOf("NAVident" to navIdent),
        expiry: Long = 3600
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        claims = claims,
        expiry = expiry,
        audience = "rekrutteringstreff-audience"
    )
}
