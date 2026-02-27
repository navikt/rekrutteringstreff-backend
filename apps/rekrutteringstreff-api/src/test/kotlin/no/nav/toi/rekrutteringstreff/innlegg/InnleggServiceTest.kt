package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff.innlegg

import no.nav.toi.JacksonConfig
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.innlegg.InnleggRepository
import no.nav.toi.rekrutteringstreff.innlegg.InnleggService
import no.nav.toi.rekrutteringstreff.innlegg.OpprettInnleggRequestDto
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnleggServiceTest {

     companion object {
        private val db = TestDatabase()
        private val mapper = JacksonConfig.mapper
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var arbeidsgiverRepository: ArbeidsgiverRepository
        private lateinit var innleggRepository: InnleggRepository
        private lateinit var jobbsøkerService: JobbsøkerService
        private lateinit var rekrutteringstreffService: RekrutteringstreffService
        private lateinit var innleggService: InnleggService

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
            innleggRepository = InnleggRepository(db.dataSource)
            jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
            rekrutteringstreffService = RekrutteringstreffService(
                db.dataSource,
                rekrutteringstreffRepository,
                jobbsøkerRepository,
                arbeidsgiverRepository,
                jobbsøkerService
            )
            innleggService = InnleggService(innleggRepository, rekrutteringstreffService)
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

    @Test
    fun `Opprett et innlegg uten at det finnes innlegg fra før`() {
        val tittel = "Tittel på innlegg"
        val navIdent = "navident"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent, tittel)

        val opprettetInnlegg = innleggService.opprettInnlegg(treffId, opprettInnleggDto(tittel, navIdent), navIdent)
        val alleInnleggForEtTreff = innleggService.hentForTreff(treffId)

        assertThat(opprettetInnlegg).isNotNull
        assertThat(opprettetInnlegg.tittel).isEqualTo(tittel)
        assertThat(opprettetInnlegg.opprettetAvPersonNavident).isEqualTo(navIdent)

        assertThat(alleInnleggForEtTreff).hasSize(1)
    }

    @Test
    fun `Opprett et innlegg to ganger skal føre til kun ett innlegg`() {
        val tittel1 = "Treff 1"
        val tittel2 = "Treff 2"
        val navIdent1 = "navident 1"
        val navIdent2 = "navident 1"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent1, tittel1)

        val opprettetInnlegg = innleggService.opprettInnlegg(treffId, opprettInnleggDto(tittel1, navIdent1), navIdent1)
        val opprettetInnlegg2 = innleggService.opprettInnlegg(treffId, opprettInnleggDto(tittel2, navIdent2), navIdent2)
        val alleInnleggForEtTreff = innleggService.hentForTreff(treffId)

        assertThat(opprettetInnlegg.id).isEqualTo(opprettetInnlegg2.id)

        assertThat(opprettetInnlegg).isNotNull
        assertThat(opprettetInnlegg.tittel).isEqualTo(tittel1)
        assertThat(opprettetInnlegg.opprettetAvPersonNavident).isEqualTo(navIdent1)

        assertThat(opprettetInnlegg2).isNotNull
        assertThat(opprettetInnlegg2.tittel).isEqualTo(tittel2)
        assertThat(opprettetInnlegg2.opprettetAvPersonNavident).isEqualTo(navIdent2)

        assertThat(alleInnleggForEtTreff).hasSize(1)
        assertThat(alleInnleggForEtTreff[0].tittel).isEqualTo(tittel2) // tittel 2 overskriver tittel 1
        assertThat(opprettetInnlegg2.opprettetAvPersonNavident).isEqualTo(navIdent1)
    }

    fun opprettInnleggDto(tittel: String, navIdent: String): OpprettInnleggRequestDto = OpprettInnleggRequestDto(
        tittel = tittel,
        opprettetAvPersonNavn = navIdent,
        opprettetAvPersonBeskrivelse = "",
        sendesTilJobbsokerTidspunkt = null,
        htmlContent = "Test")
}
