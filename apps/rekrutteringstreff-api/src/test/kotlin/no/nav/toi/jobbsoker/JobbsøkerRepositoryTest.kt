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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

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
            Kandidatnummer("K123456"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            Navkontor("NAV Oslo"),
            VeilederNavn("Kari Nordmann"),
            VeilederNavIdent("NAV123"))
        )
        repository.leggTil(input, treffId, "testperson")
        val jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val js = jobbsøkere.first()
        assertThatCode { UUID.fromString(js.personTreffId.toString()) }.doesNotThrowAnyException()
        assertThat(js.fødselsnummer.asString).isEqualTo("12345678901")
        assertThat(js.kandidatnummer?.asString).isEqualTo("K123456")
        assertThat(js.fornavn.asString).isEqualTo("Ola")
        assertThat(js.etternavn.asString).isEqualTo("Nordmann")
        assertThat(js.navkontor?.asString).isEqualTo("NAV Oslo")
        assertThat(js.veilederNavn?.asString).isEqualTo("Kari Nordmann")
        assertThat(js.veilederNavIdent?.asString).isEqualTo("NAV123")
        assertThat(js.hendelser).hasSize(1)
        val h = js.hendelser.first()
        assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun leggTilJobbsøkerMedKunObligatoriskeFelterTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val input = listOf(LeggTilJobbsøker(
            Fødselsnummer("98765432109"),
            null,
            Fornavn("Knut"),
            Etternavn("Hansen"),
            null,
            null,
            null)
        )
        repository.leggTil(input, treffId, "testperson")
        val jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val js = jobbsøkere.first()
        assertThatCode { UUID.fromString(js.personTreffId.toString()) }.doesNotThrowAnyException()
        assertThat(js.fødselsnummer.asString).isEqualTo("98765432109")
        assertThat(js.kandidatnummer).isNull()
        assertThat(js.fornavn.asString).isEqualTo("Knut")
        assertThat(js.etternavn.asString).isEqualTo("Hansen")
        assertThat(js.navkontor).isNull()
        assertThat(js.veilederNavn).isNull()
        assertThat(js.veilederNavIdent).isNull()
        assertThat(js.hendelser).hasSize(1)
        val h = js.hendelser.first()
        assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT)
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
                Kandidatnummer("K1"),
                Fornavn("Fornavn1"),
                Etternavn("Etternavn1"),
                Navkontor("Oslo"),
                VeilederNavn("Veileder1"),
                VeilederNavIdent("NAV1")
            )
        )
        val js2 = listOf(
            Jobbsøker(
                PersonTreffId(UUID.randomUUID()),
                treffId2,
                Fødselsnummer("22222222222"),
                Kandidatnummer("K2"),
                Fornavn("Fornavn2"),
                Etternavn("Etternavn2"),
                Navkontor("Oslo"),
                VeilederNavn("Veileder1"),
                VeilederNavIdent("NAV1")
            ),
            Jobbsøker(
                PersonTreffId(UUID.randomUUID()),
                treffId2,
                Fødselsnummer("33333333333"),
                Kandidatnummer("K3"),
                Fornavn("Fornavn3"),
                Etternavn("Etternavn3"),
                Navkontor("Bergen"),
                VeilederNavn("Veileder2"),
                VeilederNavIdent("NAV2")
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
            assertThat(h.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT)
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
            Kandidatnummer("K1"),
            Fornavn("Fornavn1"),
            Etternavn("Etternavn1"),
            Navkontor("NAV Test"),
            VeilederNavn("Veileder Test"),
            VeilederNavIdent("V123456")
        )
        val leggTilJobbsøker2 = LeggTilJobbsøker(
            fødselsnummer2,
            Kandidatnummer("K2"),
            Fornavn("Fornavn2"),
            Etternavn("Etternavn2"),
            null, null, null
        )
        repository.leggTil(listOf(leggTilJobbsøker1, leggTilJobbsøker2), treffId, "testperson")

        val jobbsøker = repository.hentJobbsøker(treffId, fødselsnummer1)
        assertThat(jobbsøker).isNotNull
        jobbsøker!!
        assertThatCode { UUID.fromString(jobbsøker.personTreffId.toString()) }.doesNotThrowAnyException()
        assertThat(jobbsøker.fødselsnummer).isEqualTo(fødselsnummer1)
        assertThat(jobbsøker.kandidatnummer?.asString).isEqualTo("K1")
        assertThat(jobbsøker.fornavn.asString).isEqualTo("Fornavn1")
        assertThat(jobbsøker.etternavn.asString).isEqualTo("Etternavn1")
        assertThat(jobbsøker.navkontor?.asString).isEqualTo("NAV Test")
        assertThat(jobbsøker.veilederNavn?.asString).isEqualTo("Veileder Test")
        assertThat(jobbsøker.veilederNavIdent?.asString).isEqualTo("V123456")
        assertThat(jobbsøker.treffId).isEqualTo(treffId)
        assertThat(jobbsøker.hendelser).hasSize(1)
        assertThat(jobbsøker.hendelser.first().hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT)


        val ikkeEksisterendeJobbsøker = repository.hentJobbsøker(treffId, Fødselsnummer("99999999999"))
        assertThat(ikkeEksisterendeJobbsøker).isNull()
    }

    @Test
    fun hentJobbsøkerHendelserTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreffHendelser")

        val input = listOf(LeggTilJobbsøker(
            Fødselsnummer("11223344556"),
            Kandidatnummer("K7890"),
            Fornavn("Emil"),
            Etternavn("Hansen"),
            Navkontor("NAV Bergen"),
            VeilederNavn("Lars"),
            VeilederNavIdent("NAV456"))
        )
        repository.leggTil(input, treffId, "testperson")

        val hendelser = repository.hentJobbsøkerHendelser(treffId)

        assertThat(hendelser).hasSize(1)

        val hendelse = hendelser.first()
        assertThat(hendelse.fødselsnummer.asString).isEqualTo("11223344556")
        assertThat(hendelse.kandidatnummer?.asString).isEqualTo("K7890")
        assertThat(hendelse.fornavn.asString).isEqualTo("Emil")
        assertThat(hendelse.etternavn.asString).isEqualTo("Hansen")
        assertThat(hendelse.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETT)
        assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(hendelse.aktørIdentifikasjon).isEqualTo("testperson")
        assertThat(hendelse.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThatCode { UUID.fromString(hendelse.personTreffId.toString()) }.doesNotThrowAnyException()
    }

    @Test
    fun `inviter lager en inviter-hendelse for eksisterende jobbsøkere`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff for Invitasjon")
        val fødselsnummer = Fødselsnummer("12345678901")
        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer,
            Kandidatnummer("K123"),
            Fornavn("Test"),
            Etternavn("Person"),
            null, null, null
        )
        repository.leggTil(listOf(leggTilJobbsøker), treffId, "testperson")

        var jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere.first().hendelser).hasSize(1)

        val personTreffId = jobbsøkere.first().personTreffId

        repository.inviter(listOf(personTreffId), treffId, "inviterende_person")

        jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val hendelser = jobbsøkere.first().hendelser
        assertThat(hendelser).hasSize(2)

        val inviterHendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.INVITER }
        assertThat(inviterHendelse).isNotNull
        inviterHendelse!!
        assertThat(inviterHendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(inviterHendelse.aktørIdentifikasjon).isEqualTo("inviterende_person")
        assertThat(inviterHendelse.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.OPPRETT }).isNotNull
    }

    @Test
    fun `verifiser at oppmøte lager en registrer-oppmøte-hendelse for eksisterende jobbsøkere`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff for registrering av oppmøte")
        val fødselsnummer = Fødselsnummer("12345678901")
        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer,
            Kandidatnummer("K123"),
            Fornavn("Test"),
            Etternavn("Person"),
            null, null, null
        )
        repository.leggTil(listOf(leggTilJobbsøker), treffId, "testperson")

        var jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere.first().hendelser).hasSize(1)

        val personTreffId = jobbsøkere.first().personTreffId

        repository.registrerOppmøte(listOf(personTreffId), treffId, "oppmøtt_person")

        jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val hendelser = jobbsøkere.first().hendelser
        assertThat(hendelser).hasSize(2)

        val oppmøteHendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.MØT_OPP }
        assertThat(oppmøteHendelse).isNotNull
        oppmøteHendelse!!
        assertThat(oppmøteHendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(oppmøteHendelse.aktørIdentifikasjon).isEqualTo("oppmøtt_person")
        assertThat(oppmøteHendelse.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.OPPRETT }).isNotNull
    }

    @Test
    fun `svarJaTilInvitasjon lager en svar-ja-hendelse`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff for Svar Ja")
        val fødselsnummer = Fødselsnummer("12345678901")
        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer,
            Kandidatnummer("K123"),
            Fornavn("Test"),
            Etternavn("Person"),
            null, null, null
        )
        repository.leggTil(listOf(leggTilJobbsøker), treffId, "testperson")

        var jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere.first().hendelser).hasSize(1)

        repository.svarJaTilInvitasjon(fødselsnummer, treffId, "svar_ja_person")

        jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat  (jobbsøkere).hasSize(1)
        val hendelser = jobbsøkere.first().hendelser
        assertThat(hendelser).hasSize(2)

        val svarJaHendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.SVAR_JA_TIL_INVITASJON }
        assertThat(svarJaHendelse).isNotNull
        svarJaHendelse!!
        assertThat(svarJaHendelse.opprettetAvAktørType).isEqualTo(AktørType.JOBBSØKER)
        assertThat(svarJaHendelse.aktørIdentifikasjon).isEqualTo("svar_ja_person")
        assertThat(svarJaHendelse.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
    }

    @Test
    fun `svarNeiTilInvitasjon lager en svar-nei-hendelse`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff for Svar Nei")
        val fødselsnummer = Fødselsnummer("12345678901")
        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer,
            Kandidatnummer("K123"),
            Fornavn("Test"),
            Etternavn("Person"),
            null, null, null
        )
        repository.leggTil(listOf(leggTilJobbsøker), treffId, "testperson")

        var jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere.first().hendelser).hasSize(1)

        repository.svarNeiTilInvitasjon(fødselsnummer, treffId, "svar_nei_person")

        jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val hendelser = jobbsøkere.first().hendelser
        assertThat(hendelser).hasSize(2)

        val svarNeiHendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.SVAR_NEI_TIL_INVITASJON }
        assertThat(svarNeiHendelse).isNotNull
        svarNeiHendelse!!
        assertThat(svarNeiHendelse.opprettetAvAktørType).isEqualTo(AktørType.JOBBSØKER)
        assertThat(svarNeiHendelse.aktørIdentifikasjon).isEqualTo("svar_nei_person")
        assertThat(svarNeiHendelse.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
    }

    @Test
    fun `registrerAktivitetskortOpprettelseFeilet lager en feil-hendelse`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff for Feil")
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z987654"

        val leggTilJobbsøker = LeggTilJobbsøker(
            fødselsnummer,
            Kandidatnummer("K123"),
            Fornavn("Test"),
            Etternavn("Person"),
            null, null, null
        )
        repository.leggTil(listOf(leggTilJobbsøker), treffId, "testperson")

        var jobbsøker = repository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker!!.hendelser).hasSize(1)

        repository.registrerAktivitetskortOpprettelseFeilet(fødselsnummer, treffId, endretAvIdent)

        jobbsøker = repository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker!!.hendelser).hasSize(2)

        val feilHendelse = jobbsøker.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.AKTIVITETSKORT_OPPRETTELSE_FEIL }
        assertThat(feilHendelse).isNotNull
        feilHendelse!!.apply {
            assertThat(opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
            assertThat(aktørIdentifikasjon).isEqualTo(endretAvIdent)
            assertThat(tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        }
    }
}