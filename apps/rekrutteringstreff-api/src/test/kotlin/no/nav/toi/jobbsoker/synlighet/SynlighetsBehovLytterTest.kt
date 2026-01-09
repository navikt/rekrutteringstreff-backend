package no.nav.toi.jobbsoker.synlighet

import no.nav.toi.JacksonConfig
import no.nav.toi.TestRapid
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.time.Instant

/**
 * Tester for SynlighetsBehovLytter som lytter på need-svar fra toi-synlighetsmotor.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SynlighetsBehovLytterTest {

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
    fun `skal oppdatere synlighet når need-svar mottas og synlighet ikke er satt`() {
        val rapid = TestRapid()
        SynlighetsBehovLytter(rapid, jobbsøkerService)

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")

        // Verifiser at jobbsøker er synlig (default)
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).hasSize(1)

        // Send need-svar
        rapid.sendTestMessage(
            """
            {
                "synlighetRekrutteringstreff": {
                    "erSynlig": false,
                    "ferdigBeregnet": true
                },
                "fodselsnummer": "$fnr",
                "@opprettet": "${Instant.now()}"
            }
            """.trimIndent()
        )

        // Verifiser at jobbsøker nå er ikke-synlig
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).isEmpty()
    }

    @Test
    fun `skal ikke overskrive synlighet fra event-strømmen`() {
        val rapid = TestRapid()
        SynlighetsBehovLytter(rapid, jobbsøkerService)

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")

        // Sett synlighet via event først (simulerer at event-strømmen allerede har oppdatert)
        jobbsøkerService.oppdaterSynlighetFraEvent(fnr, true, Instant.now())
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).hasSize(1)

        // Send need-svar som prøver å sette ikke-synlig
        rapid.sendTestMessage(
            """
            {
                "synlighetRekrutteringstreff": {
                    "erSynlig": false,
                    "ferdigBeregnet": true
                },
                "fodselsnummer": "$fnr",
                "@opprettet": "${Instant.now()}"
            }
            """.trimIndent()
        )

        // Skal fortsatt være synlig (need-svar overskriver ikke event-data)
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).hasSize(1)
    }

    @Test
    fun `skal ignorere need-svar der ferdigBeregnet er false`() {
        val rapid = TestRapid()
        SynlighetsBehovLytter(rapid, jobbsøkerService)

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")

        // Send need-svar med ferdigBeregnet=false - meldingen matcher ikke precondition fordi erSynlig finnes ikke
        // Så denne testen verifiserer at meldinger uten erSynlig ignoreres
        rapid.sendTestMessage(
            """
            {
                "synlighetRekrutteringstreff": {
                    "ferdigBeregnet": false
                },
                "fodselsnummer": "$fnr",
                "@opprettet": "${Instant.now()}"
            }
            """.trimIndent()
        )

        // Skal fortsatt være synlig (ufullstendig data ignoreres)
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).hasSize(1)
    }

    @Test
    fun `skal håndtere person som ikke finnes i noen treff`() {
        val rapid = TestRapid()
        SynlighetsBehovLytter(rapid, jobbsøkerService)

        // Send need-svar for person som ikke finnes
        rapid.sendTestMessage(
            """
            {
                "synlighetRekrutteringstreff": {
                    "erSynlig": false,
                    "ferdigBeregnet": true
                },
                "fodselsnummer": "99999999999",
                "@opprettet": "${Instant.now()}"
            }
            """.trimIndent()
        )

        // Skal ikke kaste exception - bare returnere 0 oppdaterte
    }
}
