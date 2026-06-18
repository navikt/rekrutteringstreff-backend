package no.nav.toi.formidling

import no.nav.toi.JacksonConfig
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.exception.JobbsøkerSperretException
import no.nav.toi.formidling.dto.ArbeidsgiverDto
import no.nav.toi.formidling.dto.OpprettFormidlingDto
import no.nav.toi.formidling.dto.StillingDto
import no.nav.toi.executeInTransaction
import io.mockk.every
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.time.Instant
import java.time.ZonedDateTime
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
        private lateinit var stillingKlient: StillingKlient
        private lateinit var kandidatKlient: KandidatKlient

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

            stillingKlient = mockk(relaxed = true)
            kandidatKlient = mockk(relaxed = true)

            formidlingService = FormidlingService(
                db.dataSource,
                formidlingRepository,
                arbeidsgiverService,
                jobbsøkerService,
                rekrutteringstreffRepository,
                stillingKlient,
                kandidatKlient
            )
        }
    }

    @BeforeEach
    fun beforeEach() {
        clearMocks(stillingKlient, kandidatKlient)
        db.slettAlt()
    }

    @AfterEach
    fun afterEach() {
        db.slettAlt()
    }

    @Test
    fun `hent formidling med formidlingId returnerer formidling`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

        val formidling = formidlingService.hent(formidlingId)

        assertThat(formidling).isNotNull
        formidling!!
        assertThat(formidling.formidlingId).isEqualTo(formidlingId)
        assertThat(formidling.treffId).isEqualTo(treffId)
        assertThat(formidling.jobbsøkerPersonTreffId).isEqualTo(personTreffId)
        assertThat(formidling.stillingId).isEqualTo(stillingId)
        assertThat(formidling.kandidatlisteId).isNotNull()
        assertThat(formidling.utfallSendtTidspunkt).isNull()
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
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

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
    fun `oppretter nye formidlinger for jobbsøkere uten eksisterende formidling`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val orgnr = "123456789"
        val stillingId = UUID.randomUUID()
        val kandidatlisteId = UUID.randomUUID()

        val arbeidsgiverTreffId = arbeidsgiverService.leggTilArbeidsgiver(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

        jobbsøkerService.leggTilJobbsøkere(
            listOf(LeggTilJobbsøker(Fødselsnummer("12345678901"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)),
            treffId, "testperson"
        )
        val personTreffId = jobbsøkerService.hentJobbsøkere(treffId).first().personTreffId

        every {
            stillingKlient.opprettFormidlingStillingOgKandidatliste(any(), any())
        } returns OpprettFormidlingStillingRespons(stillingsId = stillingId, kandidatlisteId = kandidatlisteId)

        val opprettede = formidlingService.opprettFormidling(
            treffId = treffId,
            opprettFormidling = opprettFormidlingDto(orgnr, "12345678901"),
            navIdent = "testperson",
            userToken = "test-token",
        )

        // Det opprettes én formidling for jobbsøkeren uten eksisterende formidling
        assertThat(opprettede).hasSize(1)
        val opprettet = opprettede.single()
        assertThat(opprettet.jobbsøkerPersonTreffId).isEqualTo(personTreffId)
        assertThat(opprettet.stillingId).isEqualTo(stillingId)
        assertThat(opprettet.kandidatlisteId).isEqualTo(kandidatlisteId)

        // StillingKlient kalles for å opprette stilling og kandidatliste
        verify(exactly = 1) { stillingKlient.opprettFormidlingStillingOgKandidatliste(any(), any()) }

        // Kandidaten legges på listen
        verify(exactly = 1) {
            kandidatKlient.leggTilPersonerPåKandidatliste(
                kandidatlisteId = kandidatlisteId,
                stillingId = stillingId,
                jobbsøker = any(),
                navKontorVeileder = any(),
                userToken = "test-token",
            )
        }

        // Formidling-raden er lagret med utfall_sendt_tidspunkt satt
        val lagret = formidlingService.hent(opprettet.formidlingId)
        assertThat(lagret).isNotNull
        assertThat(lagret!!.stillingId).isEqualTo(stillingId)
        assertThat(lagret.kandidatlisteId).isEqualTo(kandidatlisteId)
        assertThat(lagret.arbeidsgiverTreffId).isEqualTo(arbeidsgiverTreffId)
        assertThat(lagret.utfallSendtTidspunkt).isNotNull()
        assertThat(lagret.utfallSendtTidspunkt!!.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))

        // Jobbsøkerstatus er satt til FÅTT_JOBB
        val oppdatertJobbsøker = jobbsøkerService.hentJobbsøkere(treffId).first { it.personTreffId == personTreffId }
        assertThat(oppdatertJobbsøker.status).isEqualTo(JobbsøkerStatus.FÅTT_JOBB)

        // Yrkestittel fra opprettelsen er lagret og returneres i listen
        val linjer = formidlingService.hentAlleFormidlingerForTreff(treffId)
        assertThat(linjer.single().yrkestittel).isEqualTo("Utvikler (dataspill)")
    }

    @Test
    fun `blokkerer formidling av jobbsøker med adressebeskyttelse`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val orgnr = "123456789"

        arbeidsgiverService.leggTilArbeidsgiver(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

        jobbsøkerService.leggTilJobbsøkere(
            listOf(LeggTilJobbsøker(Fødselsnummer("12345678901"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)),
            treffId, "testperson"
        )
        val personTreffId = jobbsøkerService.hentJobbsøkere(treffId).first().personTreffId
        db.settSperret(personTreffId, true)

        assertThatThrownBy {
            formidlingService.opprettFormidling(
                treffId = treffId,
                opprettFormidling = opprettFormidlingDto(orgnr, "12345678901"),
                navIdent = "testperson",
                userToken = "test-token",
            )
        }.isInstanceOf(JobbsøkerSperretException::class.java)

        verify(exactly = 0) { stillingKlient.opprettFormidlingStillingOgKandidatliste(any(), any()) }
        assertThat(formidlingService.hentAlleFormidlingerForTreff(treffId)).isEmpty()
    }

    @Test
    fun `kan gjenbruke eksisterende formidling med tomt utfallSendtTidspunkt`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, arbeidsgiverTreffId, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)
        val orgnr = "123456789"

        val formidlingId = db.opprettFormidling(treffId, personTreffId, arbeidsgiverTreffId, stillingId, kandidatlisteId)

        val opprettede = formidlingService.opprettFormidling(
            treffId = treffId,
            opprettFormidling = opprettFormidlingDto(orgnr, "12345678901"),
            navIdent = "testperson",
            userToken = "test-token",
        )

        assertThat(opprettede).hasSize(1)
        assertThat(opprettede.single().formidlingId).isEqualTo(formidlingId)

        val oppdatert = formidlingService.hent(formidlingId)
        assertThat(oppdatert).isNotNull
        assertThat(oppdatert!!.utfallSendtTidspunkt).isNotNull()
        assertThat(oppdatert.utfallSendtTidspunkt!!.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
    }

    @Test
    fun `eksisterende formidling med sendt utfall behandles ikke på nytt`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, arbeidsgiverTreffId, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)
        val orgnr = "123456789"

        val formidlingId = db.dataSource.executeInTransaction { connection ->
            formidlingRepository.opprett(
                connection,
                treffId,
                personTreffId,
                arbeidsgiverTreffId,
                stillingId,
                kandidatlisteId,
                ZonedDateTime.now().minusMinutes(10),
            )
        }

        val opprettede = formidlingService.opprettFormidling(
            treffId = treffId,
            opprettFormidling = opprettFormidlingDto(orgnr, "12345678901"),
            navIdent = "testperson",
            userToken = "test-token",
        )

        assertThat(opprettede).isEmpty()
        assertThat(formidlingService.hent(formidlingId)!!.utfallSendtTidspunkt).isNotNull()
    }

    @Test
    fun `slett formidling skal markere formidling som slettet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

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
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

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
        val kandidatlisteId = UUID.randomUUID()

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

        val formidlingId1 = db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, kandidatlisteId)
        val formidlingId2 = db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, kandidatlisteId)

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
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

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
        val kandidatlisteId = UUID.randomUUID()

        val arbeidsgiverTreffId = arbeidsgiverService.leggTilArbeidsgiver(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

        val jobbsøker1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        val jobbsøker2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Nordmann"), null, null, null)
        jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker1, jobbsøker2), treffId, "testperson")

        val personTreffIder = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }

        val formidlingId1 = db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, kandidatlisteId)
        val formidlingId2 = db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, kandidatlisteId)

        val formidling1 = formidlingService.hent(formidlingId1)!!
        val formidling2 = formidlingService.hent(formidlingId2)!!

        assertThat(formidling1.id).isNotEqualTo(formidling2.id)
    }

    private data class FormidlingTestdata(
        val personTreffId: PersonTreffId,
        val arbeidsgiverTreffId: ArbeidsgiverTreffId,
        val stillingId: UUID,
        val kandidatlisteId: UUID,
    )

    private fun opprettTestdataForFormidling(treffId: TreffId): FormidlingTestdata {
        val orgnr = "123456789"
        val stillingId = UUID.randomUUID()
        val kandidatlisteId = UUID.randomUUID()

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

        return FormidlingTestdata(personTreffId, arbeidsgiverTreffId, stillingId, kandidatlisteId)
    }

    private fun opprettFormidlingDto(orgnr: String, fødselsnummer: String): OpprettFormidlingDto {
        return OpprettFormidlingDto(
            eierNavKontorEnhetId = "1234",
            orgnr = orgnr,
            fødselsnumre = listOf(fødselsnummer),
            stilling = StillingDto(
                employer = ArbeidsgiverDto(
                    name = "Test AS",
                    orgnr = orgnr,
                    publicName = "Test AS",
                    contactList = emptyList(),
                    locationList = emptyList(),
                    properties = emptyMap(),
                ),
            ),
            yrkestittel = "Utvikler (dataspill)",
            janzzKonseptId = "19989",
        )
    }
}

