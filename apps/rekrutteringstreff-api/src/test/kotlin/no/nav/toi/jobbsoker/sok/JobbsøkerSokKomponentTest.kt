package no.nav.toi.jobbsoker.sok

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Timestamp
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class JobbsøkerSokKomponentTest {

    companion object {
        private val db = TestDatabase()
        private val appPort = ubruktPortnrFra10000.ubruktPortnr()
        private val mapper = JacksonConfig.mapper

        private lateinit var infra: TestInfrastructureContext

        private lateinit var ctx: ApplicationContext
        private lateinit var app: App
    }

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        infra = TestInfrastructureContext(dataSource = db.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl).also { it.start() }
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
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
        infra.stop()
        app.close()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    private fun opprettTreffMedEier(navIdent: String = "A123456"): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")
        ctx.eierRepository.leggTil(treffId, listOf(navIdent))
        return treffId
    }

    private fun httpPost(path: String, body: String, navIdent: String = "A123456"): HttpResponse<String> {
        val token = infra.authServer.lagToken(infra.authPort, navIdent = navIdent)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$appPort$path"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun søkPath(treffId: TreffId): String =
        "/api/rekrutteringstreff/${treffId.somUuid}/jobbsoker/sok"

    private fun søkBody(vararg felter: Pair<String, Any>): String {
        val map = mutableMapOf<String, Any>(
            "side" to 1,
            "antallPerSide" to 20,
        )
        felter.forEach { (key, value) -> map[key] = value }
        return mapper.writeValueAsString(map)
    }

    private fun søk(treffId: TreffId, vararg felter: Pair<String, Any>): JobbsøkerSøkRespons =
        mapper.readValue(httpPost(søkPath(treffId), søkBody(*felter)).body())

    private fun jobbsøker(
        fødselsnummer: String,
        fornavn: String,
        etternavn: String,
        kontor: Kontor? = null,
        veilederNavn: VeilederNavn? = null,
        veilederNavIdent: VeilederNavIdent? = null,
        alder: Int? = null,
    ) = LeggTilJobbsøker(Fødselsnummer(fødselsnummer), Fornavn(fornavn), Etternavn(etternavn), kontor, veilederNavn, veilederNavIdent, alder)

    private fun leggTilJobbsøkere(treffId: TreffId, vararg jobbsøkere: LeggTilJobbsøker) =
        db.leggTilJobbsøkereMedHendelse(jobbsøkere.toList(), treffId)

    private fun oppdaterLagtTilDato(personTreffId: PersonTreffId, lagtTilDato: Instant) {
        db.dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE jobbsoker_hendelse
                SET tidspunkt = ?
                WHERE jobbsoker_id = (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?)
                  AND hendelsestype = 'OPPRETTET'
                """.trimIndent()
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(lagtTilDato))
                stmt.setObject(2, personTreffId.somUuid)
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `søk uten filtre returnerer paginert respons`() {
        val treffId = opprettTreffMedEier()
        val js1 = jobbsøker("11111111111", "Ola", "Nordmann", Kontor(kontornummer = "1000", kontornavn = "NAV Oslo"), VeilederNavn("Veil1"), VeilederNavIdent("NAV001"))
        val js2 = jobbsøker("22222222222", "Kari", "Hansen", Kontor(kontornummer = "1000", kontornavn = "NAV Bergen"), VeilederNavn("Veil2"), VeilederNavIdent("NAV002"))
        leggTilJobbsøkere(treffId, js1, js2)

        val response = httpPost(søkPath(treffId), søkBody())
        assertThat(response.statusCode()).isEqualTo(200)

        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        assertThat(dto.totalt).isEqualTo(2)
        assertThat(dto.side).isEqualTo(1)
        assertThat(dto.jobbsøkere).hasSize(2)
        assertThat(dto.jobbsøkere.map { it.fødselsnummer }).containsExactlyInAnyOrder("11111111111", "22222222222")
    }

    @Test
    fun `fritekst-søk filtrerer på navn`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann"),
            jobbsøker("22222222222", "Kari", "Hansen"),
            jobbsøker("33333333333", "Per", "Olsen"),
        )

        val dto = søk(treffId, "fritekst" to "ola")

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Ola")
    }

    @Test
    fun `fritekst-søk gjør prefiks-match på fødselsnummer`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("10120098765", "Ola", "Nordmann"),
            jobbsøker("10120012345", "Kari", "Hansen"),
            jobbsøker("25060011111", "Per", "Olsen"),
        )

        val delvis = søk(treffId, "fritekst" to "101200")
        assertThat(delvis.totalt).isEqualTo(2)
        assertThat(delvis.jobbsøkere.map { it.fødselsnummer })
            .containsExactlyInAnyOrder("10120098765", "10120012345")

        val fullt = søk(treffId, "fritekst" to "10120098765")
        assertThat(fullt.totalt).isEqualTo(1)
        assertThat(fullt.jobbsøkere.single().fødselsnummer).isEqualTo("10120098765")
    }

    @Test
    fun `fritekst-søk filtrerer kun på navn`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann", Kontor(kontornummer = "1000", kontornavn = "KontorAlpha"), VeilederNavn("Vera Veileder"), VeilederNavIdent("NAV001")),
            jobbsøker("22222222222", "Kari", "Hansen", Kontor(kontornummer = "1000", kontornavn = "KontorBeta"), VeilederNavn("Per Person"), VeilederNavIdent("NAV002")),
        )

        val kontorTreff = søk(treffId, "fritekst" to "alpha")
        val veilederTreff = søk(treffId, "fritekst" to "nav002")

        assertThat(kontorTreff.jobbsøkere).isEmpty()
        assertThat(veilederTreff.jobbsøkere).isEmpty()
    }

    @Test
    fun `statusfilter returnerer kun jobbsøkere med gitt status`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann"),
            jobbsøker("22222222222", "Kari", "Hansen"),
        )
        db.inviterJobbsøkere(listOf(personTreffIder[0]), treffId)

        val dto = søk(treffId, "status" to listOf("INVITERT"))

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().status).isEqualTo(JobbsøkerStatus.INVITERT)
    }

    @Test
    fun `kombinerte filtre fungerer sammen`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann", Kontor(kontornummer = "1000", kontornavn = "Nav Oslo")),
            jobbsøker("22222222222", "Kari", "Hansen", Kontor(kontornummer = "1000", kontornavn = "Nav Bergen")),
            jobbsøker("33333333333", "Per", "Olsen", Kontor(kontornummer = "1000", kontornavn = "Nav Oslo")),
        )
        db.inviterJobbsøkere(listOf(personTreffIder[0]), treffId)

        val dto = søk(treffId, "status" to listOf("INVITERT"))

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Ola")
    }

    @Test
    fun `paginering fungerer korrekt`() {
        val treffId = opprettTreffMedEier()
        val jobbsøkere = (1..5).map { i ->
            jobbsøker("${i}1111111111".take(11), "Person$i", "Etternavn$i")
        }
        leggTilJobbsøkere(treffId, *jobbsøkere.toTypedArray())

        val side1 = søk(treffId, "side" to 1, "antallPerSide" to 2)
        assertThat(side1.totalt).isEqualTo(5)
        assertThat(side1.side).isEqualTo(1)
        assertThat(side1.jobbsøkere).hasSize(2)

        val side2 = søk(treffId, "side" to 2, "antallPerSide" to 2)
        assertThat(side2.jobbsøkere).hasSize(2)

        val side3 = søk(treffId, "side" to 3, "antallPerSide" to 2)
        assertThat(side3.jobbsøkere).hasSize(1)
    }

    @Test
    fun `ugyldig høy side clampes til siste gyldige side`() {
        val treffId = opprettTreffMedEier()
        val jobbsøkere = (1..5).map { i ->
            jobbsøker("${i}2222222222".take(11), "Person$i", "Etternavn$i")
        }
        leggTilJobbsøkere(treffId, *jobbsøkere.toTypedArray())

        val dto = søk(treffId, "side" to 4, "antallPerSide" to 2)

        assertThat(dto.totalt).isEqualTo(5)
        assertThat(dto.side).isEqualTo(3)
        assertThat(dto.jobbsøkere).hasSize(1)
        assertThat(dto.jobbsøkere.single().fornavn).isEqualTo("Person5")
    }

    @Test
    fun `ugyldig høy side clampes etter at skjulte og slettede er filtrert bort`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(
            treffId,
            *((1..5).map { i ->
                jobbsøker("${i}3333333333".take(11), "Person$i", "Etternavn$i")
            }).toTypedArray(),
        )
        db.settSynlighet(personTreffIder[3], false)
        db.settJobbsøkerStatus(personTreffIder[4], JobbsøkerStatus.SLETTET)

        val dto = søk(treffId, "side" to 3, "antallPerSide" to 2)

        assertThat(dto.totalt).isEqualTo(3)
        assertThat(dto.antallSkjulte).isEqualTo(1)
        assertThat(dto.antallSlettede).isEqualTo(1)
        assertThat(dto.side).isEqualTo(2)
        assertThat(dto.jobbsøkere).hasSize(1)
    }

    @Test
    fun `sperret og slettet jobbsøker telles som slettet, ikke som skjult`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(
            treffId,
            *((1..3).map { i ->
                jobbsøker("${i}3333333333".take(11), "Person$i", "Etternavn$i")
            }).toTypedArray(),
        )
        db.settSperret(personTreffIder[1], true)
        db.settSynlighet(personTreffIder[1], false)
        db.settSperret(personTreffIder[2], true)
        db.settSynlighet(personTreffIder[2], false)
        db.settJobbsøkerStatus(personTreffIder[2], JobbsøkerStatus.SLETTET)

        val dto = søk(treffId)

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.antallSkjulte).isEqualTo(1)
        assertThat(dto.antallSlettede).isEqualTo(1)
        assertThat(dto.jobbsøkere.map { it.fødselsnummer }).containsExactly("13333333333")
    }

    @Test
    fun `sortering på navn gir alfabetisk rekkefølge`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Charlie", "C"),
            jobbsøker("22222222222", "Alice", "A"),
            jobbsøker("33333333333", "Bob", "B"),
        )

        val dto = søk(treffId, "sortering" to "navn")

        assertThat(dto.jobbsøkere.map { it.fornavn }).containsExactly("Alice", "Bob", "Charlie")
    }

    @Test
    fun `sortering på navn støtter synkende rekkefølge`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Charlie", "C"),
            jobbsøker("22222222222", "Alice", "A"),
            jobbsøker("33333333333", "Bob", "B"),
        )

        val dto = søk(treffId, "sortering" to "navn", "retning" to "desc")

        assertThat(dto.jobbsøkere.map { it.fornavn }).containsExactly("Charlie", "Bob", "Alice")
    }

    @Test
    fun `sortering på lagt til støtter stigende og synkende rekkefølge`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Charlie", "C"),
            jobbsøker("22222222222", "Alice", "A"),
            jobbsøker("33333333333", "Bob", "B"),
        )

        oppdaterLagtTilDato(personTreffIder[0], Instant.parse("2026-03-01T12:00:00Z"))
        oppdaterLagtTilDato(personTreffIder[1], Instant.parse("2026-01-01T12:00:00Z"))
        oppdaterLagtTilDato(personTreffIder[2], Instant.parse("2026-02-01T12:00:00Z"))

        val stigendeDto = søk(treffId, "sortering" to "lagt-til", "retning" to "asc")
        val synkendeDto = søk(treffId, "sortering" to "lagt-til", "retning" to "desc")

        assertThat(stigendeDto.jobbsøkere.map { it.fornavn }).containsExactly("Alice", "Bob", "Charlie")
        assertThat(synkendeDto.jobbsøkere.map { it.fornavn }).containsExactly("Charlie", "Bob", "Alice")
    }

    @Test
    fun `sortering på lagt til bruker klokkeslett innenfor samme dato`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("44444444444", "Charlie", "C"),
            jobbsøker("55555555555", "Alice", "A"),
            jobbsøker("66666666666", "Bob", "B"),
        )

        oppdaterLagtTilDato(personTreffIder[0], Instant.parse("2026-03-01T12:00:03Z"))
        oppdaterLagtTilDato(personTreffIder[1], Instant.parse("2026-03-01T12:00:01Z"))
        oppdaterLagtTilDato(personTreffIder[2], Instant.parse("2026-03-01T12:00:02Z"))

        val stigendeDto = søk(treffId, "sortering" to "lagt-til", "retning" to "asc")
        val synkendeDto = søk(treffId, "sortering" to "lagt-til", "retning" to "desc")

        assertThat(stigendeDto.jobbsøkere.map { it.fornavn }).containsExactly("Alice", "Bob", "Charlie")
        assertThat(synkendeDto.jobbsøkere.map { it.fornavn }).containsExactly("Charlie", "Bob", "Alice")
    }

    @Test
    fun `sortering på status støtter stigende rekkefølge`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("77777777771", "LagtTil", "A"),
            jobbsøker("77777777772", "Invitert", "B"),
            jobbsøker("77777777773", "SvartNei", "C"),
            jobbsøker("77777777774", "SvartJa", "D"),
        )
        db.inviterJobbsøkere(listOf(personTreffIder[1], personTreffIder[2], personTreffIder[3]), treffId)
        db.settJobbsøkerStatus(personTreffIder[2], JobbsøkerStatus.SVART_NEI)
        db.settJobbsøkerStatus(personTreffIder[3], JobbsøkerStatus.SVART_JA)

        val dto = søk(treffId, "sortering" to "status", "retning" to "asc")

        assertThat(dto.jobbsøkere.map { it.status }).containsExactly(
            JobbsøkerStatus.SVART_JA,
            JobbsøkerStatus.SVART_NEI,
            JobbsøkerStatus.INVITERT,
            JobbsøkerStatus.LAGT_TIL,
        )
    }

    @Test
    fun `sortering på status støtter synkende rekkefølge`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("77777777781", "LagtTil", "A"),
            jobbsøker("77777777782", "Invitert", "B"),
            jobbsøker("77777777783", "SvartNei", "C"),
            jobbsøker("77777777784", "SvartJa", "D"),
        )
        db.inviterJobbsøkere(listOf(personTreffIder[1], personTreffIder[2], personTreffIder[3]), treffId)
        db.settJobbsøkerStatus(personTreffIder[2], JobbsøkerStatus.SVART_NEI)
        db.settJobbsøkerStatus(personTreffIder[3], JobbsøkerStatus.SVART_JA)

        val dto = søk(treffId, "sortering" to "status", "retning" to "desc")

        assertThat(dto.jobbsøkere.map { it.status }).containsExactly(
            JobbsøkerStatus.LAGT_TIL,
            JobbsøkerStatus.INVITERT,
            JobbsøkerStatus.SVART_NEI,
            JobbsøkerStatus.SVART_JA,
        )
    }

    @Test
    fun `sortering på alder gir stigende rekkefølge`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("22222222222", "Alice", "A", alder = 40),
            jobbsøker("33333333333", "Bob", "B", alder = 25),
            jobbsøker("11111111111", "Charlie", "C", alder = 30),
            )

        val dto = søk(treffId, "sortering" to "alder")

        assertThat(dto.jobbsøkere.map { it.alder }).containsExactly(25, 30, 40)
    }

    @Test
    fun `sortering på alder støtter synkende rekkefølge`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("22222222222", "Alice", "A", alder = 40),
            jobbsøker("33333333333", "Bob", "B", alder = 25),
            jobbsøker("11111111111", "Charlie", "C", alder = 30),
        )

        val dto = søk(treffId, "sortering" to "alder", "retning" to "desc")

        assertThat(dto.jobbsøkere.map { it.alder }).containsExactly(40, 30, 25)
    }

    @Test
    fun `sortering på alder plasserer ukjent alder sist uansett retning`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("22222222222", "Alice", "A", alder = 40),
            jobbsøker("33333333333", "Bob", "B", alder = null),
            jobbsøker("11111111111", "Charlie", "C", alder = 30),
        )

        val stigende = søk(treffId, "sortering" to "alder", "retning" to "asc")
        val synkende = søk(treffId, "sortering" to "alder", "retning" to "desc")

        assertThat(stigende.jobbsøkere.map { it.alder }).containsExactly(30, 40, 0)
        assertThat(synkende.jobbsøkere.map { it.alder }).containsExactly(40, 30, 0)
    }

    @Test
    fun `tomt resultatsett returnerer tom liste og totalt 0`() {
        val treffId = opprettTreffMedEier()

        val dto = søk(treffId, "fritekst" to "finnesikke")

        assertThat(dto.totalt).isEqualTo(0)
        assertThat(dto.jobbsøkere).isEmpty()
    }

    @Test
    fun `fritekst-søk med fødselsnummer returnerer treff`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann", Kontor(kontornummer = "1000", kontornavn = "NAV Oslo")),
            jobbsøker("22222222222", "Kari", "Hansen"),
        )

        val dto = søk(treffId, "fritekst" to "11111111111")

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fødselsnummer).isEqualTo("11111111111")
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Ola")
    }

    @Test
    fun `fritekst-søk med ukjent fødselsnummer returnerer tomt resultat`() {
        val treffId = opprettTreffMedEier()

        val dto = søk(treffId, "fritekst" to "99999999999")

        assertThat(dto.totalt).isEqualTo(0)
        assertThat(dto.jobbsøkere).isEmpty()
    }

    @Test
    fun `ikke-eier får 403 på søk`() {
        val treffId = opprettTreffMedEier("A123456")

        val response = httpPost(søkPath(treffId), søkBody(), navIdent = "B999999")

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `slettede jobbsøkere ekskluderes fra søkeresultat`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Aktiv", "Person"),
            jobbsøker("22222222222", "Slettet", "Person"),
        )
        db.settJobbsøkerStatus(personTreffIder[1], JobbsøkerStatus.SLETTET)

        val dto = søk(treffId)

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Aktiv")
    }

    @Test
    fun `ikke-synlige jobbsøkere ekskluderes fra søkeresultat`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Synlig", "Person"),
            jobbsøker("22222222222", "Skjult", "Person"),
        )
        db.settSynlighet(personTreffIder[1], false)

        val dto = søk(treffId)

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.jobbsøkere.first().fornavn).isEqualTo("Synlig")
    }

    @Test
    fun `ugyldig antallPerSide gir 400`() {
        val treffId = opprettTreffMedEier()

        val response = httpPost(søkPath(treffId), søkBody("antallPerSide" to 200))
        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `manglende side og antallPerSide bruker standard paginering`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann"),
        )

        val response = httpPost(søkPath(treffId), søkBody())

        assertThat(response.statusCode()).isEqualTo(200)
        val dto = mapper.readValue<JobbsøkerSøkRespons>(response.body())
        assertThat(dto.side).isEqualTo(1)
        assertThat(dto.totalt).isEqualTo(1)
    }

    @Test
    fun `søk returnerer kun jobbsøkere for riktig treff`() {
        val treff1 = opprettTreffMedEier()
        val treff2 = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456", tittel = "AnnetTreff")
        ctx.eierRepository.leggTil(treff2, listOf("A123456"))

        leggTilJobbsøkere(treff1,
            jobbsøker("11111111111", "Treff1Person", "A"),
        )
        leggTilJobbsøkere(treff2,
            jobbsøker("22222222222", "Treff2Person", "B"),
        )

        val dto1 = søk(treff1)
        assertThat(dto1.totalt).isEqualTo(1)
        assertThat(dto1.jobbsøkere.first().fornavn).isEqualTo("Treff1Person")

        val dto2 = søk(treff2)
        assertThat(dto2.totalt).isEqualTo(1)
        assertThat(dto2.jobbsøkere.first().fornavn).isEqualTo("Treff2Person")
    }

    @Test
    fun `søk fungerer med null-felter på eldre data`() {
        val treffId = opprettTreffMedEier()
        leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Uten", "Data"),
        )

        val dto = søk(treffId)

        assertThat(dto.totalt).isEqualTo(1)
    }

    @Test
    fun `søk inkluderer minsideHendelser for jobbsøkere med MOTTATT_SVAR_FRA_MINSIDE`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Med", "Hendelse"),
            jobbsøker("22222222222", "Uten", "Hendelse"),
        )

        db.leggTilMinsideHendelse(personTreffIder[0], """{"varselId":"v1","eksternKanal":"SMS","eksternStatus":"SENDT","minsideStatus":"OPPRETTET"}""")

        val dto = søk(treffId, "sortering" to "navn")

        assertThat(dto.jobbsøkere).hasSize(2)
        val medHendelse = dto.jobbsøkere.first { it.fornavn == "Med" }
        val utenHendelse = dto.jobbsøkere.first { it.fornavn == "Uten" }

        assertThat(medHendelse.minsideHendelser).hasSize(1)
        assertThat(medHendelse.minsideHendelser.first().hendelsestype).isEqualTo("MOTTATT_SVAR_FRA_MINSIDE")

        assertThat(utenHendelse.minsideHendelser).isEmpty()
    }

    @Test
    fun `søk inkluderer flere minsideHendelser per jobbsøker`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann"),
        )

        db.leggTilMinsideHendelse(personTreffIder[0], """{"varselId":"v1","eksternKanal":"SMS","eksternStatus":"SENDT"}""")
        db.leggTilMinsideHendelse(personTreffIder[0], """{"varselId":"v2","eksternKanal":null,"eksternStatus":"SENDT","minsideStatus":"OPPRETTET"}""")

        val dto = søk(treffId)

        assertThat(dto.jobbsøkere).hasSize(1)
        assertThat(dto.jobbsøkere.first().minsideHendelser).hasSize(2)
    }

    @Test
    fun `minsideHendelser inkluderer hendelseData som json`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann"),
        )

        val hendelseJson = """{"varselId":"v1","eksternKanal":"SMS","eksternStatus":"SENDT","minsideStatus":"OPPRETTET","eksternFeilmelding":null}"""
        db.leggTilMinsideHendelse(personTreffIder[0], hendelseJson)

        val dto = søk(treffId)

        val hendelse = dto.jobbsøkere.first().minsideHendelser.first()
        val hendelseDataNode = mapper.readTree(mapper.writeValueAsString(hendelse)).get("hendelseData")
        assertThat(hendelseDataNode.get("varselId").asText()).isEqualTo("v1")
        assertThat(hendelseDataNode.get("eksternKanal").asText()).isEqualTo("SMS")
        assertThat(hendelseDataNode.get("eksternStatus").asText()).isEqualTo("SENDT")
    }

    @Test
    fun `minsideHendelser returneres ikke for andre hendelsetyper`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann"),
        )
        db.inviterJobbsøkere(listOf(personTreffIder[0]), treffId)

        val dto = søk(treffId)

        assertThat(dto.jobbsøkere.first().minsideHendelser).isEmpty()
    }

    @Test
    fun `minsideHendelser isoleres per treff ved paginering`() {
        val treffId = opprettTreffMedEier()
        val jobbsøkere = (1..3).map { i ->
            jobbsøker("${i}1111111111".take(11), "Person$i", "Etternavn$i")
        }
        val personTreffIder = leggTilJobbsøkere(treffId, *jobbsøkere.toTypedArray())

        db.leggTilMinsideHendelse(personTreffIder[0], """{"varselId":"v1","eksternKanal":"SMS","eksternStatus":"SENDT"}""")
        db.leggTilMinsideHendelse(personTreffIder[2], """{"varselId":"v2","eksternKanal":null,"eksternStatus":"SENDT","minsideStatus":"OPPRETTET"}""")

        val side1 = søk(treffId, "side" to 1, "antallPerSide" to 2, "sortering" to "navn")
        assertThat(side1.jobbsøkere).hasSize(2)

        val side2 = søk(treffId, "side" to 2, "antallPerSide" to 2, "sortering" to "navn")
        assertThat(side2.jobbsøkere).hasSize(1)

        val alleMedHendelser = (side1.jobbsøkere + side2.jobbsøkere).filter { it.minsideHendelser.isNotEmpty() }
        assertThat(alleMedHendelser).hasSize(2)
    }

    @Test
    fun `antallPerStatus returnerer riktige tall for alle statuser`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "A"),
            jobbsøker("22222222222", "Kari", "B"),
            jobbsøker("33333333333", "Per", "C"),
            jobbsøker("44444444444", "Lise", "D"),
        )
        db.inviterJobbsøkere(listOf(personTreffIder[1], personTreffIder[2]), treffId)
        db.settJobbsøkerStatus(personTreffIder[2], JobbsøkerStatus.SVART_JA)

        val dto = søk(treffId)

        assertThat(dto.antallPerStatus[JobbsøkerStatus.LAGT_TIL]).isEqualTo(2)
        assertThat(dto.antallPerStatus[JobbsøkerStatus.INVITERT]).isEqualTo(1)
        assertThat(dto.antallPerStatus[JobbsøkerStatus.SVART_JA]).isEqualTo(1)
        assertThat(dto.antallPerStatus[JobbsøkerStatus.SVART_NEI]).isNull()
    }

    @Test
    fun `antallPerStatus er ufiltrert selv med statusfilter aktivt`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "A"),
            jobbsøker("22222222222", "Kari", "B"),
            jobbsøker("33333333333", "Per", "C"),
        )
        db.inviterJobbsøkere(listOf(personTreffIder[1]), treffId)

        val dto = søk(treffId, "status" to listOf("INVITERT"))

        assertThat(dto.totalt).isEqualTo(1)
        assertThat(dto.antallPerStatus[JobbsøkerStatus.LAGT_TIL]).isEqualTo(2)
        assertThat(dto.antallPerStatus[JobbsøkerStatus.INVITERT]).isEqualTo(1)
    }

    @Test
    fun `antallPerStatus ekskluderer skjulte og slettede jobbsøkere`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Synlig", "A"),
            jobbsøker("22222222222", "Skjult", "B"),
            jobbsøker("33333333333", "Slettet", "C"),
        )
        db.settSynlighet(personTreffIder[1], false)
        db.settJobbsøkerStatus(personTreffIder[2], JobbsøkerStatus.SLETTET)

        val dto = søk(treffId)

        assertThat(dto.antallPerStatus[JobbsøkerStatus.LAGT_TIL]).isEqualTo(1)
        assertThat(dto.antallPerStatus[JobbsøkerStatus.SLETTET]).isNull()
    }

    @Test
    fun `antallPerStatus filtreres av fritekst men ikke av statusfilter`() {
        val treffId = opprettTreffMedEier()
        val personTreffIder = leggTilJobbsøkere(treffId,
            jobbsøker("11111111111", "Ola", "Nordmann"),
            jobbsøker("22222222222", "Kari", "Hansen"),
            jobbsøker("33333333333", "Ola", "Hansen"),
        )
        db.inviterJobbsøkere(listOf(personTreffIder[2]), treffId)

        val dto = søk(treffId, "fritekst" to "ola")

        assertThat(dto.totalt).isEqualTo(2)
        assertThat(dto.antallPerStatus[JobbsøkerStatus.LAGT_TIL]).isEqualTo(1)
        assertThat(dto.antallPerStatus[JobbsøkerStatus.INVITERT]).isEqualTo(1)
    }

    @Test
    fun `jobbsøkersøk returnerer null lagtTilAvNavn for eldre opprettet-hendelser uten navn`() {
        val treffId = opprettTreffMedEier()
        db.leggTilJobbsøkereMedHendelse(
            listOf(jobbsøker("11111111111", "Ola", "Nordmann")),
            treffId,
            opprettetAv = "Z123456",
        )

        val dto = søk(treffId)

        assertThat(dto.jobbsøkere).hasSize(1)
        assertThat(dto.jobbsøkere.single().lagtTilAv).isEqualTo("Z123456")
        assertThat(dto.jobbsøkere.single().lagtTilAvNavn).isNull()
    }
}
