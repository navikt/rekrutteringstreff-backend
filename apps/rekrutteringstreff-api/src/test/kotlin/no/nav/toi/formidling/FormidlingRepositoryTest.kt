package no.nav.toi.formidling

import no.nav.toi.executeInTransaction
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormidlingRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: FormidlingRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
            repository = FormidlingRepository(db.dataSource)
        }
    }

    @AfterEach
    fun slettAlt() {
        db.slettAlt()
    }

    @Test
    fun `opprett formidling og hent den tilbake`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, arbeidsgiverOrgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, arbeidsgiverOrgnr, stillingId, kandidatlisteId)

        val formidling = repository.hent(formidlingId)
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
    fun `opprett formidling med valgfrie felter satt til null`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, arbeidsgiverOrgnr, stillingId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.dataSource.executeInTransaction { connection ->
            repository.opprett(
                connection,
                treffId,
                personTreffId,
                arbeidsgiverOrgnr,
                stillingId,
                kandidatlisteId = null,
                utfallSendtTidspunkt = null,
            )
        }

        val formidling = repository.hent(formidlingId)
        assertThat(formidling).isNotNull
        formidling!!
        assertThat(formidling.kandidatlisteId).isNull()
        assertThat(formidling.utfallSendtTidspunkt).isNull()
    }

    @Test
    fun `hent formidling returnerer null når den ikke finnes`() {
        val formidling = repository.hent(999999L)
        assertThat(formidling).isNull()
    }

    @Test
    fun `oppdaterUtfallSendtTidspunkt setter tidspunktet til nå`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.dataSource.executeInTransaction { connection ->
            repository.opprett(
                connection,
                treffId,
                personTreffId,
                orgnr,
                stillingId,
                kandidatlisteId = null,
                utfallSendtTidspunkt = null,
            )
        }

        val formidlingFør = repository.hent(formidlingId)
        assertThat(formidlingFør!!.utfallSendtTidspunkt).isNull()

        db.dataSource.executeInTransaction { connection ->
            repository.oppdaterUtfallSendtTidspunkt(connection, formidlingId)
        }

        val formidlingEtter = repository.hent(formidlingId)
        assertThat(formidlingEtter!!.utfallSendtTidspunkt).isNotNull()
        assertThat(formidlingEtter.utfallSendtTidspunkt!!.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
    }

    @Test
    fun `opprett flere formidlinger for samme treff`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val orgnr = "123456789"
        val stillingId = UUID.randomUUID()
        val kandidatlisteId = UUID.randomUUID()

        // Opprett arbeidsgiver
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

        // Opprett to jobbsøkere
        val jobbsøker1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        val jobbsøker2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Nordmann"), null, null, null)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker1, jobbsøker2), treffId, "testperson")

        val formidlingId1 = db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, kandidatlisteId)
        val formidlingId2 = db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, kandidatlisteId)

        val formidling1 = repository.hent(formidlingId1)
        val formidling2 = repository.hent(formidlingId2)

        assertThat(formidling1).isNotNull
        assertThat(formidling2).isNotNull
        assertThat(formidling1!!.jobbsøkerPersonTreffId).isEqualTo(personTreffIder[0])
        assertThat(formidling2!!.jobbsøkerPersonTreffId).isEqualTo(personTreffIder[1])
        assertThat(formidling1.id).isNotEqualTo(formidling2.id)
    }

    @Test
    fun `hent formidling med treffId, personTreffId og arbeidsgiverTreffId`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

        // Hent arbeidsgiverTreffId
        val arbeidsgivere = db.hentAlleArbeidsgivere()
        val arbeidsgiverTreffId = arbeidsgivere.first().arbeidsgiverTreffId

        val formidling = repository.hent(treffId, personTreffId, arbeidsgiverTreffId)
        assertThat(formidling).isNotNull
        formidling!!
        assertThat(formidling.treffId).isEqualTo(treffId)
        assertThat(formidling.jobbsøkerPersonTreffId).isEqualTo(personTreffId)
        assertThat(formidling.arbeidsgiverTreffId).isEqualTo(arbeidsgiverTreffId)
    }

    @Test
    fun `hent formidling med treffId, personTreffId og arbeidsgiverTreffId returnerer null når den ikke finnes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")

        val formidling = repository.hent(
            treffId,
            PersonTreffId(UUID.randomUUID()),
            ArbeidsgiverTreffId(UUID.randomUUID())
        )
        assertThat(formidling).isNull()
    }

    @Test
    fun `markerSlettet markerer formidling som slettet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

        // Verifiser at formidling finnes
        assertThat(repository.hent(formidlingId)).isNotNull

        // Marker som slettet
        val slettet = db.dataSource.connection.use { conn ->
            repository.markerSlettet(conn, formidlingId)
        }
        assertThat(slettet).isTrue()

        // Verifiser at formidling ikke lenger returneres
        assertThat(repository.hent(formidlingId)).isNull()
    }

    @Test
    fun `markerSlettet returnerer false når formidling ikke finnes`() {
        val slettet = db.dataSource.connection.use { conn ->
            repository.markerSlettet(conn, 999999L)
        }
        assertThat(slettet).isFalse()
    }

    @Test
    fun `markerSlettet returnerer false for allerede slettet formidling`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

        // Slett første gang
        db.dataSource.connection.use { conn ->
            repository.markerSlettet(conn, formidlingId)
        }

        // Slett andre gang - skal returnere false
        val slettetIgjen = db.dataSource.connection.use { conn ->
            repository.markerSlettet(conn, formidlingId)
        }
        assertThat(slettetIgjen).isFalse()
    }

    @Test
    fun `slettet formidling vises ikke ved hent med treffId, personTreffId og arbeidsgiverTreffId`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val (personTreffId, orgnr, stillingId, kandidatlisteId) = opprettTestdataForFormidling(treffId)

        val formidlingId = db.opprettFormidling(treffId, personTreffId, orgnr, stillingId, kandidatlisteId)

        val arbeidsgivere = db.hentAlleArbeidsgivere()
        val arbeidsgiverTreffId = arbeidsgivere.first().arbeidsgiverTreffId

        // Verifiser at formidling finnes
        assertThat(repository.hent(treffId, personTreffId, arbeidsgiverTreffId)).isNotNull

        // Slett
        db.dataSource.connection.use { conn ->
            repository.markerSlettet(conn, formidlingId)
        }

        // Verifiser at formidling ikke lenger returneres
        assertThat(repository.hent(treffId, personTreffId, arbeidsgiverTreffId)).isNull()
    }

    private data class FormidlingTestdata(
        val personTreffId: PersonTreffId,
        val arbeidsgiverTreffId: ArbeidsgiverTreffId,
        val stillingId: UUID,
        val kandidatalisteId: UUID,
    )

    private fun opprettTestdataForFormidling(treffId: TreffId): FormidlingTestdata {
        val orgnr = "123456789"
        val stillingId = UUID.randomUUID()
        val kandidatlisteId = UUID.randomUUID()

        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

        val jobbsøker = LeggTilJobbsøker(
            Fødselsnummer("12345678901"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            null, null, null
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")


        return FormidlingTestdata(personTreffIder.first(), arbeidsgiverTreffId, stillingId, kandidatlisteId)
    }
}

