package no.nav.toi.formidling

import no.nav.toi.JacksonConfig
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormidlingServiceTest {

    companion object {
        private val db = TestDatabase()
        private val mapper = JacksonConfig.mapper
        private lateinit var formidlingRepository: FormidlingRepository
        private lateinit var formidlingService: FormidlingService
        private lateinit var arbeidsgiverService: ArbeidsgiverService
        private lateinit var jobbsøkerService: JobbsøkerService
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var stillingKlient: StillingKlientStub

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
            jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository, JobbsøkerSokRepository(db.dataSource))

            val arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
            arbeidsgiverService = ArbeidsgiverService(db.dataSource, arbeidsgiverRepository, mapper)

            formidlingRepository = FormidlingRepository(db.dataSource)
            rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)

            stillingKlient = StillingKlientStub()

            formidlingService = FormidlingService(
                db.dataSource,
                formidlingRepository,
                arbeidsgiverService,
                jobbsøkerService,
                rekrutteringstreffRepository,
                stillingKlient,
            )
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
    fun `hent formidling med formidlingId returnerer formidling`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId)

        val formidling = formidlingService.hent(formidlingId)

        assertThat(formidling).isNotNull
        formidling!!
        assertThat(formidling.formidlingId).isEqualTo(formidlingId)
        assertThat(formidling.treffId).isEqualTo(treffId)
        assertThat(formidling.jobbsøkerPersonTreffId).isEqualTo(personTreffId)
        assertThat(formidling.stillingId).isEqualTo(stillingId)
        assertThat(formidling.opprettetTidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
    }

    @Test
    fun `hent formidling med formidlingId returnerer null når den ikke finnes`() {
        val formidling = formidlingService.hent(999999L)
        assertThat(formidling).isNull()
    }

    @Test
    fun `hent formidling med treffId, personTreffId og arbeidsgiverTreffId`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId) = opprettTestdataForFormidling(treffId)

        db.opprettFormidling(treffId, personTreffId, orgnr, stillingId)

        val arbeidsgiverTreffId = arbeidsgiverService.hentArbeidsgivere(treffId).first().arbeidsgiverTreffId

        val formidling = formidlingService.hent(treffId, personTreffId, arbeidsgiverTreffId)

        assertThat(formidling).isNotNull
        formidling!!
        assertThat(formidling.treffId).isEqualTo(treffId)
        assertThat(formidling.jobbsøkerPersonTreffId).isEqualTo(personTreffId)
        assertThat(formidling.arbeidsgiverTreffId).isEqualTo(arbeidsgiverTreffId)
    }

    @Test
    fun `hent formidling med treffId, personTreffId og arbeidsgiverTreffId returnerer null når den ikke finnes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")

        val formidling = formidlingService.hent(
            treffId,
            PersonTreffId(UUID.randomUUID()),
            ArbeidsgiverTreffId(UUID.randomUUID())
        )

        assertThat(formidling).isNull()
    }

    @Test
    fun `slett formidling skal markere formidling som slettet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId)

        // Verifiser at formidling finnes
        assertThat(formidlingService.hent(formidlingId)).isNotNull

        val slettet = formidlingService.slett(formidlingId)

        assertThat(slettet).isTrue()
        assertThat(formidlingService.hent(formidlingId)).isNull()
    }

    @Test
    fun `slett formidling returnerer false når formidling ikke finnes`() {
        val slettet = formidlingService.slett(999999L)
        assertThat(slettet).isFalse()
    }

    @Test
    fun `slett formidling returnerer false for allerede slettet formidling`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId)

        // Slett første gang
        val førsteSlett = formidlingService.slett(formidlingId)
        assertThat(førsteSlett).isTrue()

        // Slett andre gang - skal returnere false
        val andreSlett = formidlingService.slett(formidlingId)
        assertThat(andreSlett).isFalse()
    }

    @Test
    fun `kan opprette og slette flere formidlinger uavhengig av hverandre`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val orgnr = "123456789"
        val stillingId = UUID.randomUUID()

        // Opprett arbeidsgiver
        val arbeidsgiverTreffId = arbeidsgiverService.leggTilArbeidsgiver(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

        // Opprett to jobbsøkere
        val jobbsøker1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        val jobbsøker2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Nordmann"), null, null, null)
        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2), treffId, "testperson")

        val personTreffIder = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }

        val formidlingId1 = db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId)
        val formidlingId2 = db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId)

        // Begge finnes
        assertThat(formidlingService.hent(formidlingId1)).isNotNull
        assertThat(formidlingService.hent(formidlingId2)).isNotNull

        // Slett bare den første
        formidlingService.slett(formidlingId1)

        // Verifiser at bare den ene er slettet
        assertThat(formidlingService.hent(formidlingId1)).isNull()
        assertThat(formidlingService.hent(formidlingId2)).isNotNull
    }

    @Test
    fun `slettet formidling vises ikke ved hent med treffId, personTreffId og arbeidsgiverTreffId`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId)

        val arbeidsgiverTreffId = arbeidsgiverService.hentArbeidsgivere(treffId).first().arbeidsgiverTreffId

        // Verifiser at formidling finnes
        assertThat(formidlingService.hent(treffId, personTreffId, arbeidsgiverTreffId)).isNotNull

        // Slett
        formidlingService.slett(formidlingId)

        // Verifiser at formidling ikke lenger returneres
        assertThat(formidlingService.hent(treffId, personTreffId, arbeidsgiverTreffId)).isNull()
    }

    @Test
    fun `formidling har unik UUID (id-felt)`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val orgnr = "123456789"
        val stillingId = UUID.randomUUID()

        val arbeidsgiverTreffId = arbeidsgiverService.leggTilArbeidsgiver(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

        val jobbsøker1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        val jobbsøker2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Nordmann"), null, null, null)
        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2), treffId, "testperson")

        val personTreffIder = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }

        val formidlingId1 = db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId)
        val formidlingId2 = db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId)

        val formidling1 = formidlingService.hent(formidlingId1)!!
        val formidling2 = formidlingService.hent(formidlingId2)!!

        assertThat(formidling1.id).isNotEqualTo(formidling2.id)
    }

    private data class FormidlingTestdata(
        val personTreffId: PersonTreffId,
        val arbeidsgiverTreffId: ArbeidsgiverTreffId,
        val stillingId: UUID,
    )

    private fun opprettTestdataForFormidling(treffId: TreffId): FormidlingTestdata {
        val orgnr = "123456789"
        val stillingId = UUID.randomUUID()

        val arbeidsgiverTreffId = arbeidsgiverService.leggTilArbeidsgiver(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

        val jobbsøker = LeggTilJobbsøker(
            Fødselsnummer("12345678901"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            null, null, null
        )
        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker), treffId, "testperson")
        val personTreffId = jobbsøkerService.hentJobbsøkere(treffId).first().personTreffId

        return FormidlingTestdata(personTreffId, arbeidsgiverTreffId, stillingId)
    }
}

