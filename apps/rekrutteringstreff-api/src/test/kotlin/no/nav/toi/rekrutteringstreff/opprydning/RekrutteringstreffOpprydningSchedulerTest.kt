package no.nav.toi.rekrutteringstreff.opprydning

import no.nav.toi.JacksonConfig
import no.nav.toi.LeaderElectionMock
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.rekrutteringstreff.KiLoggTestInsert
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.ki.KiLoggRepository
import no.nav.toi.rekrutteringstreff.ki.KiLoggService
import no.nav.toi.rekrutteringstreff.ki.client.SystemPrompt
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.ZoneOffset
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffOpprydningSchedulerTest {
    private val testDatabase = TestDatabase()
    private lateinit var kiLoggRepository: KiLoggRepository
    private lateinit var kiLoggService: KiLoggService
    private lateinit var jobbsøkerRepository: JobbsøkerRepository
    private lateinit var jobbsøkerService: JobbsøkerService
    private lateinit var arbeidsgiverRepository: ArbeidsgiverRepository
    private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
    private lateinit var rekrutteringstreffService: RekrutteringstreffService
    private lateinit var rekrutteringstreffOpprydningScheduler: RekrutteringstreffOpprydningScheduler

    @BeforeAll
    fun setup() {
        Flyway.configure().dataSource(testDatabase.dataSource).load().migrate()
        kiLoggRepository = KiLoggRepository(testDatabase.dataSource)
        kiLoggService = KiLoggService(kiLoggRepository)
        jobbsøkerRepository = JobbsøkerRepository(testDatabase.dataSource, JacksonConfig.mapper)
        jobbsøkerService = JobbsøkerService(testDatabase.dataSource, jobbsøkerRepository)
        arbeidsgiverRepository = ArbeidsgiverRepository(testDatabase.dataSource, JacksonConfig.mapper)
        rekrutteringstreffRepository = RekrutteringstreffRepository(testDatabase.dataSource)
        rekrutteringstreffService = RekrutteringstreffService(
            testDatabase.dataSource,
            rekrutteringstreffRepository,
            jobbsøkerRepository,
            arbeidsgiverRepository,
            jobbsøkerService
        )

        rekrutteringstreffOpprydningScheduler = RekrutteringstreffOpprydningScheduler(
            kiLoggService, rekrutteringstreffService, LeaderElectionMock()
        )
    }

    @AfterEach
    fun cleanup() {
        testDatabase.slettAlt()
    }

    @Test
    fun `Scheduler skal slette KI-logger eldre enn 6 måneder`() {
        val ANTALL_MÅNEDER_MINUS_LOGG_SOM_SKAL_HENTES: Long = 7
        val ANTALL_MÅNEDER_MINUS_LOGG_SOM_IKKE_SKAL_HENTES: Long = 3
        val ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING = 6
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val ekstraJson = """
        {
          "promptVersjonsnummer": "${SystemPrompt.versjonsnummer}",
          "promptEndretTidspunkt": "${SystemPrompt.endretTidspunkt}",
          "promptHash": "${SystemPrompt.hash}"
        }
    """.trimIndent()
        val loggIdForLoggSomSkalSlettes = testDatabase.opprettKiLogg(
            KiLoggTestInsert(
                opprettetTidspunkt = ZonedDateTime.now(ZoneOffset.UTC)
                    .minusMonths(ANTALL_MÅNEDER_MINUS_LOGG_SOM_SKAL_HENTES),
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = ekstraJson,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "toi-gpt-4.1",
                svartidMs = 42
            )
        )
        val loggIdForLoggSomIkkeSkalSlettes = testDatabase.opprettKiLogg(
            KiLoggTestInsert(
                opprettetTidspunkt = ZonedDateTime.now(ZoneOffset.UTC)
                    .minusMonths(ANTALL_MÅNEDER_MINUS_LOGG_SOM_IKKE_SKAL_HENTES),
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = ekstraJson,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "toi-gpt-4.1",
                svartidMs = 42
            )
        )
        assertThat(kiLoggRepository.findById(loggIdForLoggSomSkalSlettes)).isNotNull()
        assertThat(kiLoggRepository.findById(loggIdForLoggSomIkkeSkalSlettes)).isNotNull()
        assertThat(kiLoggService.hentKiLoggUuiderForScheduledSletting(ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING)).containsExactly(loggIdForLoggSomSkalSlettes)
        rekrutteringstreffOpprydningScheduler.kjørJobb()
        assertThat(kiLoggService.hentKiLoggUuiderForScheduledSletting(ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING)).isEmpty()
    }

    @Test
    fun `Scheduler skal skjule treff eldre enn 6 måneder`() {
        val ANTALL_MÅNEDER_ETTER_TREFF_OPPRETTET_FOR_SKJULING_FRA_FRONTEND: Long = 6

        val tidspunktForTreffSomSkalSkjules = ZonedDateTime.now().minusMonths(
            ANTALL_MÅNEDER_ETTER_TREFF_OPPRETTET_FOR_SKJULING_FRA_FRONTEND
        ).minusDays(1)
        val tidspunktForTreffSomIkkeSkalSkjules = ZonedDateTime.now().minusMonths(
            ANTALL_MÅNEDER_ETTER_TREFF_OPPRETTET_FOR_SKJULING_FRA_FRONTEND
        ).plusDays(1)

        val treffIdForFullførtTreffSomSkalSkjules = testDatabase.opprettRekrutteringstreffMedAlleFelter(
            navIdent = "A123456",
            opprettetAvTidspunkt = tidspunktForTreffSomSkalSkjules.minusDays(10),
            tilTid = tidspunktForTreffSomSkalSkjules,
            status = RekrutteringstreffStatus.FULLFØRT
        )
        val treffIdForAvlystTreffSomSkalSkjules = testDatabase.opprettRekrutteringstreffMedAlleFelter(
            navIdent = "A123456",
            opprettetAvTidspunkt = tidspunktForTreffSomSkalSkjules.minusDays(10),
            tilTid = tidspunktForTreffSomSkalSkjules,
            status = RekrutteringstreffStatus.AVLYST
        )
        val treffIdForTreffSomIkkeSkalSkjules = testDatabase.opprettRekrutteringstreffMedAlleFelter(
            navIdent = "B123456",
            opprettetAvTidspunkt = tidspunktForTreffSomIkkeSkalSkjules.minusDays(10),
            tilTid = tidspunktForTreffSomIkkeSkalSkjules,
            status = RekrutteringstreffStatus.FULLFØRT
        )

        assertThat(rekrutteringstreffRepository.hent(treffIdForFullførtTreffSomSkalSkjules)?.status)
            .isEqualTo(RekrutteringstreffStatus.FULLFØRT)
        assertThat(rekrutteringstreffRepository.hent(treffIdForAvlystTreffSomSkalSkjules)?.status)
            .isEqualTo(RekrutteringstreffStatus.AVLYST)
        assertThat(rekrutteringstreffRepository.hent(treffIdForTreffSomIkkeSkalSkjules)?.status)
            .isEqualTo(RekrutteringstreffStatus.FULLFØRT)

        rekrutteringstreffOpprydningScheduler.kjørJobb()

        assertThat(rekrutteringstreffRepository.hent(treffIdForFullførtTreffSomSkalSkjules)?.status)
            .isEqualTo(RekrutteringstreffStatus.SKJULT)
        assertThat(rekrutteringstreffRepository.hent(treffIdForAvlystTreffSomSkalSkjules)?.status)
            .isEqualTo(RekrutteringstreffStatus.SKJULT)
        assertThat(rekrutteringstreffRepository.hent(treffIdForTreffSomIkkeSkalSkjules)?.status)
            .isEqualTo(RekrutteringstreffStatus.FULLFØRT)
    }
}