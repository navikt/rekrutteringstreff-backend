package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.*
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.dto.EndringerDto
import no.nav.toi.rekrutteringstreff.dto.Endringsfelt
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
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
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        aktivitetskortRepository = AktivitetskortRepository(db.dataSource)
        rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
        arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
        rekrutteringstreffService = RekrutteringstreffService(db.dataSource, rekrutteringstreffRepository, jobbsøkerRepository, arbeidsgiverRepository)
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    // ==================== INVITASJONSTESTER ====================

    @Test
    fun `skal sende invitasjoner på rapid og markere dem som pollet dersom vi har nok data`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, "Z123456")

        scheduler.behandleJobbsøkerHendelser()
        assertThat(rapid.inspektør.size).isEqualTo(2)
        val varselMelding = rapid.inspektør.message(0)
        assertThat(varselMelding["@event_name"].asText()).isEqualTo("kandidatInvitert")
        assertThat(varselMelding["varselId"]).isNotNull
        assertThat(varselMelding["fnr"].asText()).isEqualTo(fødselsnummer.asString)

        val kortMelding = rapid.inspektør.message(1)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(kortMelding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())

        val usendteEtterpå = aktivitetskortRepository.hentUsendteInvitasjoner()
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende invitasjoner på rapid dersom vi mangler prerequisites for invitasjon`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, "Z123456")

        val exception = assertThrows<IllegalArgumentException> {
            scheduler.behandleJobbsøkerHendelser()
        }
        assertThat(exception.message).isEqualTo("FraTid kan ikke være null når vi inviterer")
    }

    @Test
    fun `skal ikke sende samme invitasjon to ganger`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, "Z123456")

        scheduler.behandleJobbsøkerHendelser()
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)
    }

    // ==================== SVARTESTER ====================

    @Test
    fun `skal sende ja-svar på rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(expectedFnr, rapid, scheduler)

        jobbsøkerRepository.svarJaTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(3)  // 1 invitasjon varsel + 1 invitasjon kort + 1 svar
        val melding = rapid.inspektør.message(2)
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
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(expectedFnr, rapid, scheduler)

        jobbsøkerRepository.svarNeiTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(3)  // 1 invitasjon varsel + 1 invitasjon kort + 1 svar
        val melding = rapid.inspektør.message(2)
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
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(fødselsnummer, rapid, scheduler)

        jobbsøkerRepository.svarJaTilInvitasjon(fødselsnummer, treffId, fødselsnummer.asString)

        scheduler.behandleJobbsøkerHendelser()
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(3)  // 1 invitasjon varsel + 1 invitasjon kort + 1 svar (ikke duplikater)
    }

    // ==================== TREFF ENDRET TESTER ====================

    @Test
    fun `skal sende oppdatering av aktivitetskort OG minside-varsel når relevante felt er endret`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()  // Send invitasjon først

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"

        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)

        val endringer = EndringerDto(
            tittel = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)  // 1 invitasjon varsel + 1 invitasjon kort + 1 endring varsel + 1 oppdatering kort
        
        val varselMelding = rapid.inspektør.message(2)
        assertThat(varselMelding["@event_name"].asText()).isEqualTo("kandidatInvitertTreffEndret")

        val kortMelding = rapid.inspektør.message(3)
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
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyFraTid = ZonedDateTime.now().plusDays(5)
        db.oppdaterRekrutteringstreff(treffId, fraTid = nyFraTid)

        val endringer = EndringerDto(
            fraTid = Endringsfelt(gammelVerdi = treff.fraTid.toString(), nyVerdi = nyFraTid.toString())
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)
        val varselMelding = rapid.inspektør.message(2)
        assertThat(varselMelding["@event_name"].asText()).isEqualTo("kandidatInvitertTreffEndret")

        val kortMelding = rapid.inspektør.message(3)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
    }

    @Test
    fun `skal sende oppdatering av aktivitetskort OG minside-varsel når adressefelter endres`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

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

        val endringer = EndringerDto(
            gateadresse = Endringsfelt(gammelVerdi = treff.gateadresse, nyVerdi = nyGateadresse),
            postnummer = Endringsfelt(gammelVerdi = treff.postnummer, nyVerdi = nyPostnummer),
            poststed = Endringsfelt(gammelVerdi = treff.poststed, nyVerdi = nyPoststed)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)
        val varselMelding = rapid.inspektør.message(2)
        assertThat(varselMelding["@event_name"].asText()).isEqualTo("kandidatInvitertTreffEndret")
        assertThat(varselMelding["varselId"]).isNotNull
        assertThat(varselMelding["fnr"].asText()).isEqualTo(fødselsnummer.asString)

        val kortMelding = rapid.inspektør.message(3)
        assertThat(kortMelding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(kortMelding["gateadresse"].asText()).isEqualTo(nyGateadresse)
        assertThat(kortMelding["postnummer"].asText()).isEqualTo(nyPostnummer)
        assertThat(kortMelding["poststed"].asText()).isEqualTo(nyPoststed)
    }

    @Test
    fun `skal IKKE sende oppdatering av aktivitetskort MEN sende minside-varsel når kun irrelevante felt er endret`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val endringer = EndringerDto(
            innlegg = Endringsfelt(gammelVerdi = "Gammelt innlegg", nyVerdi = "Nytt innlegg")
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(3)  // Invitasjon varsel + invitasjon kort + endring varsel

        val varselMelding = rapid.inspektør.message(2)
        assertThat(varselMelding["@event_name"].asText()).isEqualTo("kandidatInvitertTreffEndret")

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal sende oppdatering av aktivitetskort OG minside-varsel selv når nyVerdi ikke matcher database(det kommer logg i steden for exepeption)`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!

        val endringer = EndringerDto(
            tittel = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = "Feil tittel")
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        // Skal sende oppdatering med faktiske verdier fra database, selv om verifiseringen feiler
        assertThat(rapid.inspektør.size).isEqualTo(4)  // Invitasjon varsel + invitasjon kort + endring varsel + oppdatering kort
        
        val varselMelding = rapid.inspektør.message(2)
        assertThat(varselMelding["@event_name"].asText()).isEqualTo("kandidatInvitertTreffEndret")

        val kortMelding = rapid.inspektør.message(3)
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
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"
        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)

        val endringer = EndringerDto(
            tittel = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)  // 2 invitasjon + 2 oppdatering
    }

    // ==================== TREFFSTATUS ENDRET TESTER ====================

    @Test
    fun `skal sende avlyst-status paa rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(expectedFnr, rapid, scheduler)

        jobbsøkerRepository.svarJaTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        rekrutteringstreffService.avlys(treffId, expectedFnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)  // invitasjon varsel + invitasjon kort + svar + status
        val melding = rapid.inspektør.message(3)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
        assertThat(melding["svar"].asBoolean()).isTrue
        assertThat(melding["treffstatus"].asText()).isEqualTo("avlyst")

        val usendteEtterpaa = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
        assertThat(usendteEtterpaa).isEmpty()
    }

    @Test
    fun `skal sende fullfort-status paa rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(expectedFnr, rapid, scheduler)

        jobbsøkerRepository.svarJaTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        rekrutteringstreffService.fullfør(treffId, expectedFnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)  // invitasjon varsel + invitasjon kort + svar + status
        val melding = rapid.inspektør.message(3)
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
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = opprettPersonOgInviter(fnr, rapid, scheduler)

        jobbsøkerRepository.svarJaTilInvitasjon(fnr, treffId, fnr.asString)
        scheduler.behandleJobbsøkerHendelser()

        rekrutteringstreffService.avlys(treffId, fnr.asString)
        scheduler.behandleJobbsøkerHendelser()
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(4)  // invitasjon varsel + invitasjon kort + svar + status (ikke duplikat)
    }

    // ==================== REKKEFØLGE TESTER ====================

    @Test
    fun `skal behandle hendelser i riktig rekkefølge basert på tidspunkt`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")

        // Opprett flere hendelser i database
        opprettOgInviterJobbsøker(treffId, fødselsnummer)  // Hendelse 1: INVITERT
        jobbsøkerRepository.svarJaTilInvitasjon(fødselsnummer, treffId, fødselsnummer.asString)  // Hendelse 2: SVART_JA

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Endret tittel"
        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)
        val endringer = EndringerDto(tittel = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel))
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)  // Hendelse 3: TREFF_ENDRET

        rekrutteringstreffService.fullfør(treffId, fødselsnummer.asString)  // Hendelse 4: SVART_JA_TREFF_FULLFØRT

        // Kjør scheduler én gang - skal behandle alle i riktig rekkefølge
        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(6)
        assertThat(rapid.inspektør.message(0)["@event_name"].asText()).isEqualTo("kandidatInvitert")
        assertThat(rapid.inspektør.message(1)["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(rapid.inspektør.message(2)["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(rapid.inspektør.message(3)["@event_name"].asText()).isEqualTo("kandidatInvitertTreffEndret")
        assertThat(rapid.inspektør.message(4)["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(rapid.inspektør.message(5)["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
    }

    @Test
    fun `skal ikke gjøre noe hvis det ikke er noen usendte hendelser`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(0)
    }

    // ==================== IKKE-SVART TESTER ====================

    @Test
    fun `skal sende avbrutt-status for jobbsøker med kun INVITERT når treff fullføres`() {
        val fnrSvartJa = Fødselsnummer("12345678901")
        val fnrIkkeSvart = Fødselsnummer("12345678902")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        // Person 1: Svarer ja
        opprettOgInviterJobbsøker(treffId, fnrSvartJa)
        jobbsøkerRepository.svarJaTilInvitasjon(fnrSvartJa, treffId, fnrSvartJa.asString)

        // Person 2: Blir invitert men svarer ikke
        opprettOgInviterJobbsøker(treffId, fnrIkkeSvart)

        scheduler.behandleJobbsøkerHendelser()  // Send invitasjoner og svar

        // Fullfør treffet
        rekrutteringstreffService.fullfør(treffId, fnrSvartJa.asString)
        scheduler.behandleJobbsøkerHendelser()

        // Verifiser at det sendes hendelser for begge personer
        // Person 1: invitasjon varsel + invitasjon kort + svar + fullført
        // Person 2: invitasjon varsel + invitasjon kort + avbrutt
        assertThat(rapid.inspektør.size).isEqualTo(7)

        // Finn hendelser for hver person
        val hendelserForSvartJa = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrSvartJa.asString }

        val hendelserForIkkeSvart = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrIkkeSvart.asString }

        // Person som svarte ja skal få fullført-status
        assertThat(hendelserForSvartJa).hasSize(4)
        assertThat(hendelserForSvartJa.last()["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(hendelserForSvartJa.last()["svar"].asBoolean()).isTrue
        assertThat(hendelserForSvartJa.last()["treffstatus"].asText()).isEqualTo("fullført")

        // Person som ikke svarte skal få rekrutteringstreffSvarOgStatus med fullført treffstatus
        assertThat(hendelserForIkkeSvart).hasSize(3)
        assertThat(hendelserForIkkeSvart[0]["@event_name"].asText()).isEqualTo("kandidatInvitert")
        assertThat(hendelserForIkkeSvart[1]["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(hendelserForIkkeSvart[2]["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(hendelserForIkkeSvart[2].has("svar")).isFalse
        assertThat(hendelserForIkkeSvart[2]["treffstatus"].asText()).isEqualTo("fullført")

        // Verifiser at hendelsene er markert som behandlet
        val usendteFullført = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)
        assertThat(usendteFullført).isEmpty()
    }

    @Test
    fun `skal sende avbrutt-status for jobbsøker med kun INVITERT når treff avlyses`() {
        val fnrSvartJa = Fødselsnummer("12345678901")
        val fnrIkkeSvart = Fødselsnummer("12345678902")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        // Person 1: Svarer ja
        opprettOgInviterJobbsøker(treffId, fnrSvartJa)
        jobbsøkerRepository.svarJaTilInvitasjon(fnrSvartJa, treffId, fnrSvartJa.asString)

        // Person 2: Blir invitert men svarer ikke
        opprettOgInviterJobbsøker(treffId, fnrIkkeSvart)

        scheduler.behandleJobbsøkerHendelser()

        // Avlys treffet
        rekrutteringstreffService.avlys(treffId, fnrSvartJa.asString)
        scheduler.behandleJobbsøkerHendelser()

        // Verifiser at begge personer får avbrutt-status
        assertThat(rapid.inspektør.size).isEqualTo(7)

        val hendelserForSvartJa = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrSvartJa.asString }

        val hendelserForIkkeSvart = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrIkkeSvart.asString }

        // Person som svarte ja skal få avlyst-status
        assertThat(hendelserForSvartJa.last()["treffstatus"].asText()).isEqualTo("avlyst")

        // Person som ikke svarte skal få rekrutteringstreffSvarOgStatus med avlyst treffstatus
        assertThat(hendelserForIkkeSvart.last()["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(hendelserForIkkeSvart.last().has("svar")).isFalse
        assertThat(hendelserForIkkeSvart.last()["treffstatus"].asText()).isEqualTo("avlyst")
    }

    @Test
    fun `skal ikke sende avbrutt-status for jobbsøker som har svart nei når treff fullføres`() {
        val fnrSvartNei = Fødselsnummer("12345678901")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        // Person svarer nei
        opprettOgInviterJobbsøker(treffId, fnrSvartNei)
        jobbsøkerRepository.svarNeiTilInvitasjon(fnrSvartNei, treffId, fnrSvartNei.asString)

        scheduler.behandleJobbsøkerHendelser()

        // Fullfør treffet - skal ikke sende noen ekstra hendelse for person som svarte nei
        rekrutteringstreffService.fullfør(treffId, fnrSvartNei.asString)
        scheduler.behandleJobbsøkerHendelser()

        // Skal kun være invitasjon varsel + invitasjon kort + svar nei (ikke noen treffstatus-endring)
        assertThat(rapid.inspektør.size).isEqualTo(3)
        assertThat(rapid.inspektør.message(0)["@event_name"].asText()).isEqualTo("kandidatInvitert")
        assertThat(rapid.inspektør.message(1)["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(rapid.inspektør.message(2)["@event_name"].asText()).isEqualTo("rekrutteringstreffSvarOgStatus")
        assertThat(rapid.inspektør.message(2)["svar"].asBoolean()).isFalse
    }

    @Test
    fun `skal håndtere flere jobbsøkere med kun INVITERT når treff fullføres`() {
        val fnrSvartJa = Fødselsnummer("12345678901")
        val fnrIkkeSvart1 = Fødselsnummer("12345678902")
        val fnrIkkeSvart2 = Fødselsnummer("12345678903")
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        // Person 1: Svarer ja
        opprettOgInviterJobbsøker(treffId, fnrSvartJa)
        jobbsøkerRepository.svarJaTilInvitasjon(fnrSvartJa, treffId, fnrSvartJa.asString)

        // Person 2 og 3: Blir invitert men svarer ikke
        opprettOgInviterJobbsøker(treffId, fnrIkkeSvart1)
        opprettOgInviterJobbsøker(treffId, fnrIkkeSvart2)

        scheduler.behandleJobbsøkerHendelser()

        // Fullfør treffet
        rekrutteringstreffService.fullfør(treffId, fnrSvartJa.asString)
        scheduler.behandleJobbsøkerHendelser()

        // Verifiser: 3 invitasjoner varsel + 3 invitasjoner kort + 1 svar ja + 1 fullført + 2 avbrutt = 10
        assertThat(rapid.inspektør.size).isEqualTo(10)

        val hendelserForIkkeSvart1 = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrIkkeSvart1.asString }

        val hendelserForIkkeSvart2 = (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it["fnr"].asText() == fnrIkkeSvart2.asString }

        // Begge personer som ikke svarte skal få ikkeSvartTreffstatusEndret med fullført treffstatus
        assertThat(hendelserForIkkeSvart1).hasSize(3)
        assertThat(hendelserForIkkeSvart1.last()["treffstatus"].asText()).isEqualTo("fullført")

        assertThat(hendelserForIkkeSvart2).hasSize(3)
        assertThat(hendelserForIkkeSvart2.last()["treffstatus"].asText()).isEqualTo("fullført")
    }

    // ==================== HJELPEMETODER ====================

    private fun opprettPersonOgInviter(fødselsnummer: Fødselsnummer, rapid: TestRapid, scheduler: AktivitetskortJobbsøkerScheduler): no.nav.toi.rekrutteringstreff.TreffId {
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        scheduler.behandleJobbsøkerHendelser()  // Send invitasjon
        return treffId
    }

    private fun opprettOgInviterJobbsøker(treffId: no.nav.toi.rekrutteringstreff.TreffId, fødselsnummer: Fødselsnummer) {
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, "Z123456")
    }
}

