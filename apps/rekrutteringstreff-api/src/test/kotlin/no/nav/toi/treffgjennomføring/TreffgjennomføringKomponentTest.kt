package no.nav.toi.treffgjennomføring

import com.fasterxml.jackson.databind.JsonNode
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.*
import no.nav.toi.AzureAdRoller.arbeidsgiverrettet
import no.nav.toi.AzureAdRoller.jobbsøkerrettet
import no.nav.toi.AzureAdRoller.utvikler
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomføring.matching.ArbeidsgiverIntervjufordeling
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class TreffgjennomføringKomponentTest {

    private val db = TestDatabase()
    private val appPort = ubruktPortnrFra10000.ubruktPortnr()
    private val mapper = JacksonConfig.mapper
    private val dataSource = object : DataSource by db.dataSource {
        override fun getConnection(): Connection = db.dataSource.connection.apply {
            transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        }
    }

    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    private val eier = "A100001"
    private val ikkeEier = "A200002"

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        infra = TestInfrastructureContext(dataSource = dataSource, modiaKlientUrl = wmInfo.httpBaseUrl)
            .also { it.start() }
        ctx = ApplicationContext(infra)
        app = App(ctx = ctx, port = appPort).also { it.start() }
    }

    @BeforeEach
    fun stubModia() {
        stubFor(
            get(urlPathEqualTo("/api/context/v2/aktivenhet")).willReturn(
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

    @Test
    fun `eier får hele aggregatet, ikke-eier får 403`() {
        val treff = workOpTreff()

        assertThat(hent(treff, eier).statusCode()).isEqualTo(200)
        assertThat(hent(treff, ikkeEier).statusCode()).isEqualTo(403)
    }

    @Test
    fun `utvikler får tilgang uten å være eier`() {
        val treff = workOpTreff()

        val respons = hent(treff, ikkeEier, listOf(arbeidsgiverrettet, utvikler))

        assertThat(respons.statusCode()).isEqualTo(200)
    }

    @Test
    fun `jobbsøkerrettet rolle alene gir ikke tilgang`() {
        val treff = workOpTreff()

        val respons = hent(treff, eier, listOf(jobbsøkerrettet))

        assertThat(respons.statusCode()).isEqualTo(403)
    }

    @Test
    fun `lesing har ingen sideeffekt - tomt aggregat og fortsatt ingen lagret rad`() {
        val treff = workOpTreff()

        val første = aggregat(treff)

        assertThat(første["gjeldendeSteg"].asText()).isEqualTo("OPPMØTE")
        assertThat(første["starttidspunkt"].asText()).isEqualTo("10:00")
        assertThat(første["varighetPerMøteMinutter"].asInt()).isEqualTo(10)
        assertThat(første["oppmøte"]).isEmpty()
        assertThat(første["rom"]).isEmpty()
        assertThat(antallTreffgjennomføringsrader()).isZero()

        hent(treff, eier)
        assertThat(antallTreffgjennomføringsrader()).isZero()
    }

    @Test
    fun `antall rom følger antall arbeidsgivere, og er minst 1`() {
        val utenArbeidsgivere = workOpTreff(antallArbeidsgivere = 0)
        val medTre = workOpTreff(antallArbeidsgivere = 3)

        assertThat(aggregat(utenArbeidsgivere)["antallRom"].asInt()).isEqualTo(1)
        assertThat(aggregat(medTre)["antallRom"].asInt()).isEqualTo(3)
    }

    @Test
    fun `oppmøte kan registreres, angres og registreres igjen`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)

        assertThat(oppmøte(treff, person, møtt = true).statusCode()).isEqualTo(200)
        assertThat(oppmøteliste(treff)).containsExactly(person.somString)

        oppmøte(treff, person, møtt = false)
        assertThat(oppmøteliste(treff)).isEmpty()

        oppmøte(treff, person, møtt = true)
        assertThat(oppmøteliste(treff)).containsExactly(person.somString)
    }

    @Test
    fun `gjentatt registrering av samme oppmøte gir ingen ny hendelse`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)

        oppmøte(treff, person, møtt = true)
        oppmøte(treff, person, møtt = true)

        assertThat(antallHendelser(treff, "REGISTRERT_OPPMØTE")).isEqualTo(1)
    }

    @Test
    fun `deltakernummer deles ut på WorkOp, gjenbrukes av samme person og aldri av andre`() {
        val treff = workOpTreff()
        val første = jobbsøker(treff, "11111111111")
        val andre = jobbsøker(treff, "22222222222")

        oppmøte(treff, første, møtt = true)
        assertThat(deltakernummer(treff)[første.somString]).isEqualTo(1)

        oppmøte(treff, første, møtt = false)
        oppmøte(treff, andre, møtt = true)
        assertThat(deltakernummer(treff)[andre.somString]).isEqualTo(2)

        // Samme person får tilbake sitt opprinnelige nummer — kortet er allerede delt ut.
        oppmøte(treff, første, møtt = true)
        assertThat(deltakernummer(treff)[første.somString]).isEqualTo(1)
    }

    @Test
    fun `vanlig treff får ikke deltakernummer`() {
        val treff = vanligTreff()
        val person = jobbsøker(treff)

        oppmøte(treff, person, møtt = true)

        assertThat(oppmøteliste(treff)).containsExactly(person.somString)
        assertThat(deltakernummer(treff)).isEmpty()
    }

    @Test
    fun `møteoppsett oppretter romfordeling og rotasjon første gang`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        oppmøte(treff, p1, møtt = true)
        oppmøte(treff, p2, møtt = true)

        assertThat(møteoppsett(treff).statusCode()).isEqualTo(200)

        val svar = aggregat(treff)
        assertThat(svar["gjeldendeSteg"].asText()).isEqualTo("ROM")
        assertThat(svar["starttidspunkt"].asText()).isEqualTo("09:00")
        assertThat(svar["rom"]).hasSize(2)
        assertThat(svar["rom"].flatMap { it["jobbsøkere"] }.map { it.asText() })
            .containsExactly(p1.somString, p2.somString)
        assertThat(svar["arbeidsgiverRekkefølge"].map { it["førsteRomnummer"].asInt() }).containsExactly(1, 2)
    }

    @Test
    fun `endret møteoppsett beholder romfordelingen`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)
        val romFørst = aggregat(treff)["rom"].toString()

        assertThat(møteoppsett(treff, start = "11:30", varighet = 20).statusCode()).isEqualTo(200)

        val etter = aggregat(treff)
        assertThat(etter["starttidspunkt"].asText()).isEqualTo("11:30")
        assertThat(etter["varighetPerMøteMinutter"].asInt()).isEqualTo(20)
        assertThat(etter["rom"].toString()).isEqualTo(romFørst)
    }

    @Test
    fun `møteoppsett kan endres selv om alle oppmøter er fjernet`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)
        oppmøte(treff, person, møtt = false)

        assertThat(møteoppsett(treff, start = "12:00").statusCode()).isEqualTo(200)

        assertThat(aggregat(treff)["starttidspunkt"].asText()).isEqualTo("12:00")
        assertThat(antallTreffHendelser(treff, "TREFFGJENNOMFØRING_OPPRETTET")).isEqualTo(1)
        assertThat(antallTreffHendelser(treff, "TREFFGJENNOMFØRING_OPPSETT_ENDRET")).isEqualTo(1)
    }

    @Test
    fun `møteoppsett avvises på et vanlig treff`() {
        val treff = vanligTreff()
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)

        assertThat(møteoppsett(treff).statusCode()).isEqualTo(400)
    }

    @Test
    fun `møteoppsett krever minst én fremmøtt`() {
        val treff = workOpTreff()

        assertThat(møteoppsett(treff).statusCode()).isEqualTo(400)
    }

    @Test
    fun `ugyldig starttidspunkt avvises`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)

        assertThat(møteoppsett(treff, start = "25:00").statusCode()).isEqualTo(400)
        assertThat(møteoppsett(treff, varighet = 0).statusCode()).isEqualTo(400)
    }

    @Test
    fun `arbeidsgiver lagt til etter møteoppsett får rom og posisjon ved lesing`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)

        arbeidsgiver(treff, "999999999")

        val svar = aggregat(treff)
        assertThat(svar["antallRom"].asInt()).isEqualTo(3)
        assertThat(svar["rom"]).hasSize(3)
        assertThat(svar["arbeidsgiverRekkefølge"]).hasSize(3)
        assertThat(svar["arbeidsgiverRekkefølge"].map { it["førsteRomnummer"].asInt() }.toSet()).hasSize(3)
    }

    @Test
    fun `fjerning av oppmøte med registreringer gir 409 og ingen sideeffekt`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)
        interesse(treff, person, ag, interessert = true)

        val respons = oppmøte(treff, person, møtt = false)

        assertThat(respons.statusCode()).isEqualTo(409)
        val feil = mapper.readTree(respons.body())
        assertThat(feil["registreringer"]["interesser"].asInt()).isEqualTo(1)
        assertThat(oppmøteliste(treff)).containsExactly(person.somString)
        assertThat(aggregat(treff)["interesser"]).hasSize(1)
    }

    @Test
    fun `oppmøtet kan fjernes etter at interessen er ryddet, og gir kun oppmøtehendelsen`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)
        interesse(treff, person, ag, interessert = true)

        assertThat(interesse(treff, person, ag, interessert = false).statusCode()).isEqualTo(200)
        assertThat(oppmøte(treff, person, møtt = false).statusCode()).isEqualTo(200)

        val svar = aggregat(treff)
        assertThat(svar["oppmøte"]).isEmpty()
        assertThat(svar["interesser"]).isEmpty()
        assertThat(antallHendelser(treff, "REGISTRERT_OPPMØTE_FJERNET")).isEqualTo(1)
    }

    @Test
    fun `interesse endrer gjeldende steg uten å skrive hendelser`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)
        val førJobbsøker = antallJobbsøkerhendelser(treff)
        val førArbeidsgiver = antallArbeidsgiverhendelser(treff)

        assertThat(interesse(treff, person, ag, interessert = true).statusCode()).isEqualTo(200)

        assertThat(aggregat(treff)["gjeldendeSteg"].asText()).isEqualTo("INTERESSE")
        assertThat(aggregat(treff)["interesser"]).hasSize(1)
        assertThat(antallJobbsøkerhendelser(treff)).isEqualTo(førJobbsøker)
        assertThat(antallArbeidsgiverhendelser(treff)).isEqualTo(førArbeidsgiver)
    }

    @Test
    fun `interesse er idempotent ved gjentakelse`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)

        interesse(treff, person, ag, interessert = true)
        interesse(treff, person, ag, interessert = true)

        assertThat(aggregat(treff)["interesser"]).hasSize(1)
    }

    @Test
    fun `bare fremmøtte kan registrere interesse`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)

        assertThat(interesse(treff, person, ag, interessert = true).statusCode()).isEqualTo(400)
    }

    @Test
    fun `fordel erstatter hele fordelingen`() {
        val treff = workOpTreff()
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        val ag = aktivArbeidsgiver(treff)
        listOf(p1, p2).forEach { oppmøte(treff, it, møtt = true) }
        listOf(p1, p2).forEach { interesse(treff, it, ag, interessert = true) }

        assertThat(post(treff, "/treffgjennomforing/intervjufordeling/fordel").statusCode()).isEqualTo(200)

        val fordelinger = aggregat(treff)["intervjufordelinger"]
        assertThat(fordelinger).hasSize(1)
        assertThat(fordelinger[0]["inkludertePersonTreffIder"].map { it.asText() })
            .containsExactlyInAnyOrder(p1.somString, p2.somString)
        assertThat(aggregat(treff)["gjeldendeSteg"].asText()).isEqualTo("FORDELING")
    }

    @Test
    fun `vurdering lagres, og en tom rad slettes`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)

        assertThat(vurderingFor(treff, person, ag, ""","vurderingsstatus":"AKTUELL","jobbtilbud":true""").statusCode())
            .isEqualTo(200)

        val lagret = aggregat(treff)["vurderinger"]
        assertThat(lagret).hasSize(1)
        assertThat(lagret[0]["vurderingsstatus"].asText()).isEqualTo("AKTUELL")
        assertThat(antallHendelser(treff, "VURDERT")).isEqualTo(1)
        assertThat(antallHendelser(treff, "JOBBTILBUD_GITT")).isEqualTo(1)

        vurderingFor(treff, person, ag, ""","vurderingsstatus":null,"jobbtilbud":false""")
        assertThat(aggregat(treff)["vurderinger"]).isEmpty()
    }

    @Test
    fun `dato for avtalt intervju uten avkryssing avvises`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)

        val felter = ""","avtaltIntervju":false,"avtaltIntervjuDato":"2026-09-01""""
        assertThat(vurderingFor(treff, person, ag, felter).statusCode()).isEqualTo(400)
    }

    @Test
    fun `vanlig treff kan registrere oppmøte, interesse og vurdering uten møteoppsett`() {
        val treff = vanligTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)

        oppmøte(treff, person, møtt = true)
        assertThat(interesse(treff, person, ag, interessert = true).statusCode()).isEqualTo(200)

        assertThat(vurderingFor(treff, person, ag, ""","vurderingsstatus":"KANSKJE"""").statusCode()).isEqualTo(200)

        val svar = aggregat(treff)
        assertThat(svar["gjeldendeSteg"].asText()).isEqualTo("VURDERING")
        assertThat(svar["rom"]).isEmpty()
        assertThat(svar["intervjufordelinger"]).isEmpty()
        assertThat(svar["vurderinger"]).hasSize(1)
    }

    @Test
    fun `flytting til annet rom flytter personen og bevarer andre`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        oppmøte(treff, p1, møtt = true)
        oppmøte(treff, p2, møtt = true)
        møteoppsett(treff)

        val flyttP2TilRom1 = """{"romnummer":1}"""
        assertThat(put(treff, "/treffgjennomforing/romfordeling/${p2.somString}", flyttP2TilRom1).statusCode()).isEqualTo(200)

        val rom = aggregat(treff)["rom"]
        assertThat(rom.first { it["romnummer"].asInt() == 1 }["jobbsøkere"].map { it.asText() })
            .containsExactlyInAnyOrder(p1.somString, p2.somString)
        assertThat(rom.first { it["romnummer"].asInt() == 2 }["jobbsøkere"]).isEmpty()
    }

    @Test
    fun `flytting til samme rom er idempotent`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        oppmøte(treff, p1, møtt = true)
        møteoppsett(treff)

        assertThat(flyttTilRom(treff, p1, 2).statusCode()).isEqualTo(200)
        val førsteFordeling = aggregat(treff)["rom"]
        assertThat(flyttTilRom(treff, p1, 2).statusCode()).isEqualTo(200)
        val gjentattFordeling = aggregat(treff)["rom"]
        assertThat(gjentattFordeling).isEqualTo(førsteFordeling)
        assertThat(gjentattFordeling.first { it["romnummer"].asInt() == 2 }["jobbsøkere"].map { it.asText() })
            .containsExactly(p1.somString)
    }

    @Test
    fun `samtidige flyttinger av ulike personer bevarer begge endringene`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        oppmøte(treff, p1, møtt = true)
        oppmøte(treff, p2, møtt = true)
        møteoppsett(treff)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val første = executor.submit<HttpResponse<String>> { flyttTilRom(treff, p1, 2) }
            val andre = executor.submit<HttpResponse<String>> { flyttTilRom(treff, p2, 1) }
            assertThat(første.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200)
            assertThat(andre.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200)
        }

        val rom = aggregat(treff)["rom"]
        assertThat(rom.first { it["romnummer"].asInt() == 1 }["jobbsøkere"].map { it.asText() })
            .containsExactly(p2.somString)
        assertThat(rom.first { it["romnummer"].asInt() == 2 }["jobbsøkere"].map { it.asText() })
            .containsExactly(p1.somString)
    }

    @Test
    fun `flytting avviser person fra et annet treff og manglende møteoppsett`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff, "11111111111")
        val annetTreff = workOpTreff()
        val annenPerson = jobbsøker(annetTreff, "22222222222")
        oppmøte(treff, person, møtt = true)
        oppmøte(annetTreff, annenPerson, møtt = true)

        assertThat(flyttTilRom(treff, person, 2).statusCode()).isEqualTo(400)
        møteoppsett(treff)
        val før = aggregat(treff)["rom"]
        assertThat(flyttTilRom(treff, annenPerson, 2).statusCode()).isEqualTo(400)
        assertThat(aggregat(treff)["rom"]).isEqualTo(før)
    }

    @Test
    fun `rom beholdes når siste oppmøte fjernes og ny fremmøtt kan flyttes`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff, "11111111111")
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)
        oppmøte(treff, person, møtt = false)

        val tommeRom = aggregat(treff)["rom"]
        assertThat(tommeRom).hasSize(2)
        assertThat(tommeRom.flatMap { it["jobbsøkere"].toList() }).isEmpty()

        val nyPerson = jobbsøker(treff, "22222222222")
        oppmøte(treff, nyPerson, møtt = true)
        assertThat(flyttTilRom(treff, nyPerson, 2).statusCode()).isEqualTo(200)
        val rom = aggregat(treff)["rom"]
        assertThat(rom.first { it["romnummer"].asInt() == 2 }["jobbsøkere"].map { it.asText() })
            .containsExactly(nyPerson.somString)
    }

    @Test
    fun `flytting avvises med ugyldig romnummer`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)

        val ugyldigRom = """{"romnummer":99}"""
        assertThat(put(treff, "/treffgjennomforing/romfordeling/${person.somString}", ugyldigRom).statusCode()).isEqualTo(400)
    }

    @Test
    fun `flytting avviser person som ikke er fremmøtt`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        val hjemme = jobbsøker(treff, "22222222222")
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)

        val flyttHjemme = """{"romnummer":1}"""
        assertThat(put(treff, "/treffgjennomforing/romfordeling/${hjemme.somString}", flyttHjemme).statusCode()).isEqualTo(400)
    }

    @Test
    fun `flytting av nylig fremmøtt uten lagret romrad bevarer beregnet plassering for andre`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        oppmøte(treff, p1, møtt = true)
        oppmøte(treff, p2, møtt = true)
        møteoppsett(treff)

        val p3 = jobbsøker(treff, "33333333333")
        val p4 = jobbsøker(treff, "44444444444")
        oppmøte(treff, p3, møtt = true)
        oppmøte(treff, p4, møtt = true)

        val før = aggregat(treff)["rom"]
        assertThat(før.first { it["romnummer"].asInt() == 1 }["jobbsøkere"].map { it.asText() })
            .containsExactly(p1.somString, p3.somString)
        assertThat(før.first { it["romnummer"].asInt() == 2 }["jobbsøkere"].map { it.asText() })
            .containsExactly(p2.somString, p4.somString)
        assertThat(flyttTilRom(treff, p3, 2).statusCode()).isEqualTo(200)

        val rom = aggregat(treff)["rom"]
        assertThat(rom.first { it["romnummer"].asInt() == 1 }["jobbsøkere"].map { it.asText() })
            .containsExactly(p1.somString)
        assertThat(rom.first { it["romnummer"].asInt() == 2 }["jobbsøkere"].map { it.asText() })
            .containsExactly(p2.somString, p3.somString, p4.somString)
    }

    @Test
    fun `fordelRomPåNytt fordeler alle fremmøtte jevnt på rom`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        val p3 = jobbsøker(treff, "33333333333")
        oppmøte(treff, p1, møtt = true)
        oppmøte(treff, p2, møtt = true)
        oppmøte(treff, p3, møtt = true)
        møteoppsett(treff)

        // Manuelt flytt p2 og p3 til rom 1
        assertThat(put(treff, "/treffgjennomforing/romfordeling/${p2.somString}", """{"romnummer":1}""").statusCode()).isEqualTo(200)
        assertThat(put(treff, "/treffgjennomforing/romfordeling/${p3.somString}", """{"romnummer":1}""").statusCode()).isEqualTo(200)

        // Kall fordel på nytt
        val respons = post(treff, "/treffgjennomforing/romfordeling/fordel", "{}")
        assertThat(respons.statusCode()).isEqualTo(200)

        val rom = aggregat(treff)["rom"]
        val r1 = rom.first { it["romnummer"].asInt() == 1 }["jobbsøkere"].map { it.asText() }
        val r2 = rom.first { it["romnummer"].asInt() == 2 }["jobbsøkere"].map { it.asText() }
        assertThat(r1).hasSize(2)
        assertThat(r2).hasSize(1)
        assertThat(r1 + r2).containsExactlyInAnyOrder(p1.somString, p2.somString, p3.somString)
    }

    @Test
    fun `intervjufordeling avviser person som er både inkludert og ekskludert`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)

        val overlapp = """
            {"arbeidsgiverTreffId":"${ag.somString}","inkludertePersonTreffIder":["${person.somString}"],"ekskludertePersonTreffIder":["${person.somString}"]}
        """.trimIndent()
        assertThat(put(treff, "/treffgjennomforing/intervjufordeling", overlapp).statusCode()).isEqualTo(400)
    }

    @Test
    fun `gjeldende steg går bare framover`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)
        interesse(treff, person, ag, interessert = true)

        interesse(treff, person, ag, interessert = false)

        assertThat(aggregat(treff)["gjeldendeSteg"].asText()).isEqualTo("INTERESSE")
    }

    @Test
    fun `arbeidsgiver lagt til etter møteoppsett får tomt rom som personer kan flyttes til og fordeles til`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        oppmøte(treff, p1, møtt = true)
        møteoppsett(treff)

        val ag3 = arbeidsgiver(treff, "999999999")

        val etterTillegg = aggregat(treff)
        assertThat(etterTillegg["antallRom"].asInt()).isEqualTo(3)
        assertThat(etterTillegg["rom"]).hasSize(3)
        val ag3Rotasjon = etterTillegg["arbeidsgiverRekkefølge"].first { it["arbeidsgiverTreffId"].asText() == ag3.somString }
        val ag3Romnummer = ag3Rotasjon["førsteRomnummer"].asInt()
        assertThat(etterTillegg["rom"].first { it["romnummer"].asInt() == ag3Romnummer }["jobbsøkere"]).isEmpty()

        // Kan flytte person til ag3 sitt rom
        assertThat(flyttTilRom(treff, p1, ag3Romnummer).statusCode()).isEqualTo(200)
        val etterFlytt = aggregat(treff)["rom"]
        assertThat(etterFlytt.first { it["romnummer"].asInt() == ag3Romnummer }["jobbsøkere"].map { it.asText() })
            .containsExactly(p1.somString)

        // Kan registrere interesse for ag3
        assertThat(interesse(treff, p1, ag3, interessert = true).statusCode()).isEqualTo(200)
        assertThat(aggregat(treff)["interesser"]).hasSize(1)
    }

    @Test
    fun `sletting av arbeidsgiver med personer i rom avvises med 409`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        oppmøte(treff, p1, møtt = true)
        møteoppsett(treff)

        val agg = aggregat(treff)
        val ag1Id = agg["arbeidsgiverRekkefølge"].first { it["førsteRomnummer"].asInt() == 1 }["arbeidsgiverTreffId"].asText()
        val ag1 = ArbeidsgiverTreffId(ag1Id)

        // p1 starter i rom 1 (arbeidsgiver 1 sitt rom)
        val slettRespons = slettArbeidsgiver(treff, ag1)
        assertThat(slettRespons.statusCode()).isEqualTo(409)
        val feil = mapper.readTree(slettRespons.body())
        assertThat(feil["personerIRom"].asInt()).isEqualTo(1)
        assertThat(feil["hint"].asText()).containsIgnoringCase("arbeidsgiverens rom")

        // Arbeidsgiver er ikke slettet
        assertThat(aggregat(treff)["antallRom"].asInt()).isEqualTo(2)
    }

    @Test
    fun `sletting av arbeidsgiver med interesser avvises med 409`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        oppmøte(treff, p1, møtt = true)
        møteoppsett(treff)

        // Flytt p1 til rom 2 slik at rom 1 blir tomt
        assertThat(flyttTilRom(treff, p1, 2).statusCode()).isEqualTo(200)

        val agg = aggregat(treff)
        val ag1Id = agg["arbeidsgiverRekkefølge"].first { it["førsteRomnummer"].asInt() == 1 }["arbeidsgiverTreffId"].asText()
        val ag1 = ArbeidsgiverTreffId(ag1Id)

        // Legg til interesse for ag1
        assertThat(interesse(treff, p1, ag1, interessert = true).statusCode()).isEqualTo(200)

        val slettRespons = slettArbeidsgiver(treff, ag1)
        assertThat(slettRespons.statusCode()).isEqualTo(409)
        val feil = mapper.readTree(slettRespons.body())
        assertThat(feil["personerIRom"].asInt()).isEqualTo(0)
        assertThat(feil["interesser"].asInt()).isEqualTo(1)
        assertThat(feil["hint"].asText()).containsIgnoringCase("interesser")
    }

    @Test
    fun `sletting av arbeidsgiver med vurdering avvises med 409`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        oppmøte(treff, p1, møtt = true)
        møteoppsett(treff)

        // Flytt p1 til rom 2 slik at rom 1 blir tomt
        assertThat(flyttTilRom(treff, p1, 2).statusCode()).isEqualTo(200)

        val agg = aggregat(treff)
        val ag1Id = agg["arbeidsgiverRekkefølge"].first { it["førsteRomnummer"].asInt() == 1 }["arbeidsgiverTreffId"].asText()
        val ag1 = ArbeidsgiverTreffId(ag1Id)

        assertThat(vurderingFor(treff, p1, ag1, ""","vurderingsstatus":"AKTUELL"""").statusCode()).isEqualTo(200)

        val slettRespons = slettArbeidsgiver(treff, ag1)
        assertThat(slettRespons.statusCode()).isEqualTo(409)
        val feil = mapper.readTree(slettRespons.body())
        assertThat(feil["vurderinger"].asInt()).isEqualTo(1)
        assertThat(feil["hint"].asText()).containsIgnoringCase("vurderinger")
    }

    @Test
    fun `sletting av arbeidsgiver i tomt rom tillates selv med formidling og kompakterer møteplan`() {
        val treff = workOpTreff(antallArbeidsgivere = 3)
        val p1 = jobbsøker(treff, "11111111111")
        oppmøte(treff, p1, møtt = true)
        møteoppsett(treff)

        val aggFør = aggregat(treff)
        val ag2Id = aggFør["arbeidsgiverRekkefølge"].first { it["førsteRomnummer"].asInt() == 2 }["arbeidsgiverTreffId"].asText()
        val ag3Id = aggFør["arbeidsgiverRekkefølge"].first { it["førsteRomnummer"].asInt() == 3 }["arbeidsgiverTreffId"].asText()
        val ag2 = ArbeidsgiverTreffId(ag2Id)

        // p1 er i rom 3 (ag3 sitt rom)
        assertThat(flyttTilRom(treff, p1, 3).statusCode()).isEqualTo(200)

        // Opprett en formidling for ag2
        db.opprettFormidling(treff, p1, ag2, UUID.randomUUID(), UUID.randomUUID())

        // Slett ag2 (som har tomt rom 2 og kun formidling)
        val slettRespons = slettArbeidsgiver(treff, ag2)
        assertThat(slettRespons.statusCode()).isEqualTo(204)

        // Verifiser at møteplan er komprimert: antall rom er nå 2, ag3 har nå rom 2, og p1 er nå i rom 2
        val aggEtter = aggregat(treff)
        assertThat(aggEtter["antallRom"].asInt()).isEqualTo(2)
        assertThat(aggEtter["rom"]).hasSize(2)
        val nyAg3Rotasjon = aggEtter["arbeidsgiverRekkefølge"].first { it["arbeidsgiverTreffId"].asText() == ag3Id }
        assertThat(nyAg3Rotasjon["førsteRomnummer"].asInt()).isEqualTo(2)
        val rom2 = aggEtter["rom"].first { it["romnummer"].asInt() == 2 }
        assertThat(rom2["jobbsøkere"].map { it.asText() }).containsExactly(p1.somString)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `tillegg lagrer tomt rom uten å flytte sent fremmøtte`(medBehov: Boolean) {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        oppmøte(treff, p1, møtt = true)
        oppmøte(treff, p2, møtt = true)
        møteoppsett(treff)
        val sen = jobbsøker(treff, "33333333333")
        oppmøte(treff, sen, møtt = true)
        val romFør = lagredeRom(treff)
        assertThat(romFør[sen.somString]).isEqualTo(1)

        val ny = opprettArbeidsgiverViaApi(treff, "000000001", medBehov)

        assertThat(lagretRotasjon(treff)[ny.somString]).isEqualTo(3)
        assertThat(lagredeRom(treff)).isEqualTo(romFør)
        val etter = aggregat(treff)
        assertThat(etter["rom"].last()["jobbsøkere"]).isEmpty()
        assertThat(flyttTilRom(treff, sen, 3).statusCode()).isEqualTo(200)
        assertThat(post(treff, "/treffgjennomforing/romfordeling/fordel").statusCode()).isEqualTo(200)
        assertThat(aggregat(treff)["rom"].map { it["jobbsøkere"].size() }).containsExactly(1, 1, 1)
        assertThat(interesse(treff, sen, ny, true).statusCode()).isEqualTo(200)
        assertThat(post(treff, "/treffgjennomforing/intervjufordeling/fordel").statusCode()).isEqualTo(200)
        assertThat(vurderingFor(treff, sen, ny, ""","vurderingsstatus":"AKTUELL"""").statusCode()).isEqualTo(200)
    }

    @Test
    fun `sent oppmøte lagres i rom og blokkerer sletting frem til oppmøtet fjernes`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val første = jobbsøker(treff, "11111111111")
        oppmøte(treff, første, møtt = true)
        møteoppsett(treff)
        val sen = jobbsøker(treff, "22222222222")
        oppmøte(treff, sen, møtt = true)
        val arbeidsgiver = ArbeidsgiverTreffId(lagretRotasjon(treff).entries.single { it.value == 2 }.key)

        assertThat(lagredeRom(treff)[sen.somString]).isEqualTo(2)
        val blokkert = slettArbeidsgiver(treff, arbeidsgiver)
        assertThat(blokkert.statusCode()).isEqualTo(409)
        assertThat(mapper.readTree(blokkert.body())["personerIRom"].asInt()).isEqualTo(1)

        assertThat(oppmøte(treff, sen, møtt = false).statusCode()).isEqualTo(200)
        assertThat(lagredeRom(treff)).doesNotContainKey(sen.somString)
        assertThat(slettArbeidsgiver(treff, arbeidsgiver).statusCode()).isEqualTo(204)
    }

    @Test
    fun `eldre beregnet romplassering blokkerer sletting uten lagring ved avvisning`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val første = jobbsøker(treff, "11111111111")
        oppmøte(treff, første, møtt = true)
        møteoppsett(treff)
        val sen = jobbsøker(treff, "22222222222")
        oppmøte(treff, sen, møtt = true)
        fjernLagretRom(sen)
        val arbeidsgiver = ArbeidsgiverTreffId(lagretRotasjon(treff).entries.single { it.value == 2 }.key)
        val før = aggregat(treff)

        assertThat(slettArbeidsgiver(treff, arbeidsgiver).statusCode()).isEqualTo(409)

        assertThat(aggregat(treff)).isEqualTo(før)
        assertThat(lagredeRom(treff)).doesNotContainKey(sen.somString)
    }

    @Test
    fun `eldre beregnede plasseringer bevares når arbeidsgiver legges til eller fjernes`() {
        val treff = workOpTreff(antallArbeidsgivere = 3)
        val første = jobbsøker(treff, "11111111111")
        oppmøte(treff, første, møtt = true)
        møteoppsett(treff)
        flyttTilRom(treff, første, 3)
        val sen = jobbsøker(treff, "22222222222")
        oppmøte(treff, sen, møtt = true)
        fjernLagretRom(sen)
        val tomArbeidsgiver = ArbeidsgiverTreffId(lagretRotasjon(treff).entries.single { it.value == 2 }.key)

        assertThat(slettArbeidsgiver(treff, tomArbeidsgiver).statusCode()).isEqualTo(204)
        assertThat(lagredeRom(treff)).containsEntry(sen.somString, 1).containsEntry(første.somString, 2)
        fjernLagretRom(sen)
        val ny = opprettArbeidsgiverViaApi(treff, "000000001")
        assertThat(lagredeRom(treff)).containsEntry(sen.somString, 1).containsEntry(første.somString, 2)
        assertThat(lagretRotasjon(treff)[ny.somString]).isEqualTo(3)
        assertThat(aggregat(treff)["rom"].last()["jobbsøkere"]).isEmpty()
    }

    @Test
    fun `GET beregner eldre møteplan uten å skrive til databasen`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)
        fjernLagretRom(person)
        val ny = arbeidsgiver(treff, "000000001")
        val rotasjonFør = lagretRotasjon(treff)

        db.dataSource.connection.use { connection ->
            connection.isReadOnly = true
            connection.autoCommit = false
            val kontekst = ctx.treffkontekstRepository.krevKontekst(connection, treff)
            val dto = ctx.treffgjennomføringReader.les(connection, kontekst)
            assertThat(dto.arbeidsgiverRekkefølge.map { it.arbeidsgiverTreffId }).contains(ny.somString)
            connection.commit()
        }

        assertThat(hent(treff, eier).statusCode()).isEqualTo(200)
        assertThat(lagretRotasjon(treff)).isEqualTo(rotasjonFør)
        assertThat(lagredeRom(treff)).isEmpty()
    }

    @Test
    fun `sletting venter på trefflåsen og ser interessen som ble lagret mens den ventet`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)

        val respons = medVentendeOperasjon(treff, { slettArbeidsgiver(treff, ag) }) { connection ->
            connection.prepareStatement(
                """
                INSERT INTO interesse (jobbsoker_id, arbeidsgiver_id)
                SELECT j.jobbsoker_id, a.arbeidsgiver_id FROM jobbsoker j, arbeidsgiver a
                WHERE j.id = ? AND a.id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, person.somUuid)
                stmt.setObject(2, ag.somUuid)
                stmt.executeUpdate()
            }
        }

        assertThat(respons.statusCode()).isEqualTo(409)
        assertThat(mapper.readTree(respons.body())["interesser"].asInt()).isEqualTo(1)
        assertThat(aggregat(treff)["interesser"]).hasSize(1)
        assertThat(ctx.arbeidsgiverService.hentArbeidsgivere(treff)).hasSize(1)
    }

    @Test
    fun `interesse venter på trefflåsen og avviser arbeidsgiver slettet mens den ventet`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)

        val respons = medVentendeOperasjon(treff, { interesse(treff, person, ag, true) }) { connection ->
            ctx.arbeidsgiverRepository.markerSlettet(connection, ag.somUuid)
        }

        assertThat(respons.statusCode()).isEqualTo(400)
        assertThat(aggregat(treff)["interesser"]).isEmpty()
    }

    @Test
    fun `samtidige arbeidsgivertillegg lagrer ulike rom før noen leser gjennomføringen`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val første = executor.submit<ArbeidsgiverTreffId> { opprettArbeidsgiverViaApi(treff, "000000001") }
            val andre = executor.submit<ArbeidsgiverTreffId> { opprettArbeidsgiverViaApi(treff, "000000002", true) }
            val nye = listOf(første.get(10, TimeUnit.SECONDS), andre.get(10, TimeUnit.SECONDS))
            val lagret = lagretRotasjon(treff)
            assertThat(nye.map { lagret[it.somString] }).containsExactlyInAnyOrder(3, 4)
            assertThat(lagredeRom(treff)).containsExactlyEntriesOf(mapOf(person.somString to 1))
        }
    }

    @Test
    fun `siste arbeidsgiver kan fjernes fra tom møteplan og reaktiveres med rom igjen`() {
        val treff = workOpTreff(antallArbeidsgivere = 0)
        val ag = opprettArbeidsgiverViaApi(treff, "000000001", true)
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)
        oppmøte(treff, person, møtt = false)

        assertThat(slettArbeidsgiver(treff, ag).statusCode()).isEqualTo(204)
        assertThat(lagretRotasjon(treff)).isEmpty()
        val reaktivert = opprettArbeidsgiverViaApi(treff, "000000001", true)
        assertThat(reaktivert).isEqualTo(ag)
        assertThat(lagretRotasjon(treff)).containsExactlyEntriesOf(mapOf(ag.somString to 1))
        assertThat(aggregat(treff)["rom"].single()["jobbsøkere"]).isEmpty()
    }

    @Test
    fun `sletting av arbeidsgiver fra et annet treff avvises`() {
        val treff = workOpTreff()
        val annet = workOpTreff()
        val ag = aktivArbeidsgiver(annet)
        assertThat(slettArbeidsgiver(treff, ag).statusCode()).isEqualTo(404)
        assertThat(ctx.arbeidsgiverService.hentArbeidsgivere(annet)).hasSize(1)
    }

    @Test
    fun `eldre romplasseringer uten møteoppsett bevares ved tillegg og sletting`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff, "00000000000")
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)
        flyttTilRom(treff, person, 2)
        val tomArbeidsgiver = ArbeidsgiverTreffId(lagretRotasjon(treff).entries.single { it.value == 1 }.key)
        db.dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                DELETE FROM moteoppsett WHERE treffgjennomforing_id IN (
                    SELECT t.treffgjennomforing_id FROM treffgjennomforing t
                    JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = t.rekrutteringstreff_id
                    WHERE rt.id = ?
                )
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                assertThat(stmt.executeUpdate()).isEqualTo(1)
            }
        }

        val ny = opprettArbeidsgiverViaApi(treff, "000000001")
        assertThat(lagredeRom(treff)).containsExactlyEntriesOf(mapOf(person.somString to 2))
        assertThat(lagretRotasjon(treff)[ny.somString]).isEqualTo(3)
        assertThat(slettArbeidsgiver(treff, tomArbeidsgiver).statusCode()).isEqualTo(204)
        assertThat(lagredeRom(treff)).containsExactlyEntriesOf(mapOf(person.somString to 1))
        assertThat(lagretRotasjon(treff)[ny.somString]).isEqualTo(2)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `registreringer blokkerer sletting på vanlig treff uten møteplan`(medVurdering: Boolean) {
        val treff = vanligTreff()
        val person = jobbsøker(treff, "00000000000")
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)
        val registrert = if (medVurdering) {
            vurderingFor(treff, person, ag, ""","vurderingsstatus":"AKTUELL"""")
        } else {
            interesse(treff, person, ag, true)
        }
        assertThat(registrert.statusCode()).isEqualTo(200)
        assertThat(aggregat(treff)["rom"]).isEmpty()

        val respons = slettArbeidsgiver(treff, ag)

        assertThat(respons.statusCode()).isEqualTo(409)
        val felt = if (medVurdering) "vurderinger" else "interesser"
        assertThat(mapper.readTree(respons.body())[felt].asInt()).isEqualTo(1)
        assertThat(ctx.arbeidsgiverService.hentArbeidsgivere(treff)).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `tillegg ser møteoppsett som opprettes mens det venter på trefflåsen`(medBehov: Boolean) {
        val treff = workOpTreff()
        val person = jobbsøker(treff, "00000000000")
        oppmøte(treff, person, møtt = true)

        val ny = medVentendeOperasjon(treff, { opprettArbeidsgiverViaApi(treff, "000000001", medBehov) }) { connection ->
            connection.prepareStatement(
                """
                INSERT INTO moteoppsett (treffgjennomforing_id, starttidspunkt, varighet_min)
                SELECT t.treffgjennomforing_id, '09:00', 15 FROM treffgjennomforing t
                JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = t.rekrutteringstreff_id
                WHERE rt.id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                assertThat(stmt.executeUpdate()).isEqualTo(1)
            }
        }

        assertThat(lagretRotasjon(treff)[ny.somString]).isEqualTo(2)
        assertThat(lagredeRom(treff)).containsExactlyEntriesOf(mapOf(person.somString to 1))
        assertThat(aggregat(treff)["rom"].last()["jobbsøkere"]).isEmpty()
    }

    @ParameterizedTest
    @CsvSource("true,false", "false,false", "true,true", "false,true")
    fun `intervjufordeling telles separat og blokkerer både arbeidsgiversletting og fjerning av oppmøte`(
        inkludert: Boolean,
        medInteresse: Boolean,
    ) {
        val treff = workOpTreff()
        val person = jobbsøker(treff, "00000000000")
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)
        if (medInteresse) assertThat(interesse(treff, person, ag, true).statusCode()).isEqualTo(200)
        assertThat(
            intervjufordeling(
                treff, ag,
                inkluderte = if (inkludert) listOf(person) else emptyList(),
                ekskluderte = if (inkludert) emptyList() else listOf(person),
            ).statusCode()
        ).isEqualTo(200)
        val før = aggregat(treff)
        val forventetHint = if (medInteresse) {
            "Fjern registrerte interesser og fjern registrerte intervjufordelinger først."
        } else {
            "Fjern registrerte intervjufordelinger først."
        }

        val sletting = slettArbeidsgiver(treff, ag)
        assertThat(sletting.statusCode()).isEqualTo(409)
        val arbeidsgiverFeil = mapper.readTree(sletting.body())
        assertThat(arbeidsgiverFeil["interesser"].asInt()).isEqualTo(if (medInteresse) 1 else 0)
        assertThat(arbeidsgiverFeil["intervjufordelinger"].asInt()).isEqualTo(1)
        assertThat(arbeidsgiverFeil["hint"].asText()).isEqualTo(forventetHint)

        val oppmøteSvar = oppmøte(treff, person, møtt = false)
        assertThat(oppmøteSvar.statusCode()).isEqualTo(409)
        val oppmøteFeil = mapper.readTree(oppmøteSvar.body())
        assertThat(oppmøteFeil["registreringer"]["interesser"].asInt()).isEqualTo(if (medInteresse) 1 else 0)
        assertThat(oppmøteFeil["registreringer"]["intervjufordelinger"].asInt()).isEqualTo(1)
        assertThat(oppmøteFeil["hint"].asText()).isEqualTo(forventetHint)
        assertThat(aggregat(treff)).isEqualTo(før)

        assertThat(intervjufordeling(treff, ag).statusCode()).isEqualTo(200)
        if (medInteresse) assertThat(interesse(treff, person, ag, false).statusCode()).isEqualTo(200)
        assertThat(oppmøte(treff, person, møtt = false).statusCode()).isEqualTo(200)
        assertThat(slettArbeidsgiver(treff, ag).statusCode()).isEqualTo(204)
    }

    @ParameterizedTest
    @ValueSource(strings = ["interesse", "intervjufordeling", "vurdering"])
    fun `vanlig jobbsøkersletting blokkeres av registreringer også med status LAGT_TIL`(type: String) {
        val treff = workOpTreff()
        val person = jobbsøker(treff, "00000000000")
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)
        val registrert = when (type) {
            "interesse" -> interesse(treff, person, ag, true)
            "intervjufordeling" -> intervjufordeling(treff, ag, inkluderte = listOf(person))
            else -> vurderingFor(treff, person, ag, ""","vurderingsstatus":"AKTUELL"""")
        }
        assertThat(registrert.statusCode()).isEqualTo(200)
        db.dataSource.connection.use { ctx.jobbsøkerRepository.endreStatus(it, person, JobbsøkerStatus.LAGT_TIL) }
        val før = aggregat(treff)
        val hendelserFør = antallJobbsøkerhendelser(treff)

        assertThat(slettJobbsøker(treff, person).statusCode()).isEqualTo(422)
        assertThat(aggregat(treff)).isEqualTo(før)
        assertThat(antallJobbsøkerhendelser(treff)).isEqualTo(hendelserFør)
        assertThat(ctx.jobbsøkerService.hentJobbsøkere(treff).single().status).isEqualTo(JobbsøkerStatus.LAGT_TIL)
    }

    @Test
    fun `lovlig jobbsøkersletting rydder egen gammel romplassering uten å flytte andre`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val første = jobbsøker(treff, "00000000000")
        val andre = jobbsøker(treff, "00000000001")
        oppmøte(treff, første, møtt = true)
        oppmøte(treff, andre, møtt = true)
        møteoppsett(treff)
        val romFør = lagredeRom(treff)
        db.dataSource.connection.use { ctx.jobbsøkerRepository.endreStatus(it, første, JobbsøkerStatus.LAGT_TIL) }

        assertThat(slettJobbsøker(treff, første).statusCode()).isEqualTo(200)
        assertThat(lagredeRom(treff)).isEqualTo(romFør - første.somString)
        assertThat(slettJobbsøker(treff, første).statusCode()).isEqualTo(404)
        assertThat(ctx.jobbsøkerService.hentJobbsøkerHendelser(treff).count { it.hendelsestype == JobbsøkerHendelsestype.SLETTET })
            .isEqualTo(1)
    }

    @Test
    fun `jobbsøkersletting avviser feil treff selv om samme person finnes på begge treff`() {
        val førsteTreff = workOpTreff()
        val andreTreff = workOpTreff()
        val første = jobbsøker(førsteTreff, "00000000000")
        jobbsøker(andreTreff, "00000000000")

        assertThat(slettJobbsøker(andreTreff, første).statusCode()).isEqualTo(404)
        assertThat(ctx.jobbsøkerService.hentJobbsøkere(førsteTreff)).hasSize(1)
        assertThat(ctx.jobbsøkerService.hentJobbsøkere(andreTreff)).hasSize(1)
    }

    @Test
    fun `jobbsøkersletting ser oppmøte som ble registrert mens den ventet på trefflåsen`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff, "00000000000")

        val svar = medVentendeOperasjon(treff, { slettJobbsøker(treff, person) }) { connection ->
            ctx.jobbsøkerService.registrerOppmøte(connection, person)
        }

        assertThat(svar.statusCode()).isEqualTo(422)
        assertThat(oppmøteliste(treff)).containsExactly(person.somString)
        assertThat(antallHendelser(treff, "SLETTET")).isZero()
    }

    @Test
    fun `jobbsøkersletting ser intervjufordeling som ble lagret mens den ventet på trefflåsen`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff, "00000000000")
        val ag = aktivArbeidsgiver(treff)

        val svar = medVentendeOperasjon(treff, { slettJobbsøker(treff, person) }) { connection ->
            ctx.matchingRepository.erstattIntervjufordelinger(
                connection,
                listOf(ArbeidsgiverIntervjufordeling(ag, listOf(person), emptyList())),
                ctx.treffkontekstRepository.krevKontekst(connection, treff),
            )
        }

        assertThat(svar.statusCode()).isEqualTo(422)
        assertThat(aggregat(treff)["intervjufordelinger"]).hasSize(1)
        assertThat(antallHendelser(treff, "SLETTET")).isZero()
    }

    @Test
    fun `oppmøte avviser jobbsøker slettet mens det ventet på trefflåsen`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff, "00000000000")

        val svar = medVentendeOperasjon(treff, { oppmøte(treff, person, møtt = true) }) { connection ->
            ctx.jobbsøkerRepository.endreStatus(connection, person, JobbsøkerStatus.SLETTET)
        }

        assertThat(svar.statusCode()).isEqualTo(400)
        assertThat(oppmøteliste(treff)).isEmpty()
        assertThat(lagredeRom(treff)).isEmpty()
    }

    // --- hjelpere -------------------------------------------------------------

    private fun slettJobbsøker(treff: TreffId, person: PersonTreffId): HttpResponse<String> = send(
        HttpRequest.newBuilder().DELETE(),
        "/api/rekrutteringstreff/${treff.somString}/jobbsoker/${person.somString}/slett",
        eier,
        listOf(arbeidsgiverrettet),
    )

    private fun intervjufordeling(
        treff: TreffId,
        arbeidsgiver: ArbeidsgiverTreffId,
        inkluderte: List<PersonTreffId> = emptyList(),
        ekskluderte: List<PersonTreffId> = emptyList(),
    ): HttpResponse<String> = put(
        treff,
        "/treffgjennomforing/intervjufordeling",
        mapper.writeValueAsString(
            ArbeidsgiverIntervjufordelingDto(
                arbeidsgiver.somString, inkluderte.map { it.somString }, ekskluderte.map { it.somString },
            )
        ),
    )

    private fun opprettArbeidsgiverViaApi(
        treff: TreffId,
        orgnr: String,
        medBehov: Boolean = false,
    ): ArbeidsgiverTreffId {
        val behov = if (medBehov) """,
            "behov": {
                "samledeKvalifikasjoner": [{"label":"Testyrke","kategori":"YRKESTITTEL","konseptId":1}],
                "arbeidssprak":["Norsk"],"antall":1,"ansettelsesformer":["Fast"],"personligeEgenskaper":[]
            }
        """ else ""
        val sti = if (medBehov) "/arbeidsgiver-med-behov" else "/arbeidsgiver"
        val respons = post(treff, sti, """{"organisasjonsnummer":"$orgnr","navn":"Fiktiv testbedrift","næringskoder":[]$behov}""")
        assertThat(respons.statusCode()).withFailMessage(respons.body()).isEqualTo(201)
        return ctx.arbeidsgiverService.hentArbeidsgiver(treff, Orgnr(orgnr))!!.arbeidsgiverTreffId
    }

    private fun lagredeRom(treff: TreffId): Map<String, Int> = db.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT j.id::text, r.romnummer FROM jobbsoker_romtildeling r
            JOIN jobbsoker j ON j.jobbsoker_id = r.jobbsoker_id
            JOIN rekrutteringstreff t ON t.rekrutteringstreff_id = r.rekrutteringstreff_id
            WHERE t.id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) } }
        }
    }

    private fun lagretRotasjon(treff: TreffId): Map<String, Int> = db.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT a.id::text, r.forste_romnummer FROM arbeidsgiver_rotasjon r
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = r.arbeidsgiver_id
            JOIN rekrutteringstreff t ON t.rekrutteringstreff_id = a.rekrutteringstreff_id
            WHERE t.id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) } }
        }
    }

    private fun fjernLagretRom(person: PersonTreffId) = db.dataSource.connection.use { connection ->
        connection.prepareStatement(
            "DELETE FROM jobbsoker_romtildeling WHERE jobbsoker_id = (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?)"
        ).use { stmt ->
            stmt.setObject(1, person.somUuid)
            stmt.executeUpdate()
        }
    }

    private fun <T> medVentendeOperasjon(
        treff: TreffId,
        operasjon: () -> T,
        førFrigivelse: (Connection) -> Unit,
    ): T = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
        db.dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.låsTreff(treff)
            val pid = connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT pg_backend_pid()").use { it.next(); it.getInt(1) }
            }
            val ventende = executor.submit<T> { operasjon() }
            try {
                val frist = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var venter = false
                while (!venter && System.nanoTime() < frist) {
                    venter = db.dataSource.connection.use { sjekk ->
                        sjekk.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))").use { stmt ->
                            stmt.setInt(1, pid)
                            stmt.executeQuery().use { it.next(); it.getBoolean(1) }
                        }
                    }
                    if (!venter) Thread.sleep(10)
                }
                assertThat(venter).withFailMessage("Operasjonen tok ikke trefflåsen").isTrue()
                førFrigivelse(connection)
                connection.commit()
            } finally {
                connection.rollback()
            }
            ventende.get(10, TimeUnit.SECONDS)
        }
    }

    private fun aktivArbeidsgiver(treffId: TreffId): ArbeidsgiverTreffId = db.dataSource.connection.use { conn ->
        val sql = """
            SELECT a.id::text
            FROM arbeidsgiver a
            JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = a.rekrutteringstreff_id
            WHERE rt.id = ? AND a.status = 'AKTIV'
            ORDER BY a.arbeidsgiver_id
            LIMIT 1
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.executeQuery().use { rs ->
                rs.next()
                ArbeidsgiverTreffId(rs.getString(1))
            }
        }
    }


    private fun møteoppsett(treffId: TreffId, start: String = "09:00", varighet: Int = 15) =
        put(treffId, "/treffgjennomforing/moteoppsett", """{"starttidspunkt":"$start","varighetPerMøteMinutter":$varighet}""")

    private fun interesse(treffId: TreffId, person: PersonTreffId, arbeidsgiver: ArbeidsgiverTreffId, interessert: Boolean) =
        put(
            treffId, "/treffgjennomforing/interesse",
            """{"personTreffId":"${person.somString}","arbeidsgiverTreffId":"${arbeidsgiver.somString}","interessert":$interessert}""",
        )

    private fun vurdering(treffId: TreffId, body: String) = put(treffId, "/oppfolging/vurderinger", body)

    /** Bygger hele bodyen, slik at testene slipper å skjøte sammen JSON-fragmenter. */
    private fun vurderingFor(
        treffId: TreffId,
        person: PersonTreffId,
        arbeidsgiver: ArbeidsgiverTreffId,
        felter: String,
    ) = vurdering(
        treffId,
        """{"personTreffId":"${person.somString}","arbeidsgiverTreffId":"${arbeidsgiver.somString}"$felter}""",
    )

    /**
     * Feiler med statuskode og body framfor en NullPointerException på et felt
     * som mangler. Uten dette forteller en 403 deg bare at `get(...)` ga null.
     */
    private fun aggregat(treffId: TreffId): JsonNode {
        val respons = hent(treffId, eier)
        assertThat(respons.statusCode())
            .withFailMessage("Forventet 200 fra treffgjennomføringen, fikk %d. Body: %s", respons.statusCode(), respons.body())
            .isEqualTo(200)
        return mapper.readTree(respons.body())
    }

    private fun antallArbeidsgiverhendelser(treffId: TreffId): Int =
        db.hentArbeidsgiverHendelser(treffId).size

    private fun antallJobbsøkerhendelser(treffId: TreffId): Int =
        db.hentJobbsøkerHendelser(treffId).size

    private fun antallTreffHendelser(treffId: TreffId, hendelsestype: String): Int =
        db.hentHendelser(treffId).count { it.hendelsestype.name == hendelsestype }

    private fun workOpTreff(antallArbeidsgivere: Int = 1): TreffId =
        treff(RekrutteringstreffKategori.WORKOP, antallArbeidsgivere)

    private fun vanligTreff(antallArbeidsgivere: Int = 1): TreffId =
        treff(RekrutteringstreffKategori.REKRUTTERINGSTREFF, antallArbeidsgivere)

    private fun treff(kategori: RekrutteringstreffKategori, antallArbeidsgivere: Int): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = eier, kategori = kategori)
        ctx.eierRepository.leggTil(treffId, eier, "0315")
        repeat(antallArbeidsgivere) { arbeidsgiver(treffId, "99999999${it + 1}") }
        return treffId
    }

    private fun arbeidsgiver(treffId: TreffId, orgnr: String = "999999991"): ArbeidsgiverTreffId =
        db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Testbedrift $orgnr"), emptyList(), null, null, null),
            treffId,
        )

    private fun jobbsøker(treffId: TreffId, fnr: String = "12345678901"): PersonTreffId =
        db.leggTilJobbsøkereMedHendelse(
            listOf(LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Testesen"))),
            treffId,
        ).first()

    private fun oppmøteliste(treffId: TreffId): List<String> =
        aggregat(treffId)["oppmøte"].map { it.asText() }

    private fun deltakernummer(treffId: TreffId): Map<String, Int> =
        aggregat(treffId)["deltakernummer"]
            .associate { it["personTreffId"].asText() to it["deltakernummer"].asInt() }

    private fun antallTreffgjennomføringsrader(): Int = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM treffgjennomforing").executeQuery().use {
            it.next(); it.getInt(1)
        }
    }

    private fun antallHendelser(treffId: TreffId, hendelsestype: String): Int =
        db.hentJobbsøkerHendelser(treffId).count { it.hendelsestype.name == hendelsestype }

    private fun hent(
        treffId: TreffId,
        navIdent: String,
        grupper: List<UUID> = listOf(arbeidsgiverrettet),
    ): HttpResponse<String> = send(
        HttpRequest.newBuilder().GET(),
        "/api/rekrutteringstreff/${treffId.somString}/treffgjennomforing-og-oppfolging",
        navIdent,
        grupper,
    )

    private fun oppmøte(
        treffId: TreffId,
        personTreffId: PersonTreffId,
        møtt: Boolean,
    ): HttpResponse<String> = put(
        treffId, "/treffgjennomforing/oppmote",
        """{"personTreffId":"${personTreffId.somString}","møtt":$møtt}""",
    )

    private fun flyttTilRom(treffId: TreffId, personTreffId: PersonTreffId, romnummer: Int): HttpResponse<String> =
        put(treffId, "/treffgjennomforing/romfordeling/${personTreffId.somString}", """{"romnummer":$romnummer}""")

    private fun slettArbeidsgiver(treffId: TreffId, arbeidsgiverTreffId: ArbeidsgiverTreffId): HttpResponse<String> = send(
        HttpRequest.newBuilder().DELETE(),
        "/api/rekrutteringstreff/${treffId.somString}/arbeidsgiver/${arbeidsgiverTreffId.somString}",
        eier,
        listOf(arbeidsgiverrettet),
    )

    private fun put(treffId: TreffId, sti: String, body: String): HttpResponse<String> = send(
        HttpRequest.newBuilder().PUT(HttpRequest.BodyPublishers.ofString(body)),
        "/api/rekrutteringstreff/${treffId.somString}$sti",
        eier,
        listOf(arbeidsgiverrettet),
    )

    private fun post(treffId: TreffId, sti: String, body: String = "{}"): HttpResponse<String> = send(
        HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(body)),
        "/api/rekrutteringstreff/${treffId.somString}$sti",
        eier,
        listOf(arbeidsgiverrettet),
    )

    private fun send(
        builder: HttpRequest.Builder,
        sti: String,
        navIdent: String,
        grupper: List<UUID>,
    ): HttpResponse<String> {
        val token = infra.authServer.lagToken(infra.authPort, navIdent = navIdent, groups = grupper)
        val request = builder
            .uri(URI.create("http://localhost:$appPort$sti"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
