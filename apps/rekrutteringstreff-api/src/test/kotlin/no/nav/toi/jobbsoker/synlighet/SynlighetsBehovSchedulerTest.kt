package no.nav.toi.jobbsoker.synlighet

import no.nav.toi.JacksonConfig
import no.nav.toi.TestRapid
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.no.nav.toi.LeaderElectionMock
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant

/**
 * Tester for SynlighetsBehovScheduler som finner jobbsøkere uten evaluert synlighet
 * og publiserer need-meldinger for dem.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SynlighetsBehovSchedulerTest {

    private lateinit var jobbsøkerRepository: JobbsøkerRepository
    private lateinit var jobbsøkerService: JobbsøkerService
    private val db = TestDatabase()
    private val objectMapper = JacksonConfig.mapper

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, objectMapper)
        jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @Test
    fun `skal publisere need for jobbsøker som ikke har evaluert synlighet`() {
        val rapid = TestRapid()
        val scheduler = SynlighetsBehovScheduler(jobbsøkerService, rapid, LeaderElectionMock())

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)

        // Legg til jobbsøker - synlighet_sist_oppdatert vil være NULL
        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker), treffId, "testperson")

        // Verifiser at jobbsøker mangler evaluert synlighet
        val utenSynlighet = jobbsøkerRepository.hentFødselsnumreUtenEvaluertSynlighet()
        assertThat(utenSynlighet).contains(fnr)

        // Kjør scheduler
        scheduler.behandleJobbsøkereUtenSynlighet()

        // Verifiser at need-melding ble publisert
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("behov")
        assertThat(melding["@behov"].map { it.asText() }).contains("synlighetRekrutteringstreff")
        assertThat(melding["fodselsnummer"].asText()).isEqualTo(fnr)
    }

    @Test
    fun `skal ikke publisere need for jobbsøker som allerede har evaluert synlighet`() {
        val rapid = TestRapid()
        val scheduler = SynlighetsBehovScheduler(jobbsøkerService, rapid, LeaderElectionMock())

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)

        // Legg til jobbsøker
        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker), treffId, "testperson")

        // Simuler at synlighet allerede er evaluert (fra event-strøm eller tidligere need-svar)
        jobbsøkerService.oppdaterSynlighetFraEvent(fnr, true, Instant.now())

        // Verifiser at jobbsøker IKKE mangler evaluert synlighet
        val utenSynlighet = jobbsøkerRepository.hentFødselsnumreUtenEvaluertSynlighet()
        assertThat(utenSynlighet).doesNotContain(fnr)

        // Kjør scheduler
        scheduler.behandleJobbsøkereUtenSynlighet()

        // Verifiser at ingen need-melding ble publisert
        assertThat(rapid.inspektør.size).isEqualTo(0)
    }

    @Test
    fun `skal publisere need for flere jobbsøkere som mangler synlighet`() {
        val rapid = TestRapid()
        val scheduler = SynlighetsBehovScheduler(jobbsøkerService, rapid, LeaderElectionMock())

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr1 = "12345678901"
        val fnr2 = "10987654321"
        val jobbsøkere = listOf(
            LeggTilJobbsøker(Fødselsnummer(fnr1), Fornavn("Test1"), Etternavn("Person1"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer(fnr2), Fornavn("Test2"), Etternavn("Person2"), null, null, null)
        )

        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")

        // Kjør scheduler
        scheduler.behandleJobbsøkereUtenSynlighet()

        // Verifiser at need-meldinger ble publisert for begge
        assertThat(rapid.inspektør.size).isEqualTo(2)
        val publiserteFnr = (0 until rapid.inspektør.size).map { rapid.inspektør.message(it)["fodselsnummer"].asText() }
        assertThat(publiserteFnr).containsExactlyInAnyOrder(fnr1, fnr2)
    }

    @Test
    fun `skal kun publisere need for jobbsøkere som mangler synlighet - ikke de som allerede er evaluert`() {
        val rapid = TestRapid()
        val scheduler = SynlighetsBehovScheduler(jobbsøkerService, rapid, LeaderElectionMock())

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnrMedSynlighet = "12345678901"
        val fnrUtenSynlighet = "10987654321"
        val jobbsøkere = listOf(
            LeggTilJobbsøker(Fødselsnummer(fnrMedSynlighet), Fornavn("Test1"), Etternavn("Person1"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer(fnrUtenSynlighet), Fornavn("Test2"), Etternavn("Person2"), null, null, null)
        )

        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")

        // Sett synlighet for én av jobbsøkerne
        jobbsøkerService.oppdaterSynlighetFraEvent(fnrMedSynlighet, true, Instant.now())

        // Kjør scheduler
        scheduler.behandleJobbsøkereUtenSynlighet()

        // Verifiser at kun jobbsøker uten synlighet fikk need-melding
        assertThat(rapid.inspektør.size).isEqualTo(1)
        assertThat(rapid.inspektør.message(0)["fodselsnummer"].asText()).isEqualTo(fnrUtenSynlighet)
    }

    @Test
    fun `skal ikke publisere noe når ingen jobbsøkere mangler synlighet`() {
        val rapid = TestRapid()
        val scheduler = SynlighetsBehovScheduler(jobbsøkerService, rapid, LeaderElectionMock())

        // Ingen jobbsøkere i databasen

        // Kjør scheduler
        scheduler.behandleJobbsøkereUtenSynlighet()

        // Verifiser at ingen meldinger ble publisert
        assertThat(rapid.inspektør.size).isEqualTo(0)
    }

    @Test
    fun `skal publisere need for samme fødselsnummer i flere treff - men kun én gang`() {
        val rapid = TestRapid()
        val scheduler = SynlighetsBehovScheduler(jobbsøkerService, rapid, LeaderElectionMock())

        val fnr = "12345678901"
        val treffId1 = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "Treff1")
        val treffId2 = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "Treff2")

        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)

        // Legg til samme person i to treff
        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker), treffId1, "testperson")
        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker), treffId2, "testperson")

        // Kjør scheduler
        scheduler.behandleJobbsøkereUtenSynlighet()

        // Verifiser at kun én need-melding ble publisert (DISTINCT fødselsnummer)
        assertThat(rapid.inspektør.size).isEqualTo(1)
        assertThat(rapid.inspektør.message(0)["fodselsnummer"].asText()).isEqualTo(fnr)
    }

    @Test
    fun `skal ikke publisere need for slettede jobbsøkere`() {
        val rapid = TestRapid()
        val scheduler = SynlighetsBehovScheduler(jobbsøkerService, rapid, LeaderElectionMock())

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)

        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker), treffId, "testperson")

        // Slett jobbsøkeren
        val personTreffId = jobbsøkerRepository.hentPersonTreffId(treffId, Fødselsnummer(fnr))!!
        jobbsøkerService.markerSlettet(personTreffId, treffId, "testperson")

        // Kjør scheduler
        scheduler.behandleJobbsøkereUtenSynlighet()

        // Verifiser at ingen need-melding ble publisert (slettet jobbsøker skal ignoreres)
        assertThat(rapid.inspektør.size).isEqualTo(0)
    }
}
