package no.nav.toi.formidling

import io.javalin.http.NotFoundResponse
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
import org.assertj.core.api.Assertions.assertThatNoException
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

        // Kontornummer og kontornavn fra opprettelsen er lagret på formidlingen
        val (kontornummer, kontornavn) = db.hentFormidlingKontor(treffId)
        assertThat(kontornummer).isEqualTo("1234")
        assertThat(kontornavn).isEqualTo("Nav Test")
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
        val formidlingUuid = formidlingService.hent(formidlingId)!!.id

        // Verifiser at formidling finnes
        assertThat(formidlingService.hent(formidlingId)).isNotNull

        formidlingService.slett(treffId, formidlingUuid, "testperson", "dummytoken", "1234")

        assertThat(formidlingService.hent(formidlingId)).isNull()
    }


    @Test
    fun `slett formidling via treffId og UUID tilbakestiller jobbsøkerstatus til statusen før FÅTT_JOBB`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)
        val fnr = Fødselsnummer("12345678901")

        // Realistisk flyt: jobbsøker inviteres og svarer ja før formidling opprettes
        jobbsøkerService.inviter(listOf(personTreffId), treffId, "testperson")
        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, "testperson")

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)
        val formidlingUuid = formidlingService.hent(formidlingId)!!.id

        // Simuler at jobbsøkeren har fått jobb (slik opprettelse av formidling gjør)
        db.dataSource.executeInTransaction { connection ->
            jobbsøkerService.registrerFåttJobb(connection, personTreffId, "testperson")
        }
        assertThat(jobbsøkerService.hentJobbsøkere(treffId).first { it.personTreffId == personTreffId }.status)
            .isEqualTo(JobbsøkerStatus.FÅTT_JOBB)

        formidlingService.slett(treffId, formidlingUuid, "testperson", "dummytoken", "1234")

        assertThat(formidlingService.hent(formidlingId)).isNull()
        // Jobbsøkerstatus tilbakestilles til statusen før FÅTT_JOBB (SVART_JA)
        assertThat(jobbsøkerService.hentJobbsøkere(treffId).first { it.personTreffId == personTreffId }.status)
            .isEqualTo(JobbsøkerStatus.SVART_JA)
    }

    @Test
    fun `slett formidling tilbakestiller jobbsøkerstatus til SVART_NEI når det var statusen før FÅTT_JOBB`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)
        val fnr = Fødselsnummer("12345678901")

        jobbsøkerService.inviter(listOf(personTreffId), treffId, "testperson")
        jobbsøkerService.svarNeiTilInvitasjon(fnr, treffId, "testperson")

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)
        val formidlingUuid = formidlingService.hent(formidlingId)!!.id

        db.dataSource.executeInTransaction { connection ->
            jobbsøkerService.registrerFåttJobb(connection, personTreffId, "testperson")
        }
        assertThat(jobbsøkerService.hentJobbsøkere(treffId).first { it.personTreffId == personTreffId }.status)
            .isEqualTo(JobbsøkerStatus.FÅTT_JOBB)

        formidlingService.slett(treffId, formidlingUuid, "testperson", "dummytoken", "1234")

        assertThat(jobbsøkerService.hentJobbsøkere(treffId).first { it.personTreffId == personTreffId }.status)
            .isEqualTo(JobbsøkerStatus.SVART_NEI)
    }

    @Test
    fun `slett formidling via treffId og UUID kaster NotFoundResponse når formidling ikke finnes på treffet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        assertThatThrownBy {
            formidlingService.slett(treffId, UUID.randomUUID(), "testperson", "dummytoken", "1234")
        }.isInstanceOf(NotFoundResponse::class.java)
    }

    @Test
    fun `slett formidling via treffId og UUID er idempotent når formidlingen allerede er slettet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)
        val formidlingUuid = formidlingService.hent(formidlingId)!!.id

        // Slett første gang
        formidlingService.slett(treffId, formidlingUuid, "testperson", "dummytoken", "1234")
        assertThat(formidlingService.hent(formidlingId)).isNull()

        // Slett andre gang - skal være et no-op (idempotent), ikke kaste
        assertThatNoException().isThrownBy {
            formidlingService.slett(treffId, formidlingUuid, "testperson", "dummytoken", "1234")
        }
        assertThat(formidlingService.hent(formidlingId)).isNull()
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
        formidlingService.slett(treffId, formidlingService.hent(formidlingId1)!!.id, "testperson", "dummytoken", "1234")

        // Verifiser at bare den ene er slettet
        assertThat(formidlingService.hent(formidlingId1)).isNull()
        assertThat(formidlingService.hent(formidlingId2)).isNotNull
    }

    @Test
    fun `slettet formidling vises ikke ved hent med treffId, personTreffId og arbeidsgiverTreffId`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)
        val formidlingUuid = formidlingService.hent(formidlingId)!!.id

        val arbeidsgiverTreffId = arbeidsgiverService.hentArbeidsgivere(treffId).first().arbeidsgiverTreffId

        // Verifiser at formidling finnes
        assertThat(formidlingService.hent(treffId, personTreffId, arbeidsgiverTreffId)).isNotNull

        // Slett
        formidlingService.slett(treffId, formidlingUuid, "testperson", "dummytoken", "1234")

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
            kontornummer = "1234",
            kontornavn = "Nav Test",
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

