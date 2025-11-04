package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.dto.EndringerDto
import no.nav.toi.rekrutteringstreff.dto.Endringsfelt
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortTreffEndretSchedulerTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var aktivitetskortRepository: AktivitetskortRepository
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        aktivitetskortRepository = AktivitetskortRepository(db.dataSource)
        rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @Test
    fun `skal sende oppdatering på rapid når relevante felt er endret`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortTreffEndretScheduler(
            aktivitetskortRepository,
            rekrutteringstreffRepository,
            rapid,
            mapper
        )

        // Opprett treff og jobbsøker
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

        // Oppdater treffet med ny tittel
        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"

        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)

        // Registrer endring som ville blitt gjort av RekrutteringstreffService
        val endringer = EndringerDto(
            tittel = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        // Behandle endringer
        scheduler.behandleTreffEndringer()

        // Verifiser at melding ble sendt
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())
        assertThat(melding["fnr"].asText()).isEqualTo(fødselsnummer.asString)
        assertThat(melding["tittel"].asText()).isEqualTo(nyTittel)

        // Verifiser at hendelsen er markert som sendt
        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal sende oppdatering når fraTid eller tilTid endres`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortTreffEndretScheduler(
            aktivitetskortRepository,
            rekrutteringstreffRepository,
            rapid,
            mapper
        )

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

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyFraTid = ZonedDateTime.now().plusDays(5)
        db.oppdaterRekrutteringstreff(treffId, fraTid = nyFraTid)

        val endringer = EndringerDto(
            fraTid = Endringsfelt(gammelVerdi = treff.fraTid.toString(), nyVerdi = nyFraTid.toString())
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleTreffEndringer()

        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
    }

    @Test
    fun `skal sende oppdatering når adressefelter endres`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortTreffEndretScheduler(
            aktivitetskortRepository,
            rekrutteringstreffRepository,
            rapid,
            mapper
        )

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

        scheduler.behandleTreffEndringer()

        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppdatering")
        assertThat(melding["gateadresse"].asText()).isEqualTo(nyGateadresse)
        assertThat(melding["postnummer"].asText()).isEqualTo(nyPostnummer)
        assertThat(melding["poststed"].asText()).isEqualTo(nyPoststed)
    }

    @Test
    fun `skal ikke sende oppdatering når kun irrelevante felt er endret`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortTreffEndretScheduler(
            aktivitetskortRepository,
            rekrutteringstreffRepository,
            rapid,
            mapper
        )

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

        // Endre kun beskrivelse (ikke relevant for aktivitetskort)
        val endringer = EndringerDto(
            innlegg = Endringsfelt(gammelVerdi = "Gammelt innlegg", nyVerdi = "Nytt innlegg")
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleTreffEndringer()

        // Skal ikke sende noe på rapid
        assertThat(rapid.inspektør.size).isEqualTo(0)

        // Men hendelsen skal være markert som behandlet
        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende oppdatering når nyVerdi ikke matcher database`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortTreffEndretScheduler(
            aktivitetskortRepository,
            rekrutteringstreffRepository,
            rapid,
            mapper
        )

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

        val treff = rekrutteringstreffRepository.hent(treffId)!!

        // Registrer endring med feil nyVerdi (ikke lik det som faktisk er i databasen)
        val endringer = EndringerDto(
            tittel = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = "Feil tittel")
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleTreffEndringer()

        // Skal ikke sende noe på rapid
        assertThat(rapid.inspektør.size).isEqualTo(0)

        // Hendelsen skal IKKE være markert som behandlet siden verifikasjon feilet
        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(usendteEtterpå).hasSize(1)
    }

    @Test
    fun `skal ikke sende samme oppdatering to ganger`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortTreffEndretScheduler(
            aktivitetskortRepository,
            rekrutteringstreffRepository,
            rapid,
            mapper
        )

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

        val treff = rekrutteringstreffRepository.hent(treffId)!!
        val nyTittel = "Ny tittel"
        db.oppdaterRekrutteringstreff(treffId, tittel = nyTittel)

        val endringer = EndringerDto(
            tittel = Endringsfelt(gammelVerdi = treff.tittel, nyVerdi = nyTittel)
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleTreffEndringer()
        scheduler.behandleTreffEndringer()

        // Skal bare sendes én gang
        assertThat(rapid.inspektør.size).isEqualTo(1)
    }

    @Test
    fun `skal ikke gjøre noe hvis det ikke er noen usendte endringer`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortTreffEndretScheduler(
            aktivitetskortRepository,
            rekrutteringstreffRepository,
            rapid,
            mapper
        )

        scheduler.behandleTreffEndringer()

        assertThat(rapid.inspektør.size).isEqualTo(0)
    }
}

