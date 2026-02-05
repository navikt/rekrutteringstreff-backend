package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: AktivitetskortRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
            repository = AktivitetskortRepository(db.dataSource)
        }
    }

    @AfterEach
    fun slettAlt() {
        db.slettAlt()
    }

    @Test
    fun `hentUsendteHendelse returnerer INVITERT-hendelser som ikke er pollet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")
        val jobbsøkere = db.hentJobbsøkereViaRepository(treffId)
        val personTreffId = jobbsøkere.first().personTreffId
        db.inviterJobbsøkere(listOf(personTreffId), treffId, "testperson")

        val usendteHendelser = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)

        assertThat(usendteHendelser).hasSize(1)
        assertThat(usendteHendelser.first().fnr).isEqualTo(fnr.asString)
        assertThat(usendteHendelser.first().rekrutteringstreffUuid).isEqualTo(treffId.somString)
    }

    @Test
    fun `hentUsendteHendelse returnerer tom liste når ingen hendelser finnes`() {
        val usendteHendelser = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)

        assertThat(usendteHendelser).isEmpty()
    }

    @Test
    fun `hentUsendteHendelse returnerer ikke hendelser som allerede er pollet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")
        val jobbsøkere = db.hentJobbsøkereViaRepository(treffId)
        val personTreffId = jobbsøkere.first().personTreffId
        db.inviterJobbsøkere(listOf(personTreffId), treffId, "testperson")

        val usendteHendelserFør = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        assertThat(usendteHendelserFør).hasSize(1)

        // Marker hendelsen som pollet via repository
        repository.lagrePollingstatus(usendteHendelserFør.first().jobbsokerHendelseDbId)

        val usendteHendelserEtter = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        assertThat(usendteHendelserEtter).isEmpty()
    }

    @Test
    fun `lagrePollingstatus lagrer pollingstatus for hendelse`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")
        val jobbsøkere = db.hentJobbsøkereViaRepository(treffId)
        val personTreffId = jobbsøkere.first().personTreffId
        db.inviterJobbsøkere(listOf(personTreffId), treffId, "testperson")

        val usendteHendelser = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        val hendelse = usendteHendelser.first()

        repository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)

        // Verifiser at pollingstatus er lagret ved å sjekke at hendelsen ikke lenger returneres
        val usendteEtter = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        assertThat(usendteEtter).isEmpty()
    }

    @Test
    fun `lagrePollingstatus med Connection fungerer i transaksjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")
        val jobbsøkere = db.hentJobbsøkereViaRepository(treffId)
        val personTreffId = jobbsøkere.first().personTreffId
        db.inviterJobbsøkere(listOf(personTreffId), treffId, "testperson")

        val usendteHendelser = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        val hendelse = usendteHendelser.first()

        db.dataSource.connection.use { connection ->
            repository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)
        }

        val usendteEtter = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        assertThat(usendteEtter).isEmpty()
    }

    @Test
    fun `hentUsendteHendelse filtrerer på riktig hendelsestype`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")
        val jobbsøkere = db.hentJobbsøkereViaRepository(treffId)
        val personTreffId = jobbsøkere.first().personTreffId
        db.inviterJobbsøkere(listOf(personTreffId), treffId, "testperson")

        // Sjekk at INVITERT finnes
        val invitert = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        assertThat(invitert).hasSize(1)

        // Sjekk at andre hendelsestyper ikke returneres
        val svartJa = repository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON)
        assertThat(svartJa).isEmpty()

        val opprettet = repository.hentUsendteHendelse(JobbsøkerHendelsestype.OPPRETTET)
        assertThat(opprettet).hasSize(1) // OPPRETTET-hendelsen finnes også
    }

    @Test
    fun `hentUsendteHendelse skal filtrere ut hendelser for ikke-synlige jobbsøkere`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        
        // Opprett synlig jobbsøker
        val fnrSynlig = Fødselsnummer("12345678901")
        val jobbsøkerSynlig = LeggTilJobbsøker(fnrSynlig, Fornavn("Synlig"), Etternavn("Person"), null, null, null)
        
        // Opprett ikke-synlig jobbsøker
        val fnrIkkeSynlig = Fødselsnummer("98765432109")
        val jobbsøkerIkkeSynlig = LeggTilJobbsøker(fnrIkkeSynlig, Fornavn("Skjult"), Etternavn("Person"), null, null, null)

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøkerSynlig, jobbsøkerIkkeSynlig), treffId, "testperson")
        val jobbsøkere = db.hentJobbsøkereViaRepository(treffId)
        
        // Hent person_treff_id for begge og inviter dem
        val personTreffIdSynlig = jobbsøkere.first { it.fødselsnummer == fnrSynlig }.personTreffId
        val personTreffIdIkkeSynlig = jobbsøkere.first { it.fødselsnummer == fnrIkkeSynlig }.personTreffId
        
        db.inviterJobbsøkere(listOf(personTreffIdSynlig, personTreffIdIkkeSynlig), treffId, "testperson")
        
        // Sett den ene som ikke-synlig
        db.settSynlighet(personTreffIdIkkeSynlig, false)

        // Hent usendte hendelser - skal kun få hendelser for synlig jobbsøker
        val usendteHendelser = repository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)

        assertThat(usendteHendelser).hasSize(1)
        assertThat(usendteHendelser.first().fnr).isEqualTo(fnrSynlig.asString)
    }
}

