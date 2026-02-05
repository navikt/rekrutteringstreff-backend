package no.nav.toi.jobbsoker

import no.nav.toi.AktørType
import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobbsøkerRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: JobbsøkerRepository
        private val mapper = JacksonConfig.mapper

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
            repository = JobbsøkerRepository(db.dataSource, mapper)
        }
    }

    @AfterEach
    fun slettAlt() {
        db.slettAlt()
    }

    @Test
    fun leggTilJobbsøkerTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val input = listOf(LeggTilJobbsøker(
            Fødselsnummer("12345678901"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            Navkontor("NAV Oslo"),
            VeilederNavn("Kari Nordmann"),
            VeilederNavIdent("NAV123"))
        )
        db.leggTilJobbsøkereMedHendelse(input, treffId, "testperson")
        val jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val js = jobbsøkere.first()
        assertThatCode { UUID.fromString(js.personTreffId.toString()) }.doesNotThrowAnyException()
        assertThat(js.fødselsnummer.asString).isEqualTo("12345678901")
        assertThat(js.fornavn.asString).isEqualTo("Ola")
        assertThat(js.etternavn.asString).isEqualTo("Nordmann")
        assertThat(js.navkontor?.asString).isEqualTo("NAV Oslo")
        assertThat(js.veilederNavn?.asString).isEqualTo("Kari Nordmann")
        assertThat(js.veilederNavIdent?.asString).isEqualTo("NAV123")
        assertThat(js.hendelser).hasSize(1)
        val h = js.hendelser.first()
        assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun leggTilJobbsøkerMedKunObligatoriskeFelterTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val input = listOf(LeggTilJobbsøker(
            Fødselsnummer("98765432109"),
            Fornavn("Knut"),
            Etternavn("Hansen"),
            null,
            null,
            null)
        )
        db.leggTilJobbsøkereMedHendelse(input, treffId, "testperson")
        val jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val js = jobbsøkere.first()
        assertThatCode { UUID.fromString(js.personTreffId.toString()) }.doesNotThrowAnyException()
        assertThat(js.fødselsnummer.asString).isEqualTo("98765432109")
        assertThat(js.fornavn.asString).isEqualTo("Knut")
        assertThat(js.etternavn.asString).isEqualTo("Hansen")
        assertThat(js.navkontor).isNull()
        assertThat(js.veilederNavn).isNull()
        assertThat(js.veilederNavIdent).isNull()
        assertThat(js.hendelser).hasSize(1)
        val h = js.hendelser.first()
        assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun hentJobbsøkereTest() {
        val treffId1 = db.opprettRekrutteringstreffIDatabase()
        val treffId2 = db.opprettRekrutteringstreffIDatabase()
        val js1 = listOf(
            Jobbsøker(
                PersonTreffId(UUID.randomUUID()),
                treffId1,
                Fødselsnummer("11111111111"),
                Fornavn("Fornavn1"),
                Etternavn("Etternavn1"),
                Navkontor("Oslo"),
                VeilederNavn("Veileder1"),
                VeilederNavIdent("NAV1"),
                JobbsøkerStatus.INVITERT,
            )
        )
        val js2 = listOf(
            Jobbsøker(
                PersonTreffId(UUID.randomUUID()),
                treffId2,
                Fødselsnummer("22222222222"),
                Fornavn("Fornavn2"),
                Etternavn("Etternavn2"),
                Navkontor("Oslo"),
                VeilederNavn("Veileder1"),
                VeilederNavIdent("NAV1"),
                JobbsøkerStatus.INVITERT,
            ),
            Jobbsøker(
                PersonTreffId(UUID.randomUUID()),
                treffId2,
                Fødselsnummer("33333333333"),
                Fornavn("Fornavn3"),
                Etternavn("Etternavn3"),
                Navkontor("Bergen"),
                VeilederNavn("Veileder2"),
                VeilederNavIdent("NAV2"),
                JobbsøkerStatus.INVITERT,
            )
        )
        db.leggTilJobbsøkere(js1)
        db.leggTilJobbsøkere(js2)
        val hentet = repository.hentJobbsøkere(treffId2)
        assertThat(hentet).hasSize(2)
        hentet.forEach { js ->
            assertThatCode { UUID.fromString(js.personTreffId.toString()) }.doesNotThrowAnyException()
            assertThat(js.hendelser).hasSize(1)
            val h = js.hendelser.first()
            assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
            assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
            assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
            assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
            assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
        }
    }

    @Test
    fun `hentJobbsøker henter riktig jobbsøker`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer1 = Fødselsnummer("11111111111")
        val fødselsnummer2 = Fødselsnummer("22222222222")

        val leggTilJobbsøker1 = LeggTilJobbsøker(
            fødselsnummer1,
            Fornavn("Fornavn1"),
            Etternavn("Etternavn1"),
            Navkontor("NAV Test"),
            VeilederNavn("Veileder Test"),
            VeilederNavIdent("V123456")
        )
        val leggTilJobbsøker2 = LeggTilJobbsøker(
            fødselsnummer2,
            Fornavn("Fornavn2"),
            Etternavn("Etternavn2"),
            null, null, null
        )
        db.leggTilJobbsøkereMedHendelse(listOf(leggTilJobbsøker1, leggTilJobbsøker2), treffId, "testperson")

        val jobbsøker = repository.hentJobbsøker(treffId, fødselsnummer1)
        assertThat(jobbsøker).isNotNull
        jobbsøker!!
        assertThatCode { UUID.fromString(jobbsøker.personTreffId.toString()) }.doesNotThrowAnyException()
        assertThat(jobbsøker.fødselsnummer).isEqualTo(fødselsnummer1)
        assertThat(jobbsøker.fornavn.asString).isEqualTo("Fornavn1")
        assertThat(jobbsøker.etternavn.asString).isEqualTo("Etternavn1")
        assertThat(jobbsøker.navkontor?.asString).isEqualTo("NAV Test")
        assertThat(jobbsøker.veilederNavn?.asString).isEqualTo("Veileder Test")
        assertThat(jobbsøker.veilederNavIdent?.asString).isEqualTo("V123456")
        assertThat(jobbsøker.treffId).isEqualTo(treffId)
        assertThat(jobbsøker.hendelser).hasSize(1)
        assertThat(jobbsøker.hendelser.first().hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)


        val ikkeEksisterendeJobbsøker = repository.hentJobbsøker(treffId, Fødselsnummer("99999999999"))
        assertThat(ikkeEksisterendeJobbsøker).isNull()
    }

    @Test
    fun `Hent antall jobbsøkere`() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(
                Fødselsnummer("12345678901"),
                Fornavn("Ola"),
                Etternavn("Nordmann"),
                Navkontor("Nav Oslo"),
                VeilederNavn("Kari Nordmann"),
                VeilederNavIdent("NAV123")
            ),
            LeggTilJobbsøker(
                Fødselsnummer("12345678902"),
                Fornavn("Ole"),
                Etternavn("Nordmann"),
                Navkontor("Nav Oslo"),
                VeilederNavn("Kari Nordmann"),
                VeilederNavIdent("NAV123")
            )
        )
        db.leggTilJobbsøkereMedHendelse(jobbsøkere, treffId, "testperson")
        val antallJobbsøkere = repository.hentAntallJobbsøkere(treffId)
        assertThat(antallJobbsøkere == 2)
    }

    @Test
    fun hentJobbsøkerHendelserTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreffHendelser")

        val input = listOf(LeggTilJobbsøker(
            Fødselsnummer("11223344556"),
            Fornavn("Emil"),
            Etternavn("Hansen"),
            Navkontor("NAV Bergen"),
            VeilederNavn("Lars"),
            VeilederNavIdent("NAV456"))
        )
        db.leggTilJobbsøkereMedHendelse(input, treffId, "testperson")

        val hendelser = repository.hentJobbsøkerHendelser(treffId)

        assertThat(hendelser).hasSize(1)

        val hendelse = hendelser.first()
        assertThat(hendelse.fødselsnummer.asString).isEqualTo("11223344556")
        assertThat(hendelse.fornavn.asString).isEqualTo("Emil")
        assertThat(hendelse.etternavn.asString).isEqualTo("Hansen")
        assertThat(hendelse.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
        assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(hendelse.aktørIdentifikasjon).isEqualTo("testperson")
        assertThat(hendelse.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThatCode { UUID.fromString(hendelse.personTreffId.toString()) }.doesNotThrowAnyException()
    }


    @Test
    fun `hentJobbsøkere med Connection returnerer jobbsøkere med hendelser sortert DESC`() {
        val navIdent = "testperson"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "Test")

        val jobbsøker1 = LeggTilJobbsøker(
            Fødselsnummer("12345678901"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            null, null, null
        )

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker1), treffId, navIdent)

        val alleJobbsøkere = repository.hentJobbsøkere(treffId)
        db.inviterJobbsøkere(alleJobbsøkere.map { it.personTreffId }, treffId, navIdent)

        db.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)

        db.dataSource.connection.use { c ->
            val jobbsøkere = repository.hentJobbsøkere(c, treffId)
            assertThat(jobbsøkere).hasSize(1)

            val hendelser = jobbsøkere[0].hendelser
            assertThat(hendelser).hasSizeGreaterThanOrEqualTo(3) // OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON

            assertThat(hendelser[0].hendelsestype).isEqualTo(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON)
            assertThat(hendelser[1].hendelsestype).isEqualTo(JobbsøkerHendelsestype.INVITERT)
            assertThat(hendelser[2].hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
        }
    }

    @Test
    fun `hentJobbsøkere uten Connection bruker intern Connection og gir samme resultat`() {
        val navIdent = "testperson"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "Test")

        val jobbsøker1 = LeggTilJobbsøker(
            Fødselsnummer("12345678901"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            null, null, null
        )

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker1), treffId, navIdent)

        val alleJobbsøkere = repository.hentJobbsøkere(treffId)
        db.inviterJobbsøkere(alleJobbsøkere.map { it.personTreffId }, treffId, navIdent)

        val jobbsøkereUtenConn = repository.hentJobbsøkere(treffId)

        val jobbsøkereMedConn = db.dataSource.connection.use { c ->
            repository.hentJobbsøkere(c, treffId)
        }

        assertThat(jobbsøkereUtenConn).hasSize(1)
        assertThat(jobbsøkereMedConn).hasSize(1)
        assertThat(jobbsøkereUtenConn[0].personTreffId).isEqualTo(jobbsøkereMedConn[0].personTreffId)
        assertThat(jobbsøkereUtenConn[0].hendelser).hasSize(jobbsøkereMedConn[0].hendelser.size)
    }

    @Test
    fun `hentJobbsøkere returnerer alle jobbsøkere med deres hendelser`() {
        val navIdent = "testperson"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "Test")

        val jobbsøker1 = LeggTilJobbsøker(
            Fødselsnummer("12345678901"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            null, null, null
        )
        val jobbsøker2 = LeggTilJobbsøker(
            Fødselsnummer("23456789012"),
            Fornavn("Kari"),
            Etternavn("Nordmann"),
            null, null, null
        )

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker1, jobbsøker2), treffId, navIdent)

        val alleJobbsøkere = repository.hentJobbsøkere(treffId)
        db.inviterJobbsøkere(alleJobbsøkere.map { it.personTreffId }, treffId, navIdent)

        db.svarJaTilInvitasjon(jobbsøker1.fødselsnummer, treffId, jobbsøker1.fødselsnummer.asString)

        db.dataSource.connection.use { c ->
            val jobbsøkere = repository.hentJobbsøkere(c, treffId)
            assertThat(jobbsøkere).hasSize(2)

            val js1 = jobbsøkere.find { it.fødselsnummer == jobbsøker1.fødselsnummer }
            val js2 = jobbsøkere.find { it.fødselsnummer == jobbsøker2.fødselsnummer }

            assertThat(js1).isNotNull
            assertThat(js2).isNotNull

            assertThat(js1!!.hendelser[0].hendelsestype).isEqualTo(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON)
            assertThat(js2!!.hendelser[0].hendelsestype).isEqualTo(JobbsøkerHendelsestype.INVITERT)
        }
    }

    @Test
    fun `status slettet skal ikke synes i listen`() {
        val navIdent = "testperson"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "Test")

        val jobbsøker = LeggTilJobbsøker(
            Fødselsnummer("12345678901"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            null, null, null
        )

        db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, navIdent)

        val alleJobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(alleJobbsøkere).hasSize(1)
        db.dataSource.connection.use { c ->
            repository.endreStatus(c, alleJobbsøkere.get(0).personTreffId, JobbsøkerStatus.SLETTET)
        }
        val alleJobbsøkereEtterSletting = repository.hentJobbsøkere(treffId)
        assertThat(alleJobbsøkereEtterSletting).hasSize(0)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Synlighets-tester
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `hentJobbsøkere skal filtrere ut ikke-synlige jobbsøkere`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")

        val synligJobbsøker = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Person"), null, null, null)
        val ikkeSynligJobbsøker = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("IkkeSynlig"), Etternavn("Person"), null, null, null)

        db.leggTilJobbsøkereMedHendelse(listOf(synligJobbsøker, ikkeSynligJobbsøker), treffId, "testperson")

        // Hent begge for å få personTreffId, og sett synlighet
        val alleJobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(alleJobbsøkere).hasSize(2)

        val ikkeSynligId = alleJobbsøkere.find { it.fødselsnummer.asString == "22222222222" }!!.personTreffId
        db.settSynlighet(ikkeSynligId, false)

        // Hent igjen - skal nå bare returnere synlig jobbsøker
        val synligeJobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(synligeJobbsøkere).hasSize(1)
        assertThat(synligeJobbsøkere.first().fødselsnummer.asString).isEqualTo("11111111111")
    }

    @Test
    fun `hentAntallJobbsøkere skal ikke telle ikke-synlige jobbsøkere`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")

        val jobbsøker1 = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("En"), Etternavn("Person"), null, null, null)
        val jobbsøker2 = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("To"), Etternavn("Person"), null, null, null)
        val jobbsøker3 = LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Tre"), Etternavn("Person"), null, null, null)

        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker1, jobbsøker2, jobbsøker3), treffId, "testperson")

        // Verifiser at alle 3 telles
        assertThat(repository.hentAntallJobbsøkere(treffId)).isEqualTo(3)

        // Sett én som ikke-synlig
        db.settSynlighet(personTreffIder[1], false)

        // Verifiser at bare 2 telles
        assertThat(repository.hentAntallJobbsøkere(treffId)).isEqualTo(2)
    }

    @Test
    fun `hentJobbsøkerHendelser skal filtrere ut hendelser for ikke-synlige jobbsøkere`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")

        val synligJobbsøker = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Person"), null, null, null)
        val ikkeSynligJobbsøker = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("IkkeSynlig"), Etternavn("Person"), null, null, null)

        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(synligJobbsøker, ikkeSynligJobbsøker), treffId, "testperson")

        // Begge har OPPRETTET-hendelse, så vi forventer 2 hendelser
        val alleHendelser = repository.hentJobbsøkerHendelser(treffId)
        assertThat(alleHendelser).hasSize(2)

        // Sett én som ikke-synlig
        db.settSynlighet(personTreffIder[1], false)

        // Verifiser at bare hendelser for synlig jobbsøker returneres
        val synligeHendelser = repository.hentJobbsøkerHendelser(treffId)
        assertThat(synligeHendelser).hasSize(1)
        assertThat(synligeHendelser.first().fødselsnummer.asString).isEqualTo("11111111111")
    }

    @Test
    fun `hentJobbsøker skal returnere null for ikke-synlig jobbsøker`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøker = LeggTilJobbsøker(fnr, Fornavn("Test"), Etternavn("Person"), null, null, null)

        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, "testperson")

        // Verifiser at jobbsøker finnes
        val hentetJobbsøker = repository.hentJobbsøker(treffId, fnr)
        assertThat(hentetJobbsøker).isNotNull

        // Sett som ikke-synlig
        db.settSynlighet(personTreffIder[0], false)

        // Verifiser at jobbsøker ikke returneres
        val ikkeSynligJobbsøker = repository.hentJobbsøker(treffId, fnr)
        assertThat(ikkeSynligJobbsøker).isNull()
    }

    @Test
    fun `hentJobbsøkerTellinger returnerer gjensidig utelukkende tellinger`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")

        // Lag 4 jobbsøkere
        val jobbsøkere = listOf(
            LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig1"), Etternavn("Person"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("Synlig2"), Etternavn("Person"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("33333333333"), Fornavn("Skjult"), Etternavn("Person"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("44444444444"), Fornavn("Slettet"), Etternavn("Person"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("55555555555"), Fornavn("SlettetOgSkjult"), Etternavn("Person"), null, null, null)
        )
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(jobbsøkere, treffId, "testperson")

        // Sett én som skjult (ikke slettet)
        db.settSynlighet(personTreffIder[2], false)

        // Slett én (synlig)
        db.settJobbsøkerStatus(personTreffIder[3], JobbsøkerStatus.SLETTET)

        // Slett og skjul én (skal telles som slettet, ikke skjult)
        db.settSynlighet(personTreffIder[4], false)
        db.settJobbsøkerStatus(personTreffIder[4], JobbsøkerStatus.SLETTET)

        // Hent tellinger
        val tellinger = repository.hentJobbsøkerTellinger(treffId)

        // Verifiser gjensidig utelukkende kategorier
        assertThat(tellinger.antallSkjulte).isEqualTo(1)  // Bare "Skjult" (ikke slettet)
        assertThat(tellinger.antallSlettede).isEqualTo(2)  // "Slettet" og "SlettetOgSkjult"

        // Verifiser at synlige jobbsøkere er korrekt
        val synligeJobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(synligeJobbsøkere).hasSize(2)  // "Synlig1" og "Synlig2"

        // Invariant: synlige + skjulte + slettede = totalt (5)
        assertThat(synligeJobbsøkere.size + tellinger.antallSkjulte + tellinger.antallSlettede).isEqualTo(5)
    }
}
