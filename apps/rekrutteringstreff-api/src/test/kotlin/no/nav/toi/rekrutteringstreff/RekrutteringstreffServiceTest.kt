package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import no.nav.toi.JacksonConfig

import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.nowOslo
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class RekrutteringstreffServiceTest {
    companion object {
        private val db = TestDatabase()
        private val mapper = JacksonConfig.mapper
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var arbeidsgiverRepository: ArbeidsgiverRepository
        private lateinit var rekrutteringstreffService: RekrutteringstreffService

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
            jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
            arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
            rekrutteringstreffService = RekrutteringstreffService(
                db.dataSource,
                rekrutteringstreffRepository,
                jobbsøkerRepository,
                arbeidsgiverRepository,
            )
        }
    }

    @AfterEach
    fun tearDown() {
        db.slettAlt()
    }

    @Test
    fun `Skal kunne hente alle rekrutteringstreff`() {
        val rekrutteringstreff1 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 1",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val rekrutteringstreff2 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 2",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val rekrutteringstreff3 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 3",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val treffId1 = rekrutteringstreffRepository.opprett(rekrutteringstreff1)
        val treffId2 = rekrutteringstreffRepository.opprett(rekrutteringstreff2)
        val treffId3 = rekrutteringstreffRepository.opprett(rekrutteringstreff3)

        val rekrutteringstreff = rekrutteringstreffService.hentAlleRekrutteringstreff()

        assertThat(rekrutteringstreff.any { it.id == treffId1.somUuid }).isTrue
        assertThat(rekrutteringstreff.any { it.id == treffId2.somUuid }).isTrue
        assertThat(rekrutteringstreff.any { it.id == treffId3.somUuid }).isTrue
    }

    @Test
    fun `Skal kunne hente et rekrutteringstreff`() {
        val rekrutteringstreff1 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 1",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val treffId1 = rekrutteringstreffRepository.opprett(rekrutteringstreff1)
        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreff(treffId1)

        assertThat(rekrutteringstreff.id == treffId1.somUuid).isTrue
    }

    @Test
    fun `Skal kunne avlyse et rekrutteringstreff`() {
        val rekrutteringstreff1 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 1",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val treffId1 = rekrutteringstreffRepository.opprett(rekrutteringstreff1)
        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreff(treffId1)

        assertThat(rekrutteringstreff.status == RekrutteringstreffStatus.UTKAST).isTrue

        rekrutteringstreffService.avlys(treffId1, "NAV1234")

        val rekrutteringstreffEtterAvlys = rekrutteringstreffService.hentRekrutteringstreff(treffId1)

        assertThat(rekrutteringstreffEtterAvlys.status == RekrutteringstreffStatus.AVLYST).isTrue
    }

    @Test
    fun `Skal kunne fullføre et rekrutteringstreff`() {
        val rekrutteringstreff1 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 1",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val treffId1 = rekrutteringstreffRepository.opprett(rekrutteringstreff1)
        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreff(treffId1)

        assertThat(rekrutteringstreff.status == RekrutteringstreffStatus.UTKAST).isTrue

        rekrutteringstreffService.fullfør(treffId1, "NAV1234")

        val rekrutteringstreffEtterFullfør = rekrutteringstreffService.hentRekrutteringstreff(treffId1)

        assertThat(rekrutteringstreffEtterFullfør.status == RekrutteringstreffStatus.FULLFØRT).isTrue
    }
}