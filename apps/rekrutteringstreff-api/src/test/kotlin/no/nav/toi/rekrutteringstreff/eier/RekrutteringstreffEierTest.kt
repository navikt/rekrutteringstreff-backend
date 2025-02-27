package no.nav.toi.rekrutteringstreff.eier


import com.fasterxml.jackson.core.type.TypeReference
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.App
import no.nav.toi.AuthenticationConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import no.nav.toi.ObjectMapperProvider.mapper
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.nowOslo
import no.nav.toi.rekrutteringstreff.OpprettRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import org.junit.Ignore
import java.net.HttpURLConnection.HTTP_CREATED
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffEierTest {


    private val authServer = MockOAuth2Server()
    private val authPort = 18012
    private val database = TestDatabase()
    private val appPort = 10000
    private val repo = RekrutteringstreffRepository(database.dataSource)

    private val app = App(
        port = appPort,
        authConfigs = listOf(
            AuthenticationConfiguration(
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
                audience = "rekrutteringstreff-audience"
            )
        ),
        database.dataSource
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
    fun hentEiere() {
        val navIdent = "A123456"
        val eiere = ('0'..'9').map { "Z99999$it" }
        val token = lagToken(navIdent = navIdent)

        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        database.oppdaterRekrutteringstreff(eiere, opprettetRekrutteringstreff.id)
        val (_, response, result) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> {
                assertThat(response.statusCode).isEqualTo(200)
                val dto = mapper.readValue(result.get(), object : TypeReference<List<String>>() {})
                assertThat(dto).containsExactlyInAnyOrder(*eiere.toTypedArray())
            }
        }
    }

    @Test
    fun leggTilEier() {
        val bruker = "A123456"
        val nyEier = "B654321"
        val token = lagToken(navIdent = bruker)
        opprettRekrutteringstreffIDatabase(bruker)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).doesNotContain(nyEier)

        val (_, updateResponse, updateResult) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .body(mapper.writeValueAsString(listOf(nyEier)))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (updateResult) {
            is Result.Failure -> throw updateResult.error
            is Result.Success -> {
                assertThat(updateResponse.statusCode).isEqualTo(HTTP_CREATED)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).contains(nyEier)
            }
        }
    }

    @Test
    fun leggTilFlereEiereSamtidig() {
        val bruker = "A123456"
        val nyeEiere = listOf("B654321", "C987654")
        val token = lagToken(navIdent = bruker)
        opprettRekrutteringstreffIDatabase(bruker)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).doesNotContainAnyElementsOf(nyeEiere)

        val (_, updateResponse, updateResult) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .body(mapper.writeValueAsString(nyeEiere))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (updateResult) {
            is Result.Failure -> throw updateResult.error
            is Result.Success -> {
                assertThat(updateResponse.statusCode).isEqualTo(HTTP_CREATED)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).containsAll(nyeEiere)
            }
        }
    }

    @Test
    fun ikkeFjernGamleEiereNårManLeggerTilNye() {
        val bruker = "A123456"
        val nyeEiere = listOf("B654321", "C987654")
        val token = lagToken(navIdent = bruker)
        opprettRekrutteringstreffIDatabase(bruker)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).contains(bruker)

        val (_, updateResponse, updateResult) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .body(mapper.writeValueAsString(nyeEiere))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (updateResult) {
            is Result.Failure -> throw updateResult.error
            is Result.Success -> {
                assertThat(updateResponse.statusCode).isEqualTo(HTTP_CREATED)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).contains(bruker)
            }
        }
    }

    @Test
    fun ikkeLeggTilDuplikaterAvEiere() {
        val bruker = "A123456"
        val token = lagToken(navIdent = bruker)
        opprettRekrutteringstreffIDatabase(bruker)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).contains(bruker)

        val (_, updateResponse, updateResult) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere")
            .body(mapper.writeValueAsString(listOf(bruker)))
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        when (updateResult) {
            is Result.Failure -> throw updateResult.error
            is Result.Success -> {
                assertThat(updateResponse.statusCode).isEqualTo(HTTP_CREATED)
                val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
                assertThat(eiere).contains(bruker)
                    .hasSize(1)
            }
        }
    }

    @Test
    fun slettEier() {
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        val (_, deleteResponse, deleteResult) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere/$navIdent")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (deleteResult) {
            is Result.Failure -> throw deleteResult.error
            is Result.Success -> {
                assertThat(deleteResponse.statusCode).isEqualTo(200)
            }
        }
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).doesNotContain(navIdent)
    }

    @Test
    fun slettEierBeholderAndreEiere() {
        val navIdent = "A123456"
        val beholdIdent = "B987654"
        val token = lagToken(navIdent = navIdent)
        opprettRekrutteringstreffIDatabase(navIdent)
        val opprettetRekrutteringstreff = database.hentAlleRekrutteringstreff().first()
        repo.eierRepository.leggTil(opprettetRekrutteringstreff.id, listOf(beholdIdent))
        val (_, deleteResponse, deleteResult) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/${opprettetRekrutteringstreff.id}/eiere/$navIdent")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
        when (deleteResult) {
            is Result.Failure -> throw deleteResult.error
            is Result.Success -> {
                assertThat(deleteResponse.statusCode).isEqualTo(200)
            }
        }
        val eiere = database.hentEiere(opprettetRekrutteringstreff.id)
        assertThat(eiere).contains(beholdIdent)
    }

    private fun opprettRekrutteringstreffIDatabase(
        navIdent: String,
        tittel: String = "Original Tittel",
        sted: String = "Original Sted"
    ) {
        val originalDto = OpprettRekrutteringstreffDto(
            tittel = tittel,
            beskrivelse = "Testbeskrivelse",
            opprettetAvNavkontorEnhetId = "Original Kontor",
            opprettetAvPersonNavident = navIdent,
            opprettetAvTidspunkt = nowOslo().minusDays(10),
            fraTid = nowOslo().minusDays(1),
            tilTid = nowOslo().plusDays(1),
            sted = sted
        )
        repo.opprett(originalDto, navIdent)
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

    @Test
    fun unauthorizedHentEiere() {
        // Bruker et dummy UUID
        val dummyId = UUID.randomUUID().toString()
        val (_, response, _) = Fuel.get("http://localhost:$appPort/api/rekrutteringstreff/$dummyId/eiere")
            .header("Authorization", "Bearer invalidtoken")
            .responseString()
        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    @Ignore
    fun unauthorizedLeggTilEiere() {
        val dummyId = UUID.randomUUID().toString()
        val (_, response, _) = Fuel.put("http://localhost:$appPort/api/rekrutteringstreff/$dummyId/eiere")
            .body(mapper.writeValueAsString("""["A123456"]"""))
            .header("Authorization", "Bearer invalidtoken")
            .responseString()
        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun unauthorizedSlettEier() {
        val dummyId = UUID.randomUUID().toString()
        val navIdent = "A123456"
        val (_, response, _) = Fuel.delete("http://localhost:$appPort/api/rekrutteringstreff/$dummyId/eiere/$navIdent")
            .header("Authorization", "Bearer invalidtoken")
            .responseString()
        assertThat(response.statusCode).isEqualTo(401)
    }
}
