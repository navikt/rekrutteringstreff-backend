package no.nav.toi.jobbsoker.synlighet

import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.time.Instant

/**
 * Komponenttest som verifiserer at synlighetsfiltrering fungerer korrekt
 * på tvers av alle repositories som eksponerer jobbsøkerdata.
 * 
 * Synlighet styres av kode 6/7, KVP, død, osv. og oppdateres via toi-synlighetsmotor.
 * Jobbsøkere som ikke er synlige skal filtreres ut fra alle spørringer.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SynlighetsKomponentTest {

    companion object {
        private val db = TestDatabase()
        private val mapper = JacksonConfig.mapper
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var jobbsøkerService: JobbsøkerService
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var aktivitetskortRepository: AktivitetskortRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
            jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
            rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
            aktivitetskortRepository = AktivitetskortRepository(db.dataSource)
        }
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @AfterEach
    fun afterEach() {
        db.slettAlt()
    }

    // === Testdata-oppsett ===
    
    private data class TestTreffMedJobbsøkere(
        val treffId: TreffId,
        val fnrSynlig: Fødselsnummer,
        val fnrIkkeSynlig: Fødselsnummer,
        val personTreffIdSynlig: PersonTreffId,
        val personTreffIdIkkeSynlig: PersonTreffId
    )
    
    private fun opprettTreffMedSynligOgIkkeSynligJobbsøker(): TestTreffMedJobbsøkere {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        
        val fnrSynlig = Fødselsnummer("11111111111")
        val fnrIkkeSynlig = Fødselsnummer("22222222222")
        
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnrSynlig, Fornavn("Synlig"), Etternavn("Person"), null, null, null),
            LeggTilJobbsøker(fnrIkkeSynlig, Fornavn("IkkeSynlig"), Etternavn("Person"), null, null, null)
        )
        
        db.leggTilJobbsøkereMedHendelse(jobbsøkere, treffId, "testperson")
        val alleJobbsøkere = db.hentJobbsøkereViaRepository(treffId)
        
        val personTreffIdSynlig = alleJobbsøkere.first { it.fødselsnummer == fnrSynlig }.personTreffId
        val personTreffIdIkkeSynlig = alleJobbsøkere.first { it.fødselsnummer == fnrIkkeSynlig }.personTreffId
        
        // Sett synlighet - den ene synlig, den andre ikke synlig
        db.settSynlighet(personTreffIdSynlig, true)
        db.settSynlighet(personTreffIdIkkeSynlig, false)
        
        return TestTreffMedJobbsøkere(treffId, fnrSynlig, fnrIkkeSynlig, personTreffIdSynlig, personTreffIdIkkeSynlig)
    }

    // === Tester for JobbsøkerRepository ===

    @Test
    fun `hentJobbsøkere filtrerer ut ikke-synlige jobbsøkere`() {
        val testData = opprettTreffMedSynligOgIkkeSynligJobbsøker()
        
        val jobbsøkere = jobbsøkerRepository.hentJobbsøkere(testData.treffId)
        
        assertThat(jobbsøkere).hasSize(1)
        assertThat(jobbsøkere.first().personTreffId).isEqualTo(testData.personTreffIdSynlig)
    }

    @Test
    fun `hentAntallJobbsøkere teller ikke ikke-synlige jobbsøkere`() {
        val testData = opprettTreffMedSynligOgIkkeSynligJobbsøker()
        
        val antall = jobbsøkerRepository.hentAntallJobbsøkere(testData.treffId)
        
        assertThat(antall).isEqualTo(1)
    }

    @Test
    fun `hentJobbsøker returnerer null for ikke-synlig jobbsøker`() {
        val testData = opprettTreffMedSynligOgIkkeSynligJobbsøker()
        
        // Synlig jobbsøker kan hentes
        val synligJobbsøker = jobbsøkerRepository.hentJobbsøker(testData.treffId, testData.fnrSynlig)
        assertThat(synligJobbsøker).isNotNull
        
        // Ikke-synlig jobbsøker returnerer null
        val ikkeSynligJobbsøker = jobbsøkerRepository.hentJobbsøker(testData.treffId, testData.fnrIkkeSynlig)
        assertThat(ikkeSynligJobbsøker).isNull()
    }

    @Test
    fun `hentJobbsøkerHendelser filtrerer ut hendelser for ikke-synlige jobbsøkere`() {
        val testData = opprettTreffMedSynligOgIkkeSynligJobbsøker()
        
        // Begge har OPPRETTET-hendelser, men kun synlig skal være med
        val hendelser = jobbsøkerRepository.hentJobbsøkerHendelser(testData.treffId)
        
        assertThat(hendelser).hasSize(1)
        assertThat(hendelser.first().fornavn.asString).isEqualTo("Synlig")
    }

    // === Tester for RekrutteringstreffRepository ===

    @Test
    fun `hentAlleHendelser filtrerer ut jobbsøker-hendelser for ikke-synlige jobbsøkere`() {
        val testData = opprettTreffMedSynligOgIkkeSynligJobbsøker()
        
        val alleHendelser = rekrutteringstreffRepository.hentAlleHendelser(testData.treffId)
        
        // Filtrer basert på ressurs-feltet (JOBBSØKER, REKRUTTERINGSTREFF, ARBEIDSGIVER)
        val jobbsøkerHendelser = alleHendelser.filter { it.ressurs.name == "JOBBSØKER" }
        val treffHendelser = alleHendelser.filter { it.ressurs.name == "REKRUTTERINGSTREFF" }
        
        // Det bør være 1 jobbsøker-hendelse (fra synlig person)
        // (Ikke-synlig jobbsøker sin hendelse skal filtreres ut)
        assertThat(jobbsøkerHendelser).hasSize(1)
        // Rekrutteringstreff-hendelser skal fortsatt være med
        assertThat(treffHendelser).isNotEmpty()
    }

    // === Tester for AktivitetskortRepository ===

    @Test
    fun `hentUsendteHendelse filtrerer ut hendelser for ikke-synlige jobbsøkere`() {
        val testData = opprettTreffMedSynligOgIkkeSynligJobbsøker()
        
        // Inviter begge jobbsøkerne
        db.inviterJobbsøkere(listOf(testData.personTreffIdSynlig, testData.personTreffIdIkkeSynlig), testData.treffId, "testperson")
        
        // Hent usendte INVITERT-hendelser
        val usendteHendelser = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        
        // Kun synlig jobbsøker sin hendelse skal være med
        assertThat(usendteHendelser).hasSize(1)
        assertThat(usendteHendelser.first().fnr).isEqualTo("11111111111")
    }

    // === Integrasjonstest for synlighetsoppdatering ===

    @Test
    fun `synlighetsoppdatering fra event oppdaterer alle jobbsøkere med samme fødselsnummer`() {
        // Opprett to treff med samme person
        val treff1 = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "Treff1")
        val treff2 = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "Treff2")
        
        val fnr = "33333333333"
        val jobbsøker = LeggTilJobbsøker(
            Fødselsnummer(fnr), 
            Fornavn("Test"), 
            Etternavn("Person"), 
            null, null, null
        )
        
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treff1, "testperson")
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treff2, "testperson")
        
        // Oppdater synlighet via service (simulerer event fra toi-synlighetsmotor)
        val oppdatert = jobbsøkerService.oppdaterSynlighetFraEvent(fnr, false, Instant.now())
        
        // Begge jobbsøkerne skal være oppdatert
        assertThat(oppdatert).isEqualTo(2)
        
        // Verifiser at begge er filtrert ut
        assertThat(jobbsøkerRepository.hentJobbsøkere(treff1)).isEmpty()
        assertThat(jobbsøkerRepository.hentJobbsøkere(treff2)).isEmpty()
    }

    @Test
    fun `synlighetsoppdatering fra need oppdaterer kun der synlighet ikke er satt`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        
        val fnr = "44444444444"
        val jobbsøker = LeggTilJobbsøker(
            Fødselsnummer(fnr), 
            Fornavn("Test"), 
            Etternavn("Person"), 
            null, null, null
        )
        
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")
        
        // Første need-svar setter synlighet
        val oppdatert1 = jobbsøkerService.oppdaterSynlighetFraNeed(fnr, true, Instant.now())
        assertThat(oppdatert1).isEqualTo(1)
        
        // Andre need-svar skal ikke overskrive (synlighet_sist_oppdatert er nå satt)
        val oppdatert2 = jobbsøkerService.oppdaterSynlighetFraNeed(fnr, false, Instant.now())
        assertThat(oppdatert2).isEqualTo(0)
        
        // Jobbsøker skal fortsatt være synlig
        val jobbsøkere = jobbsøkerRepository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
    }

    // === Ende-til-ende scenario ===

    @Test
    fun `komplett scenario - jobbsøker blir ikke-synlig og forsvinner fra alle visninger`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        
        val fnr = Fødselsnummer("55555555555")
        val jobbsøker = LeggTilJobbsøker(
            fnr, 
            Fornavn("Test"), 
            Etternavn("Person"), 
            null, null, null
        )
        
        // Legg til jobbsøker og inviter
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")
        val personTreffId = db.hentJobbsøkereViaRepository(treffId).first().personTreffId
        db.inviterJobbsøkere(listOf(personTreffId), treffId, "testperson")
        
        // Verifiser at jobbsøker er synlig overalt
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).hasSize(1)
        assertThat(jobbsøkerRepository.hentAntallJobbsøkere(treffId)).isEqualTo(1)
        assertThat(jobbsøkerRepository.hentJobbsøker(treffId, fnr)).isNotNull
        assertThat(jobbsøkerRepository.hentJobbsøkerHendelser(treffId)).hasSize(2) // OPPRETTET + INVITERT
        assertThat(aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)).hasSize(1)
        
        // Simuler at jobbsøker får kode 6/7 - synlighet oppdateres via event
        jobbsøkerService.oppdaterSynlighetFraEvent(fnr.asString, false, Instant.now())
        
        // Verifiser at jobbsøker er filtrert ut overalt
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).isEmpty()
        assertThat(jobbsøkerRepository.hentAntallJobbsøkere(treffId)).isEqualTo(0)
        assertThat(jobbsøkerRepository.hentJobbsøker(treffId, fnr)).isNull()
        assertThat(jobbsøkerRepository.hentJobbsøkerHendelser(treffId)).isEmpty()
        assertThat(aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)).isEmpty()
    }

    @Test
    fun `jobbsøker som blir synlig igjen dukker opp i alle visninger`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        
        val fnr = Fødselsnummer("66666666666")
        val jobbsøker = LeggTilJobbsøker(
            fnr, 
            Fornavn("Test"), 
            Etternavn("Person"), 
            null, null, null
        )
        
        // Legg til jobbsøker som ikke-synlig
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")
        val personTreffId = db.hentJobbsøkereViaRepository(treffId).first().personTreffId
        db.settSynlighet(personTreffId, false)
        
        // Verifiser at jobbsøker ikke er synlig
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).isEmpty()
        
        // Synlighet endres - jobbsøker blir synlig igjen (f.eks. KVP avsluttet)
        jobbsøkerService.oppdaterSynlighetFraEvent(fnr.asString, true, Instant.now())
        
        // Verifiser at jobbsøker er synlig igjen
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).hasSize(1)
        assertThat(jobbsøkerRepository.hentAntallJobbsøkere(treffId)).isEqualTo(1)
        assertThat(jobbsøkerRepository.hentJobbsøker(treffId, fnr)).isNotNull
    }
}
