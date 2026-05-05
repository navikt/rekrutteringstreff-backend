package no.nav.toi.rekrutteringstreff

import no.nav.toi.JacksonConfig
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.LeaderElectionMock
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.nowOslo
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.eier.EierService
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffSchedulerTest {

    private val db = TestDatabase()
    private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
    private lateinit var rekrutteringstreffService: RekrutteringstreffService

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        val mapper = JacksonConfig.mapper
        rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
        val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        val arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
        val jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
        rekrutteringstreffService = RekrutteringstreffService(
            db.dataSource,
            rekrutteringstreffRepository,
            jobbsøkerRepository,
            arbeidsgiverRepository,
            jobbsøkerService,
            EierService(EierRepository(db.dataSource), rekrutteringstreffRepository, db.dataSource)
        )
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @Test
    fun `fullførJobbtreff skal sette ferdig publisert treff til FULLFORT`() {
        val ferdigTreffId = db.opprettRekrutteringstreffMedAlleFelter(
            status = RekrutteringstreffStatus.PUBLISERT,
            tilTid = nowOslo().minusDays(1),
        )
        val scheduler = RekrutteringstreffScheduler(rekrutteringstreffService, LeaderElectionMock())

        scheduler.kjørJobb()

        val treff = rekrutteringstreffRepository.hent(ferdigTreffId)
        assertThat(treff?.status).isEqualTo(RekrutteringstreffStatus.FULLFØRT)
    }

    @Test
    fun `fullførJobbtreff skal ikke endre status paa publiserte treff som ikke er ferdig`() {
        val fremtidigTreffId = db.opprettRekrutteringstreffMedAlleFelter(
            status = RekrutteringstreffStatus.PUBLISERT,
            tilTid = nowOslo().plusDays(1),
        )
        val scheduler = RekrutteringstreffScheduler(rekrutteringstreffService, LeaderElectionMock())

        scheduler.kjørJobb()

        val treff = rekrutteringstreffRepository.hent(fremtidigTreffId)
        assertThat(treff?.status).isEqualTo(RekrutteringstreffStatus.PUBLISERT)
    }

    @Test
    fun `fullførJobbtreff skal ikke gjore noe når instansen ikke er leader`() {
        val ferdigTreffId = db.opprettRekrutteringstreffMedAlleFelter(
            status = RekrutteringstreffStatus.PUBLISERT,
            tilTid = nowOslo().minusDays(1),
        )
        val ikkeLeader = object : LeaderElectionInterface { override fun isLeader() = false }
        val scheduler = RekrutteringstreffScheduler(rekrutteringstreffService, ikkeLeader)

        scheduler.wrapJobbkjøring()

        val treff = rekrutteringstreffRepository.hent(ferdigTreffId)
        assertThat(treff?.status).isEqualTo(RekrutteringstreffStatus.PUBLISERT)
    }
}
