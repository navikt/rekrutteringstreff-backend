package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.*
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
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        aktivitetskortRepository = AktivitetskortRepository(db.dataSource)
        rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
        rekrutteringstreffService = RekrutteringstreffService(db.dataSource, rekrutteringstreffRepository, jobbsøkerRepository)
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
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())

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

        assertThat(rapid.inspektør.size).isEqualTo(1)
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

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon + 1 svar
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffsvar")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isTrue
        assertThat(melding["svartJa"].asBoolean()).isTrue

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

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon + 1 svar
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffsvar")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isTrue
        assertThat(melding["svartJa"].asBoolean()).isFalse

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

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon + 1 svar (ikke duplikater)
    }

    // ==================== TREFF ENDRET TESTER ====================

    @Test
    fun `skal sende oppdatering på rapid når relevante felt er endret`() {
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

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon + 1 oppdatering
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())
        assertThat(melding["fnr"].asText()).isEqualTo(fødselsnummer.asString)
        assertThat(melding["tittel"].asText()).isEqualTo(nyTittel)

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal sende oppdatering når fraTid eller tilTid endres`() {
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

        assertThat(rapid.inspektør.size).isEqualTo(2)
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
    }

    @Test
    fun `skal sende oppdatering når adressefelter endres`() {
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

        assertThat(rapid.inspektør.size).isEqualTo(2)
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(melding["gateadresse"].asText()).isEqualTo(nyGateadresse)
        assertThat(melding["postnummer"].asText()).isEqualTo(nyPostnummer)
        assertThat(melding["poststed"].asText()).isEqualTo(nyPoststed)
    }

    @Test
    fun `skal ikke sende oppdatering når kun irrelevante felt er endret`() {
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

        assertThat(rapid.inspektør.size).isEqualTo(1)  // Kun invitasjonen

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal sende oppdatering selv når nyVerdi ikke matcher database`() {
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
        assertThat(rapid.inspektør.size).isEqualTo(2)  // Invitasjon + oppdatering
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        // Verifiser at faktiske verdier fra database sendes, ikke de feilaktige fra endringer
        assertThat(melding["tittel"].asText()).isEqualTo(treff.tittel)

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

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon + 1 oppdatering
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

        assertThat(rapid.inspektør.size).isEqualTo(3)  // invitasjon + svar + status
        val melding = rapid.inspektør.message(2)
        assertThat(melding["@event_name"].asText()).isEqualTo("svartJaTreffstatusEndret")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
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

        assertThat(rapid.inspektør.size).isEqualTo(3)  // invitasjon + svar + status
        val melding = rapid.inspektør.message(2)
        assertThat(melding["@event_name"].asText()).isEqualTo("svartJaTreffstatusEndret")
        assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
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

        assertThat(rapid.inspektør.size).isEqualTo(3)  // invitasjon + svar + status (ikke duplikat)
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

        assertThat(rapid.inspektør.size).isEqualTo(4)
        assertThat(rapid.inspektør.message(0)["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(rapid.inspektør.message(1)["@event_name"].asText()).isEqualTo("rekrutteringstreffsvar")
        assertThat(rapid.inspektør.message(2)["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(rapid.inspektør.message(3)["@event_name"].asText()).isEqualTo("svartJaTreffstatusEndret")
    }

    @Test
    fun `skal ikke gjøre noe hvis det ikke er noen usendte hendelser`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid, mapper)

        scheduler.behandleJobbsøkerHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(0)
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

