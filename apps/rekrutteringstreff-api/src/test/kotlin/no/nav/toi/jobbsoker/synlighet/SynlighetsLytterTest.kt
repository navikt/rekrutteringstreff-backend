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
 * Tester for SynlighetsLytter som lytter på event-strømmen fra toi-synlighetsmotor.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SynlighetsLytterTest {

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
    fun `skal oppdatere synlighet når event mottas`() {
        val rapid = TestRapid()
        SynlighetsLytter(rapid, jobbsøkerService)

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")

        // Verifiser at jobbsøker er synlig (default)
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).hasSize(1)

        // Send synlighets-event
        rapid.sendTestMessage(
            """
            {
                "synlighet": {
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
    fun `skal oppdatere alle rekrutteringstreff for samme person`() {
        val rapid = TestRapid()
        SynlighetsLytter(rapid, jobbsøkerService)

        val treff1 = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "Treff1")
        val treff2 = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "Treff2")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)
        
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treff1, "testperson")
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treff2, "testperson")

        // Send synlighets-event
        rapid.sendTestMessage(
            """
            {
                "synlighet": {
                    "erSynlig": false,
                    "ferdigBeregnet": true
                },
                "fodselsnummer": "$fnr",
                "@opprettet": "${Instant.now()}"
            }
            """.trimIndent()
        )

        // Verifiser at begge er oppdatert
        assertThat(jobbsøkerRepository.hentJobbsøkere(treff1)).isEmpty()
        assertThat(jobbsøkerRepository.hentJobbsøkere(treff2)).isEmpty()
    }

    @Test
    fun `skal ignorere eldre meldinger`() {
        val rapid = TestRapid()
        SynlighetsLytter(rapid, jobbsøkerService)

        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = "12345678901"
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Person"), null, null, null)
        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")

        val nyTidspunkt = Instant.now()
        val gammelTidspunkt = nyTidspunkt.minusSeconds(3600) // 1 time før

        // Send ny melding først
        rapid.sendTestMessage(
            """
            {
                "synlighet": {
                    "erSynlig": false,
                    "ferdigBeregnet": true
                },
                "fodselsnummer": "$fnr",
                "@opprettet": "$nyTidspunkt"
            }
            """.trimIndent()
        )
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).isEmpty()

        // Send gammel melding som prøver å sette synlig=true
        rapid.sendTestMessage(
            """
            {
                "synlighet": {
                    "erSynlig": true,
                    "ferdigBeregnet": true
                },
                "fodselsnummer": "$fnr",
                "@opprettet": "$gammelTidspunkt"
            }
            """.trimIndent()
        )

        // Skal fortsatt være ikke-synlig (gammel melding ignorert)
        assertThat(jobbsøkerRepository.hentJobbsøkere(treffId)).isEmpty()
    }

    @Test
    fun `skal håndtere person som ikke finnes i noen treff`() {
        val rapid = TestRapid()
        SynlighetsLytter(rapid, jobbsøkerService)

        // Send event for person som ikke finnes
        rapid.sendTestMessage(
            """
            {
                "synlighet": {
                    "erSynlig": false,
                    "ferdigBeregnet": true
                },
                "fodselsnummer": "99999999999",
                "@opprettet": "${Instant.now()}"
            }
            """.trimIndent()
        )

        // Skal ikke kaste exception - bare returnere 0 oppdaterte
        // (vi verifiserer at testen ikke feiler)
    }
}
