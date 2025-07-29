package no.nav.toi.jobbsoker

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import java.net.HttpURLConnection.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobbsøkerInnloggetBorgerTest {

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
            utvikler = AzureAdRoller.utvikler,
            kandidatsokApiUrl = "",
            kandidatsokScope = "",
            azureClientId = "",
            azureClientSecret = "",
            azureTokenEndpoint = "",
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

    @Test
    fun `svar ja til invitasjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("12345678901")
        val token = authServer.lagTokenBorger(authPort, pid = fnr.asString)

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fnr, null, Fornavn("Test"), Etternavn("Person"), null, null, null)
            )
        )

        assertThat(db.hentJobbsøkerHendelser(treffId)).hasSize(1)

        val requestBody = """
        { "fødselsnummer": "${fnr.asString}" }
    """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/borger/svar-ja")
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
                Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fnr, null, Fornavn("Test"), Etternavn("Person"), null, null, null)
            )
        )

        assertThat(db.hentJobbsøkerHendelser(treffId)).hasSize(1)

        val requestBody = """
        { "fødselsnummer": "${fnr.asString}" }
    """.trimIndent()

        val (_, response, result) = Fuel.post("http://localhost:$appPort/api/rekrutteringstreff/$treffId/jobbsoker/borger/svar-nei")
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
    fun `hentJobbsøkerInnloggetBorger returnerer jobbsøker med alle data`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("11111111111")
        val token = authServer.lagToken(authPort, navIdent = "testperson")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(
            listOf(
                Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fødselsnummer, Kandidatnummer("K1"), Fornavn("Test"), Etternavn("Person"), Navkontor("NAV En"), VeilederNavn("Veileder En"), VeilederNavIdent("V1"))
            )
        )

        val jobbsøkere = db.hentAlleJobbsøkere()
        inviter(jobbsøkere, treffId, token)

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/borger/svar-ja")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${borgerToken.serialize()}")
            .responseString()


        val (_, _, result) = hentJobbsøkerInnloggetBorger(treffId, fødselsnummer, borgerToken)

        val jobbsøker = result.get()
        assertThatCode { UUID.fromString(jobbsøker.personTreffId) }.doesNotThrowAnyException()
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
    fun `hentJobbsøkerInnloggetBorger håndterer status påmeldt`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("11111111111")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        val jobbsøkere = db.hentAlleJobbsøkere()
        inviter(jobbsøkere, treffId, token)

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/borger/svar-ja")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${borgerToken.serialize()}")
            .responseString()

        val (_, _, result) = hentJobbsøkerInnloggetBorger(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().statuser.erPåmeldt).isTrue()
        assertThat(result.get().statuser.erInvitert).isTrue()
    }

    private fun inviter(
        jobbsøkere: List<Jobbsøker>,
        treffId: TreffId,
        token: SignedJWT
    ) {
        val personTreffIder = jobbsøkere.toList().map { it.personTreffId }
        assertThat(personTreffIder).hasSize(1)

        val requestBody = """
            { "personTreffIder": ["${personTreffIder.first()}", "${personTreffIder.last()}"] }
        """.trimIndent()


        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body(requestBody)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()
    }


    @Test
    fun `hentJobbsøkerInnloggetBorger håndterer status avmeldt`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("22222222222")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        val jobbsøkere = db.hentAlleJobbsøkere()
        inviter(jobbsøkere, treffId, token)

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/borger/svar-nei")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${borgerToken.serialize()}")
            .responseString()

        val (_, _, result) = hentJobbsøkerInnloggetBorger(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().statuser.erPåmeldt).isFalse()
        assertThat(result.get().statuser.erInvitert).isTrue()
    }

    @Test
    fun `hentJobbsøkerInnloggetBorger håndterer status kun invitert`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("33333333333")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        val jobbsøkere = db.hentAlleJobbsøkere()
        inviter(jobbsøkere, treffId, token)

        val (_, _, result) = hentJobbsøkerInnloggetBorger(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().statuser.erPåmeldt).isFalse()
        assertThat(result.get().statuser.erInvitert).isTrue()
    }

    @Test
    fun `hentJobbsøkerInnloggetBorger håndterer status ikke invitert`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("44444444444")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        val (_, _, result) = hentJobbsøkerInnloggetBorger(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().statuser.erPåmeldt).isFalse()
        assertThat(result.get().statuser.erInvitert).isFalse()
    }

    @Test
    fun `hentJobbsøkerInnloggetBorger håndterer harSvart når bruker har svart ja`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("11111111111")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body("""{ "fødselsnumre": ["${fødselsnummer.asString}"] }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/borger/svar-ja")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${borgerToken.serialize()}")
            .responseString()

        val (_, _, result) = hentJobbsøkerInnloggetBorger(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().statuser.harSvart).isTrue()
    }

    @Test
    fun `hentJobbsøkerInnloggetBorger håndterer harSvart når bruker har svart nei`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("22222222222")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body("""{ "fødselsnumre": ["${fødselsnummer.asString}"] }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/borger/svar-nei")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${borgerToken.serialize()}")
            .responseString()

        val (_, _, result) = hentJobbsøkerInnloggetBorger(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().statuser.harSvart).isTrue()
    }

    @Test
    fun `hentJobbsøkerInnloggetBorger håndterer harSvart når bruker ikke har svart`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("33333333333")
        val token = authServer.lagToken(authPort, navIdent = "test")
        val borgerToken = authServer.lagTokenBorger(authPort, pid = fødselsnummer.asString)

        db.leggTilJobbsøkere(listOf(Jobbsøker(PersonTreffId(UUID.randomUUID()), treffId, fødselsnummer, null, Fornavn("Test"), Etternavn("Person"), null, null, null)))

        Fuel.post("http://localhost:${appPort}/api/rekrutteringstreff/$treffId/jobbsoker/inviter")
            .body("""{ "fødselsnumre": ["${fødselsnummer.asString}"] }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseString()

        val (_, _, result) = hentJobbsøkerInnloggetBorger(treffId, fødselsnummer, borgerToken)
        assertThat(result.get().statuser.harSvart).isFalse()
    }

    @Test
    fun `hentJobbsøkerInnloggetBorger returnerer 404 for ukjent jobbsøker`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fnr = Fødselsnummer("44444444444")
        val token = authServer.lagTokenBorger(authPort, pid = fnr.asString)

        val (_, response, _) = hentJobbsøkerInnloggetBorger(treffId, Fødselsnummer("99999999999"), token)
        assertThat(response.statusCode).isEqualTo(HTTP_NOT_FOUND)
    }

    private fun hentJobbsøkerInnloggetBorger(treffId: TreffId, fødselsnummer: Fødselsnummer, token: com.nimbusds.jwt.SignedJWT) =
        Fuel.get("http://localhost:${appPort}/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/borger")
            .body("""{ "fødselsnummer": "${fødselsnummer.asString}" }""")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject(object : ResponseDeserializable<JobbsøkerMedStatuserOutboundDto> {
                override fun deserialize(content: String) =
                    mapper.readValue(content, JobbsøkerMedStatuserOutboundDto::class.java)
            })


}
