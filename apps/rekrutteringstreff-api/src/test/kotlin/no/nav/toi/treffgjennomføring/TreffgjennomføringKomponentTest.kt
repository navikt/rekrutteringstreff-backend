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
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class TreffgjennomføringKomponentTest {

    private val db = TestDatabase()
    private val appPort = ubruktPortnrFra10000.ubruktPortnr()
    private val mapper = JacksonConfig.mapper

    private lateinit var infra: TestInfrastructureContext
    private lateinit var ctx: ApplicationContext
    private lateinit var app: App

    private val eier = "A100001"
    private val ikkeEier = "A200002"

    @BeforeAll
    fun setUp(wmInfo: WireMockRuntimeInfo) {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        infra = TestInfrastructureContext(dataSource = db.dataSource, modiaKlientUrl = wmInfo.httpBaseUrl)
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
            .containsExactlyInAnyOrder(p1.somString, p2.somString)
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
    fun `fjerning av oppmøte med registreringer gir 409 uten bekreftelse og uten sideeffekt`() {
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
    fun `bekreftet fjerning sletter registreringene og gir kun oppmøtehendelsen`() {
        val treff = workOpTreff()
        val person = jobbsøker(treff)
        val ag = aktivArbeidsgiver(treff)
        oppmøte(treff, person, møtt = true)
        interesse(treff, person, ag, interessert = true)

        assertThat(oppmøte(treff, person, møtt = false, bekreft = true).statusCode()).isEqualTo(200)

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
    fun `romfordeling erstatter plasseringene`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        oppmøte(treff, p1, møtt = true)
        oppmøte(treff, p2, møtt = true)
        møteoppsett(treff)

        val nyFordeling = """[{"romnummer":1,"jobbsøkere":["${p1.somString}","${p2.somString}"]},{"romnummer":2,"jobbsøkere":[]}]"""
        assertThat(put(treff, "/treffgjennomforing/romfordeling", nyFordeling).statusCode()).isEqualTo(200)

        val rom = aggregat(treff)["rom"]
        assertThat(rom.first { it["romnummer"].asInt() == 1 }["jobbsøkere"].map { it.asText() })
            .containsExactly(p1.somString, p2.somString)
        assertThat(rom.first { it["romnummer"].asInt() == 2 }["jobbsøkere"]).isEmpty()
    }

    @Test
    fun `romfordeling avvises med feil antall rom`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)

        val ettRom = """[{"romnummer":1,"jobbsøkere":["${person.somString}"]}]"""
        assertThat(put(treff, "/treffgjennomforing/romfordeling", ettRom).statusCode()).isEqualTo(400)
    }

    @Test
    fun `romfordeling avviser samme person i to rom`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)

        val dobbelt = """
            [{"romnummer":1,"jobbsøkere":["${person.somString}"]},{"romnummer":2,"jobbsøkere":["${person.somString}"]}]
        """.trimIndent()
        assertThat(put(treff, "/treffgjennomforing/romfordeling", dobbelt).statusCode()).isEqualTo(400)
    }

    @Test
    fun `romfordeling avviser person som ikke er fremmøtt`() {
        val treff = workOpTreff(antallArbeidsgivere = 2)
        val person = jobbsøker(treff)
        val hjemme = jobbsøker(treff, "22222222222")
        oppmøte(treff, person, møtt = true)
        møteoppsett(treff)

        val medHjemme = """
            [{"romnummer":1,"jobbsøkere":["${person.somString}","${hjemme.somString}"]},{"romnummer":2,"jobbsøkere":[]}]
        """.trimIndent()
        assertThat(put(treff, "/treffgjennomforing/romfordeling", medHjemme).statusCode()).isEqualTo(400)
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

    // --- hjelpere -------------------------------------------------------------

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
        ctx.eierRepository.leggTil(treffId, listOf(eier))
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
        bekreft: Boolean = false,
    ): HttpResponse<String> = put(
        treffId, "/treffgjennomforing/oppmote",
        """{"personTreffId":"${personTreffId.somString}","møtt":$møtt,"bekreftSlettRegistreringer":$bekreft}""",
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
