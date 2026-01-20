package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.TestRapid
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.*
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortJobbsøkerSchedulerTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var aktivitetskortRepository: AktivitetskortRepository
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var rekrutteringstreffService: RekrutteringstreffService
        private lateinit var arbeidsgiverRepository: ArbeidsgiverRepository
        private lateinit var jobbsøkerService: JobbsøkerService
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        aktivitetskortRepository = AktivitetskortRepository(db.dataSource)
        rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
        arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
        jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
        rekrutteringstreffService = RekrutteringstreffService(db.dataSource, rekrutteringstreffRepository, jobbsøkerRepository, arbeidsgiverRepository, jobbsøkerService)
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }


    @Test
    fun `skal sende invitasjoner på rapid og markere dem som pollet dersom vi har nok data`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        opprettOgInviterJobbsøker(treffId, fødselsnummer)

        scheduler.behandleJobbsøkerHendelser()
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val kortMelding = rapid.inspektør.message(0)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(kortMelding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende invitasjoner på rapid dersom vi mangler prerequisites for invitasjon`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        opprettOgInviterJobbsøker(treffId, fødselsnummer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(0)
        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        assertThat(usendteEtterpå).hasSize(1)
    }

    @Test
    fun `skal ikke sende samme invitasjon to ganger`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")
        opprettOgInviterJobbsøker(treffId, fødselsnummer)

        scheduler.behandleJobbsøkerHendelser()
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(1)
    }


    @Test
    fun `skal sende ja-svar på rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(expectedFnr, rapid, scheduler)

        jobbsøkerService.svarJaTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon kort + 1 svar
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isTrue
        assertThat(melding["svar"].asBoolean()).isTrue

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON)
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal sende nei-svar på rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(expectedFnr, rapid, scheduler)

        jobbsøkerService.svarNeiTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon kort + 1 svar
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isTrue
        assertThat(melding["svar"].asBoolean()).isFalse

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON)
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende samme svar to ganger`() {
        val fødselsnummer = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(fødselsnummer, rapid, scheduler)

        jobbsøkerService.svarJaTilInvitasjon(fødselsnummer, treffId, fødselsnummer.asString)

        scheduler.behandleJobbsøkerHendelser()
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon kort + 1 svar (ikke duplikater)
    }


    @Test
    fun `skal sende oppdatering av aktivitetskort OG minside-varsel når relevante felt er endret`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()  // Send invitasjon først

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"

        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)

        val endringer = Rekrutteringstreffendringer(
            navn = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon kort + 1 oppdatering kort

        val kortMelding = rapid.inspektør.message(1)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(kortMelding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())
        assertThat(kortMelding["fnr"].asText()).isEqualTo(fødselsnummer.asString)
        assertThat(kortMelding["tittel"].asText()).isEqualTo(nyTittel)

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal sende oppdatering av aktivitetskort OG minside-varsel når fraTid eller tilTid endres`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyFraTid = ZonedDateTime.now().plusDays(5)
        db.oppdaterRekrutteringstreff(treffId, fraTid = nyFraTid)

        val endringer = Rekrutteringstreffendringer(
            tidspunkt = Endringsfelt(gammelVerdi = treff.fraTid.toString(), nyVerdi = nyFraTid.toString())
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)
        val kortMelding = rapid.inspektør.message(1)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
    }

    @Test
    fun `skal sende oppdatering av aktivitetskort OG minside-varsel når adressefelter endres`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyGateadresse = "Ny gate 42"
        val nyPostnummer = "1234"
        val nyPoststed = "Nysted"
        db.oppdaterRekrutteringstreff(
            treffId,
            gateadresse = nyGateadresse,
            postnummer = nyPostnummer,
            poststed = nyPoststed
        )

        val endringer = Rekrutteringstreffendringer(
            sted = Endringsfelt(gammelVerdi = "${treff.gateadresse}, ${treff.postnummer}, ${treff.poststed}", nyVerdi = "$nyGateadresse, $nyPostnummer, $nyPoststed")
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)
        val kortMelding = rapid.inspektør.message(1)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(kortMelding["gateadresse"].asText()).isEqualTo(nyGateadresse)
        assertThat(kortMelding["postnummer"].asText()).isEqualTo(nyPostnummer)
        assertThat(kortMelding["poststed"].asText()).isEqualTo(nyPoststed)
    }

    @Test
    fun `skal sende oppdatering av aktivitetskort OG minside-varsel når kun irrelevante felt er endret`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val endringer = Rekrutteringstreffendringer(
            introduksjon = Endringsfelt(gammelVerdi = "Gammelt innlegg", nyVerdi = "Nytt innlegg")
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)  // Invitasjon kort + oppdatering kort

        val kortMelding = rapid.inspektør.message(1)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal sende oppdatering av aktivitetskort OG minside-varsel selv når nyVerdi ikke matcher database(det kommer logg i steden for exepeption)`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!

        val endringer = Rekrutteringstreffendringer(
            navn = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = "Feil tittel")
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        // Skal sende oppdatering med faktiske verdier fra database, selv om verifiseringen feiler
        assertThat(rapid.inspektør.size).isEqualTo(2)  // Invitasjon kort + oppdatering kort

        val kortMelding = rapid.inspektør.message(1)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        // Verifiser at faktiske verdier fra database sendes, ikke de feilaktige fra endringer
        assertThat(kortMelding["tittel"].asText()).isEqualTo(treff.tittel)

        // Hendelsen skal være markert som behandlet
        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende samme oppdatering to ganger`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"
        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)

        val endringer = Rekrutteringstreffendringer(
            navn = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon + 1 oppdatering
    }


    @Test
    fun `skal sende oppdatering med endredeFelter når skalVarsle er true for tittel`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"
        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)

        val endringer = Rekrutteringstreffendringer(
            navn = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel, skalVarsle = true)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        // Skal nå være 2 meldinger: invitasjon + én samlet oppdatering (med endredeFelter)
        assertThat(rapid.inspektør.size).isEqualTo(2)

        // Sjekk samlet oppdatering
        val oppdateringMelding = rapid.inspektør.message(1)
        assertThat(oppdateringMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(oppdateringMelding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())
        assertThat(oppdateringMelding["fnr"].asText()).isEqualTo(fødselsnummer.asString)
        assertThat(oppdateringMelding.has("endredeFelter")).isTrue
        assertThat(oppdateringMelding["endredeFelter"].map { it.asText() }).containsExactly("NAVN")
        // Skal også ha aktivitetskort-data
        assertThat(oppdateringMelding.has("tittel")).isTrue
        assertThat(oppdateringMelding.has("fraTid")).isTrue
    }

    @Test
    fun `skal sende oppdatering med TIDSPUNKT når fraTid har skalVarsle true`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyFraTid = ZonedDateTime.now().plusDays(5)
        db.oppdaterRekrutteringstreff(treffId, fraTid = nyFraTid)

        val endringer = Rekrutteringstreffendringer(
            tidspunkt = Endringsfelt(gammelVerdi = treff.fraTid.toString(), nyVerdi = nyFraTid.toString(), skalVarsle = true)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)

        val oppdateringMelding = rapid.inspektør.message(1)
        assertThat(oppdateringMelding["endredeFelter"].map { it.asText() }).containsExactly("TIDSPUNKT")
    }

    @Test
    fun `skal sende oppdatering med STED når gateadresse har skalVarsle true`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyGateadresse = "Malmøgata 2"
        db.oppdaterRekrutteringstreff(treffId, gateadresse = nyGateadresse)

        val endringer = Rekrutteringstreffendringer(
            sted = Endringsfelt(gammelVerdi = treff.gateadresse, nyVerdi = nyGateadresse, skalVarsle = true)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)

        val oppdateringMelding = rapid.inspektør.message(1)
        assertThat(oppdateringMelding["endredeFelter"].map { it.asText() }).containsExactly("STED")
    }

    @Test
    fun `skal sende oppdatering med flere endredeFelter når flere felt har skalVarsle true`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"
        val nyGateadresse = "Malmøgata 2"
        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel, gateadresse = nyGateadresse)

        val endringer = Rekrutteringstreffendringer(
            navn = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel, skalVarsle = true),
            sted = Endringsfelt(gammelVerdi = treff.gateadresse, nyVerdi = nyGateadresse, skalVarsle = true),
            introduksjon = Endringsfelt(gammelVerdi = "Gammelt innlegg", nyVerdi = "Nytt innlegg", skalVarsle = true)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)

        val oppdateringMelding = rapid.inspektør.message(1)
        assertThat(oppdateringMelding["endredeFelter"].map { it.asText() }).containsExactlyInAnyOrder("NAVN", "STED", "INTRODUKSJON")
    }

    @Test
    fun `skal ikke inkludere endredeFelter når ingen felt har skalVarsle true`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"
        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)

        // skalVarsle = false (standard)
        val endringer = Rekrutteringstreffendringer(
            navn = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel, skalVarsle = false)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        // Skal være 2 meldinger: invitasjon + oppdatering (uten endredeFelter)
        assertThat(rapid.inspektør.size).isEqualTo(2)

        // Verifiser at oppdatering sendes uten endredeFelter
        val oppdateringMelding = rapid.inspektør.message(1)
        assertThat(oppdateringMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(oppdateringMelding.has("endredeFelter")).isFalse
        // Skal fortsatt ha aktivitetskort-data
        assertThat(oppdateringMelding.has("tittel")).isTrue
    }

    @Test
    fun `skal sende oppdatering med SVARFRIST når svarfrist har skalVarsle true`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nySvarfrist = ZonedDateTime.now().plusDays(7)

        val endringer = Rekrutteringstreffendringer(
            svarfrist = Endringsfelt(gammelVerdi = treff.svarfrist?.toString(), nyVerdi = nySvarfrist.toString(), skalVarsle = true)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)

        val oppdateringMelding = rapid.inspektør.message(1)
        assertThat(oppdateringMelding["endredeFelter"].map { it.asText() }).containsExactly("SVARFRIST")
    }

    @Test
    fun `skal kombinere fraTid og tilTid til TIDSPUNKT endredeFelter-verdi`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyFraTid = ZonedDateTime.now().plusDays(5)
        val nyTilTid = ZonedDateTime.now().plusDays(5).plusHours(2)

        // Tidspunkt er allerede et sammenslått felt, så denne testen består fortsatt
        val endringer = Rekrutteringstreffendringer(
            tidspunkt = Endringsfelt(gammelVerdi = "${treff.fraTid} - ${treff.tilTid}", nyVerdi = "$nyFraTid - $nyTilTid", skalVarsle = true)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)

        val oppdateringMelding = rapid.inspektør.message(1)
        // Skal kun ha én TIDSPUNKT, ikke to
        assertThat(oppdateringMelding["endredeFelter"].map { it.asText() }).containsExactly("TIDSPUNKT")
    }

    @Test
    fun `skal kombinere gateadresse, postnummer og poststed til STED endredeFelter-verdi`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!

        // Sted er allerede et sammenslått felt, så denne testen består fortsatt
        val endringer = Rekrutteringstreffendringer(
            sted = Endringsfelt(gammelVerdi = "${treff.gateadresse}, ${treff.postnummer}, ${treff.poststed}", nyVerdi = "Malmøgata 2, 0566, Oslo", skalVarsle = true)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)

        val oppdateringMelding = rapid.inspektør.message(1)
        // Skal kun ha én STED, ikke tre
        assertThat(oppdateringMelding["endredeFelter"].map { it.asText() }).containsExactly("STED")
    }


    @Test
    fun `skal sende avlyst-status og avlysningsvarsel for jobbsøker som har svart ja`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(expectedFnr, rapid, scheduler)

        jobbsøkerService.svarJaTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        rekrutteringstreffService.avlys(treffId, expectedFnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)  // invitasjon kort + svar + status + avlysningsvarsel
        val melding = rapid.inspektør.message(2)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
        assertThat(melding["svar"].asBoolean()).isTrue
        assertThat(melding["treffstatus"].asText()).isEqualTo("avlyst")

        // Verifiser avlysningsmeldingen til kandidatvarsel-api
        val avlysningsMelding = rapid.inspektør.message(3)
        assertThat(avlysningsMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffavlysning")
        assertThat(avlysningsMelding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(avlysningsMelding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)

        val usendteEtterpaa = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
        assertThat(usendteEtterpaa).isEmpty()
    }

    @Test
    fun `skal sende fullfort-status paa rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(expectedFnr, rapid, scheduler)

        jobbsøkerService.svarJaTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        db.endreTilTidTilPassert(treffId, expectedFnr.asString)
        rekrutteringstreffService.publiser(treffId, expectedFnr.asString)
        rekrutteringstreffService.fullfør(treffId, expectedFnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(3)  // invitasjon kort + svar + status
        val melding = rapid.inspektør.message(2)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
        assertThat(melding["svar"].asBoolean()).isTrue
        assertThat(melding["treffstatus"].asText()).isEqualTo("fullført")

        val usendteEtterpaa = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)
        assertThat(usendteEtterpaa).isEmpty()
    }

    @Test
    fun `skal ikke sende samme treffstatus to ganger`() {
        val fnr = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(fnr, rapid, scheduler)

        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, fnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        rekrutteringstreffService.avlys(treffId, fnr.asString)
        scheduler.behandleJobbsøkerHendelser()
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)  // invitasjon kort + svar + status + avlysningsvarsel (ikke duplikat)
    }


    @Test
    fun `skal behandle hendelser i riktig rekkefølge basert på tidspunkt`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        // Opprett flere hendelser i database
        opprettOgInviterJobbsøker(treffId, fødselsnummer)  // Hendelse 1: INVITERT
        jobbsøkerService.svarJaTilInvitasjon(fødselsnummer, treffId, fødselsnummer.asString)  // Hendelse 2: SVART_JA

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Endret tittel"
        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)
        val endringer = Rekrutteringstreffendringer(navn = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel))
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)  // Hendelse 3: TREFF_ENDRET

        db.endreTilTidTilPassert(treffId, fødselsnummer.asString)
        rekrutteringstreffService.publiser(treffId, fødselsnummer.asString)
        rekrutteringstreffService.fullfør(treffId, fødselsnummer.asString)  // Hendelse 4: SVART_JA_TREFF_FULLFØRT

        // Kjør scheduler én gang - skal behandle alle i riktig rekkefølge
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)
        assertThat(rapid.inspektør.message(0)["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(rapid.inspektør.message(1)["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(rapid.inspektør.message(2)["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(rapid.inspektør.message(3)["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
    }

    @Test
    fun `skal ikke gjøre noe hvis det ikke er noen usendte hendelser`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(0)
    }


    @Test
    fun `skal sende avbrutt-status for jobbsøker med kun INVITERT når treff fullføres`() {
        val fnrSvartJa = Fødselsnummer("12345678901")
        val fnrIkkeSvart = Fødselsnummer("12345678902")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        // Person 1: Svarer ja
        opprettOgInviterJobbsøker(treffId, fnrSvartJa)
        jobbsøkerService.svarJaTilInvitasjon(fnrSvartJa, treffId, fnrSvartJa.asString)

        // Person 2: Blir invitert men svarer ikke
        opprettOgInviterJobbsøker(treffId, fnrIkkeSvart)

        scheduler.behandleJobbsøkerHendelser()  // Send invitasjoner og svar

        // Fullfør treffet
        db.endreTilTidTilPassert(treffId, fnrSvartJa.asString)
        rekrutteringstreffService.publiser(treffId, fnrSvartJa.asString)
        rekrutteringstreffService.fullfør(treffId, fnrSvartJa.asString)
        scheduler.behandleJobbsøkerHendelser()

        // Verifiser at det sendes hendelser for begge personer
        // Person 1: invitasjon kort + svar + fullført
        // Person 2: invitasjon kort + avbrutt
        assertThat(rapid.inspektør.size).isEqualTo(5)

        // Finn hendelser for hver person
        val hendelserForSvartJa = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrSvartJa.asString }

        val hendelserForIkkeSvart = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrIkkeSvart.asString }

        // Person som svarte ja skal få fullført-status
        assertThat(hendelserForSvartJa).hasSize(3)
        assertThat(hendelserForSvartJa.last()["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(hendelserForSvartJa.last()["svar"].asBoolean()).isTrue
        assertThat(hendelserForSvartJa.last()["treffstatus"].asText()).isEqualTo("fullført")

        // Person som ikke svarte skal få rekrutteringstreffSvarOgStatus med fullført treffstatus
        assertThat(hendelserForIkkeSvart).hasSize(2)
        assertThat(hendelserForIkkeSvart[0]["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(hendelserForIkkeSvart[1]["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(hendelserForIkkeSvart[1].has("svar")).isFalse
        assertThat(hendelserForIkkeSvart[1]["treffstatus"].asText()).isEqualTo("fullført")

        // Verifiser at hendelsene er markert som behandlet
        val usendteFullført = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)
        assertThat(usendteFullført).isEmpty()
    }

    @Test
    fun `skal sende avlysningsvarsel kun til jobbsøkere som har svart ja - ikke til kun inviterte`() {
        val fnrSvartJa = Fødselsnummer("12345678901")
        val fnrIkkeSvart = Fødselsnummer("12345678902")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        // Person 1: Svarer ja
        opprettOgInviterJobbsøker(treffId, fnrSvartJa)
        jobbsøkerService.svarJaTilInvitasjon(fnrSvartJa, treffId, fnrSvartJa.asString)

        // Person 2: Blir invitert men svarer ikke
        opprettOgInviterJobbsøker(treffId, fnrIkkeSvart)

        scheduler.behandleJobbsøkerHendelser()

        // Avlys treffet
        rekrutteringstreffService.avlys(treffId, fnrSvartJa.asString)
        scheduler.behandleJobbsøkerHendelser()

        // Verifiser at begge personer får avbrutt-status, men kun svart-ja får avlysningsvarsel
        // 6 meldinger: invitasjon (fnr1) + svar (fnr1) + invitasjon (fnr2) + status (fnr1) + avlysningsvarsel (fnr1) + status (fnr2)
        assertThat(rapid.inspektør.size).isEqualTo(6)

        val hendelserForSvartJa = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrSvartJa.asString }

        val hendelserForIkkeSvart = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrIkkeSvart.asString }

        // Person som svarte ja skal få avlyst-status og avlysningsvarsel
        val svarOgStatusMeldingForSvartJa = hendelserForSvartJa.find { it["@event_name"].asText() == "rekrutteringstreffSvarOgStatus" && it.has("treffstatus") }
        assertThat(svarOgStatusMeldingForSvartJa!!["treffstatus"].asText()).isEqualTo("avlyst")
        
        // Person som svarte ja skal også få avlysningsvarsel
        val avlysningsMeldingForSvartJa = hendelserForSvartJa.find { it["@event_name"].asText() == "rekrutteringstreffavlysning" }
        assertThat(avlysningsMeldingForSvartJa).isNotNull

        // Person som ikke svarte skal få rekrutteringstreffSvarOgStatus med avlyst treffstatus
        assertThat(hendelserForIkkeSvart.last()["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(hendelserForIkkeSvart.last().has("svar")).isFalse
        assertThat(hendelserForIkkeSvart.last()["treffstatus"].asText()).isEqualTo("avlyst")
    }

    @Test
    fun `skal ikke sende avbrutt-status for jobbsøker som har svart nei når treff fullføres`() {
        val fnrSvartNei = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        // Person svarer nei
        opprettOgInviterJobbsøker(treffId, fnrSvartNei)
        jobbsøkerService.svarNeiTilInvitasjon(fnrSvartNei, treffId, fnrSvartNei.asString)

        scheduler.behandleJobbsøkerHendelser()

        // Fullfør treffet - skal ikke sende noen ekstra hendelse for person som svarte nei
        db.endreTilTidTilPassert(treffId, fnrSvartNei.asString)
        rekrutteringstreffService.publiser(treffId, fnrSvartNei.asString)
        rekrutteringstreffService.fullfør(treffId, fnrSvartNei.asString)
        scheduler.behandleJobbsøkerHendelser()

        // Skal kun være invitasjon kort + svar nei (ikke noen treffstatus-endring)
        assertThat(rapid.inspektør.size).isEqualTo(2)
        assertThat(rapid.inspektør.message(0)["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(rapid.inspektør.message(1)["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(rapid.inspektør.message(1)["svar"].asBoolean()).isFalse
    }

    @Test
    fun `skal håndtere flere jobbsøkere med kun INVITERT når treff fullføres`() {
        val fnrSvartJa = Fødselsnummer("12345678901")
        val fnrIkkeSvart1 = Fødselsnummer("12345678902")
        val fnrIkkeSvart2 = Fødselsnummer("12345678903")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        // Person 1: Svarer ja
        opprettOgInviterJobbsøker(treffId, fnrSvartJa)
        jobbsøkerService.svarJaTilInvitasjon(fnrSvartJa, treffId, fnrSvartJa.asString)

        // Person 2 og 3: Blir invitert men svarer ikke
        opprettOgInviterJobbsøker(treffId, fnrIkkeSvart1)
        opprettOgInviterJobbsøker(treffId, fnrIkkeSvart2)

        scheduler.behandleJobbsøkerHendelser()

        // Fullfør treffet
        db.endreTilTidTilPassert(treffId, fnrSvartJa.asString)
        rekrutteringstreffService.publiser(treffId, fnrSvartJa.asString)
        rekrutteringstreffService.fullfør(treffId, fnrSvartJa.asString)
        scheduler.behandleJobbsøkerHendelser()

        // Verifiser: 3 invitasjoner kort + 1 svar ja + 1 fullført + 2 avbrutt = 7
        assertThat(rapid.inspektør.size).isEqualTo(7)

        val hendelserForIkkeSvart1 = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrIkkeSvart1.asString }

        val hendelserForIkkeSvart2 = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrIkkeSvart2.asString }

        // Begge personer som ikke svarte skal få ikkeSvartTreffstatusEndret med fullført treffstatus
        assertThat(hendelserForIkkeSvart1).hasSize(2)
        assertThat(hendelserForIkkeSvart1.last()["treffstatus"].asText()).isEqualTo("fullført")

        assertThat(hendelserForIkkeSvart2).hasSize(2)
        assertThat(hendelserForIkkeSvart2.last()["treffstatus"].asText()).isEqualTo("fullført")
    }


    private fun opprettPersonOgInviter(fødselsnummer: Fødselsnummer, rapid: TestRapid, scheduler: AktivitetskortJobbsøkerScheduler): no.nav.toi.rekrutteringstreff.TreffId {
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()  // Send invitasjon
        return treffId
    }

    private fun opprettOgInviterJobbsøker(treffId: no.nav.toi.rekrutteringstreff.TreffId, fødselsnummer: Fødselsnummer) {
        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerService.inviter(listOf(personTreffId), treffId, "Z123456")
    }
}

