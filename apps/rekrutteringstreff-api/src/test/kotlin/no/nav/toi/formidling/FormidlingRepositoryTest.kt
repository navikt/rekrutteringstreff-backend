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
import java.time.ZonedDateTime
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
    fun `hent skiller mellom formidling med og uten sendt utfall`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val orgnr = "123456789"
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId,
            "testperson"
        )

        val fnrMedSendtUtfall = "11111111111"
        val fnrUtenSendtUtfall = "22222222222"
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer(fnrMedSendtUtfall), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer(fnrUtenSendtUtfall), Fornavn("Kari"), Etternavn("Nordmann"), null, null, null),
            ),
            treffId,
            "testperson"
        )

        val stillingId = UUID.randomUUID()
        db.dataSource.executeInTransaction { connection ->
            repository.opprett(
                connection,
                treffId,
                personTreffIder[0],
                arbeidsgiverTreffId,
                stillingId,
                kandidatlisteId = null,
                utfallSendtTidspunkt = ZonedDateTime.now().minusMinutes(1),
            )
            repository.opprett(
                connection,
                treffId,
                personTreffIder[1],
                arbeidsgiverTreffId,
                stillingId,
                kandidatlisteId = null,
                utfallSendtTidspunkt = null,
            )
        }

        val formidlingMedSendtUtfall = repository.hent(treffId, personTreffIder[0], arbeidsgiverTreffId)
        val formidlingUtenSendtUtfall = repository.hent(treffId, personTreffIder[1], arbeidsgiverTreffId)

        assertThat(formidlingMedSendtUtfall).isNotNull
        assertThat(formidlingMedSendtUtfall!!.utfallSendtTidspunkt).isNotNull()
        assertThat(formidlingUtenSendtUtfall).isNotNull
        assertThat(formidlingUtenSendtUtfall!!.utfallSendtTidspunkt).isNull()
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

    @Test
    fun `hentAlleForTreff returnerer alle formidlinger med jobbsøker- og arbeidsgiverdata`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Testbedrift AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Frida"), Etternavn("Testberg"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("Z111111")),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Petter"), Etternavn("Eksempel"), Kontor("1000", "Nav Test"), VeilederNavn("Veil A"), VeilederNavIdent("Z111111")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, UUID.randomUUID())

        val linjer = repository.hentAlleForTreff(treffId)

        assertThat(linjer).hasSize(2)
        val frida = linjer.single { it.fødselsnummer == "11111111111" }
        assertThat(frida.fornavn).isEqualTo("Frida")
        assertThat(frida.etternavn).isEqualTo("Testberg")
        assertThat(frida.orgnr).isEqualTo("123456789")
        assertThat(frida.orgnavn).isEqualTo("Testbedrift AS")
        assertThat(frida.stillingId).isEqualTo(stillingId)
    }

    @Test
    fun `hentAlleForTreff returnerer tom liste når treffet ikke har formidlinger`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")

        assertThat(repository.hentAlleForTreff(treffId)).isEmpty()
    }

    @Test
    fun `hentEgneForTreff returnerer kun formidlinger der bruker er veileder eller har kontortilgang`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Testbedrift AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Egen"), Etternavn("Veileder"), Kontor("1000", "Nav Test"), VeilederNavn("Min Veil"), VeilederNavIdent("Z111111")),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Annet"), Etternavn("Kontor"), Kontor("2000", "Nav Andre"), VeilederNavn("Annen Veil"), VeilederNavIdent("Z999999")),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, UUID.randomUUID())

        val somVeileder = repository.hentEgneForTreff(treffId, "Z111111", emptyList())
        assertThat(somVeileder).hasSize(1)
        assertThat(somVeileder.single().fødselsnummer).isEqualTo("11111111111")

        val viaKontor = repository.hentEgneForTreff(treffId, "ukjent", listOf("2000"))
        assertThat(viaKontor).hasSize(1)
        assertThat(viaKontor.single().fødselsnummer).isEqualTo("22222222222")

        val ingenTilgang = repository.hentEgneForTreff(treffId, "ukjent", emptyList())
        assertThat(ingenTilgang).isEmpty()
    }

    @Test
    fun `hentAlleForTreff sorterer på nyeste formidlingstidspunkt som standard`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Testbedrift AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Eldst"), Etternavn("Aase"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Nyest"), Etternavn("Bø"), null, null, null),
            ),
            treffId, "testperson",
        )
        db.dataSource.executeInTransaction { connection ->
            repository.opprett(connection, treffId, personTreffIder[0], arbeidsgiverTreffId, UUID.randomUUID(), null, ZonedDateTime.now().minusDays(1))
            repository.opprett(connection, treffId, personTreffIder[1], arbeidsgiverTreffId, UUID.randomUUID(), null, ZonedDateTime.now())
        }

        val linjer = repository.hentAlleForTreff(treffId)

        assertThat(linjer.map { it.fødselsnummer })
            .containsExactly("22222222222", "11111111111")
    }

    @Test
    fun `hentAlleForTreff kan sortere på arbeidsgivernavn`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val agA = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("111111111"), Orgnavn("Alfa AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val agB = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("222222222"), Orgnavn("Beta AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Hos"), Etternavn("Beta"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Hos"), Etternavn("Alfa"), null, null, null),
            ),
            treffId, "testperson",
        )
        db.opprettFormidling(treffId, personTreffIder[0], agB, UUID.randomUUID(), UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], agA, UUID.randomUUID(), UUID.randomUUID())

        val linjer = repository.hentAlleForTreff(treffId, FormidlingSortering.ARBEIDSGIVER)

        assertThat(linjer.map { it.orgnavn })
            .containsExactly("Alfa AS", "Beta AS")
    }

    @Test
    fun `hentAlleForTreff kan sortere på jobbsøkernavn`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Testbedrift AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Ås"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Kari"), Etternavn("Berg"), null, null, null),
            ),
            treffId, "testperson",
        )
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, UUID.randomUUID(), UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, UUID.randomUUID(), UUID.randomUUID())

        val linjer = repository.hentAlleForTreff(treffId, FormidlingSortering.JOBBSOKER)

        assertThat(linjer.map { it.etternavn })
            .containsExactly("Berg", "Ås")
    }

    @Test
    fun `hentAlleForTreff kan filtrere på arbeidsgiverens orgnr`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val agA = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("111111111"), Orgnavn("Alfa AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val agB = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("222222222"), Orgnavn("Beta AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Hos"), Etternavn("Alfa"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Hos"), Etternavn("Beta"), null, null, null),
            ),
            treffId, "testperson",
        )
        db.opprettFormidling(treffId, personTreffIder[0], agA, UUID.randomUUID(), UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], agB, UUID.randomUUID(), UUID.randomUUID())

        val linjer = repository.hentAlleForTreff(treffId, arbeidsgivere = listOf("111111111"))

        assertThat(linjer).hasSize(1)
        assertThat(linjer.single().orgnr).isEqualTo("111111111")
    }

    @Test
    fun `hentAlleForTreff kan filtrere på flere arbeidsgivere samtidig`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val agA = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("111111111"), Orgnavn("Alfa AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val agB = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("222222222"), Orgnavn("Beta AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val agC = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("333333333"), Orgnavn("Gamma AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Hos"), Etternavn("Alfa"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Hos"), Etternavn("Beta"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Hos"), Etternavn("Gamma"), null, null, null),
            ),
            treffId, "testperson",
        )
        db.opprettFormidling(treffId, personTreffIder[0], agA, UUID.randomUUID(), UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], agB, UUID.randomUUID(), UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[2], agC, UUID.randomUUID(), UUID.randomUUID())

        val linjer = repository.hentAlleForTreff(treffId, arbeidsgivere = listOf("111111111", "333333333"))

        assertThat(linjer.map { it.orgnr }).containsExactlyInAnyOrder("111111111", "333333333")
    }

    @Test
    fun `hentAlleForTreff kan sortere på eldste formidlingstidspunkt med stigende retning`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Testbedrift AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Eldst"), Etternavn("Aase"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Nyest"), Etternavn("Bø"), null, null, null),
            ),
            treffId, "testperson",
        )
        db.dataSource.executeInTransaction { connection ->
            repository.opprett(connection, treffId, personTreffIder[0], arbeidsgiverTreffId, UUID.randomUUID(), null, ZonedDateTime.now().minusDays(1))
            repository.opprett(connection, treffId, personTreffIder[1], arbeidsgiverTreffId, UUID.randomUUID(), null, ZonedDateTime.now())
        }

        val linjer = repository.hentAlleForTreff(
            treffId,
            FormidlingSortering.TIDSPUNKT,
            FormidlingSorteringsretning.STIGENDE,
        )

        assertThat(linjer.map { it.fødselsnummer })
            .containsExactly("11111111111", "22222222222")
    }

    @Test
    fun `hentAlleForTreff kan sortere på arbeidsgivernavn med synkende retning`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val agA = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("111111111"), Orgnavn("Alfa AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val agB = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("222222222"), Orgnavn("Beta AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Hos"), Etternavn("Beta"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Hos"), Etternavn("Alfa"), null, null, null),
            ),
            treffId, "testperson",
        )
        db.opprettFormidling(treffId, personTreffIder[0], agB, UUID.randomUUID(), UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], agA, UUID.randomUUID(), UUID.randomUUID())

        val linjer = repository.hentAlleForTreff(
            treffId,
            FormidlingSortering.ARBEIDSGIVER,
            FormidlingSorteringsretning.SYNKENDE,
        )

        assertThat(linjer.map { it.orgnavn })
            .containsExactly("Beta AS", "Alfa AS")
    }

    @Test
    fun `opprett lagrer yrkestittel og janzzKonseptId, og yrkestittel returneres i listen`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Testbedrift AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
            ),
            treffId, "testperson",
        )
        val formidlingId = db.dataSource.executeInTransaction { connection ->
            repository.opprett(
                connection,
                treffId,
                personTreffIder[0],
                arbeidsgiverTreffId,
                UUID.randomUUID(),
                kandidatlisteId = null,
                utfallSendtTidspunkt = null,
                yrkestittel = "Utvikler (dataspill)",
                janzzKonseptId = "19989",
            )
        }

        val linjer = repository.hentAlleForTreff(treffId)
        assertThat(linjer).hasSize(1)
        assertThat(linjer.single().yrkestittel).isEqualTo("Utvikler (dataspill)")

        val lagretJanzzKonseptId = db.dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT janzz_konsept_id FROM formidling WHERE formidling_id = ?").use { stmt ->
                stmt.setLong(1, formidlingId)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getString("janzz_konsept_id")
                }
            }
        }
        assertThat(lagretJanzzKonseptId).isEqualTo("19989")
    }

    @Test
    fun `hentAlleForTreff skjuler fødselsnummer for usynlig jobbsøker`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Testbedrift AS"), emptyList(), null, null, null),
            treffId, "testperson",
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Aase"), null, null, null),
                LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Usynlig"), Etternavn("Bø"), null, null, null),
            ),
            treffId, "testperson",
        )
        val stillingId = UUID.randomUUID()
        db.opprettFormidling(treffId, personTreffIder[0], arbeidsgiverTreffId, stillingId, UUID.randomUUID())
        db.opprettFormidling(treffId, personTreffIder[1], arbeidsgiverTreffId, stillingId, UUID.randomUUID())

        db.settSynlighet(personTreffIder[1], false)

        val linjer = repository.hentAlleForTreff(treffId)
        assertThat(linjer).hasSize(2)

        val synlig = linjer.single { it.etternavn == "Aase" }
        assertThat(synlig.fødselsnummer).isEqualTo("11111111111")

        val usynlig = linjer.single { it.etternavn == "Bø" }
        assertThat(usynlig.fødselsnummer).isNull()
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

