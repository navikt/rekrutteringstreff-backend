package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortInvitasjonSchedulerTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var aktivitetskortInvitasjonRepository: AktivitetskortInvitasjonRepository
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private val mapper = JacksonConfig.mapper



    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        aktivitetskortInvitasjonRepository = AktivitetskortInvitasjonRepository(db.dataSource)
        rekrutteringstreffRepository =
            RekrutteringstreffRepository(db.dataSource)

    }


    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @Test
    fun `skal sende invitasjoner på rapid og markere dem som pollet`() {
        // Arrange
        val rapid = TestRapid()
        val scheduler =
            AktivitetskortInvitasjonScheduler(aktivitetskortInvitasjonRepository, rekrutteringstreffRepository, rapid)
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

        // Act
        scheduler.behandleInvitasjoner()
        // Assert
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())

        val usendteEtterpå = aktivitetskortInvitasjonRepository.hentUsendteInvitasjoner()
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende samme invitasjon to ganger`() {
        // Arrange
        val rapid = TestRapid()
        val scheduler =
            AktivitetskortInvitasjonScheduler(aktivitetskortInvitasjonRepository, rekrutteringstreffRepository, rapid)
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

        // Act
        scheduler.behandleInvitasjoner()
        scheduler.behandleInvitasjoner()

        // Assert
        assertThat(rapid.inspektør.size).isEqualTo(1)
    }

    @Test
    fun `skal ikke gjøre noe hvis det ikke er noen usendte invitasjoner`() {
        // Arrange
        val rapid = TestRapid()
        val scheduler =
            AktivitetskortInvitasjonScheduler(aktivitetskortInvitasjonRepository, rekrutteringstreffRepository, rapid)

        // Act
        scheduler.behandleInvitasjoner()

        // Assert
        assertThat(rapid.inspektør.size).isEqualTo(0)
    }
}