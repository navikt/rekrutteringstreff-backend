package no.nav.toi.arbeidsgiver

import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArbeidsgiverRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: ArbeidsgiverRepository
        private val mapper = JacksonConfig.mapper

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
            repository = ArbeidsgiverRepository(db.dataSource, mapper)
        }
    }

    @AfterEach
    fun slettAlt() {
        db.slettAlt()
    }

    @Test
    fun leggTilArbeidsgiverTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val input = LeggTilArbeidsgiver(
            Orgnr("123456789"),
            Orgnavn("Example Company"),
            listOf(Næringskode("47.111", "Detaljhandel med bredt varesortiment uten salg av drivstoff")),
            "Fyrstikkalleen 1",
            "0661",
            "Oslo",
        )
        db.leggTilArbeidsgiverMedHendelse(input, treffId, "testperson")
        val arbeidsgivere = repository.hentArbeidsgivere(treffId)
        assertThat(arbeidsgivere).hasSize(1)
        val ag = arbeidsgivere.first()
        assertThatCode { UUID.fromString(ag.arbeidsgiverTreffId.toString()) }.doesNotThrowAnyException()
        assertThat(ag.orgnr.asString).isEqualTo("123456789")
        assertThat(ag.orgnavn.asString).isEqualTo("Example Company")
        assertThat(ag.gateadresse).isEqualTo("Fyrstikkalleen 1")
        assertThat(ag.postnummer).isEqualTo("0661")
        assertThat(ag.poststed).isEqualTo("Oslo")

        val arbeidsgiverHendelser = repository.hentArbeidsgiverHendelser(treffId)
        val h = arbeidsgiverHendelser.first()
        assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETTET)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktøridentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun hentArbeidsgivereTest() {
        val treffId1 = db.opprettRekrutteringstreffIDatabase()
        val treffId2 = db.opprettRekrutteringstreffIDatabase()
        val ag1 = Arbeidsgiver(ArbeidsgiverTreffId(UUID.randomUUID()), treffId1, Orgnr("111111111"), Orgnavn("Company A"), ArbeidsgiverStatus.AKTIV, "Fyrstikkalleen 1", "0661", "Oslo")
        val ag2 = Arbeidsgiver(ArbeidsgiverTreffId(UUID.randomUUID()), treffId2, Orgnr("222222222"), Orgnavn("Company B"), ArbeidsgiverStatus.AKTIV, "Fyrstikkalleen 1", "0661", "Oslo")
        val ag3 = Arbeidsgiver(ArbeidsgiverTreffId(UUID.randomUUID()), treffId2, Orgnr("333333333"), Orgnavn("Company C"), ArbeidsgiverStatus.AKTIV, "Fyrstikkalleen 1", "0661", "Oslo")
        db.leggTilArbeidsgivere(listOf(ag1))
        db.leggTilArbeidsgivere(listOf(ag2, ag3))
        val hentet = repository.hentArbeidsgivere(treffId2)
        assertThat(hentet).hasSize(2)
        hentet.forEach {
            assertThatCode { UUID.fromString(it.arbeidsgiverTreffId.toString()) }.doesNotThrowAnyException()
            assertThat(it.treffId).isEqualTo(treffId2)
        }
    }

    @Test
    fun hentArbeidsgiverHendelserTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreffHendelser")
        val input = LeggTilArbeidsgiver(
            Orgnr("444444444"),
            Orgnavn("Company D"),
            listOf(Næringskode("47.111", "Detaljhandel med bredt varesortiment uten salg av drivstoff")),
            "Fyrstikkalleen 1",
            "0661",
            "Oslo",
        )
        db.leggTilArbeidsgiverMedHendelse(input, treffId, "testperson")
        val hendelser = repository.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser).hasSize(1)
        val h = hendelser.first()
        assertThat(h.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETTET)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktøridentifikasjon).isEqualTo("testperson")
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.orgnr.asString).isEqualTo("444444444")
        assertThat(h.orgnavn.asString).isEqualTo("Company D")
    }

    @Test
    fun slettArbeidsgiver_returnerer_true_og_sletter_rad() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val input = LeggTilArbeidsgiver(Orgnr("987654321"), Orgnavn("Slettbar Bedrift"), emptyList(), "Fyrstikkalleen 1", "0661", "Oslo")
        db.leggTilArbeidsgiverMedHendelse(input, treffId, "testperson")
        val id = repository.hentArbeidsgivere(treffId).first().arbeidsgiverTreffId

        val resultat = db.markerArbeidsgiverSlettet(id.somUuid, treffId, "testperson")
        assertThat(resultat).isTrue()
        assertThat(repository.hentArbeidsgivere(treffId)).isEmpty()
        val hendelser = repository.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser.any { it.hendelsestype == ArbeidsgiverHendelsestype.SLETTET }).isTrue()

        val arbeidsgiverSomErSlettet = repository.hentArbeidsgiver(treffId, input.orgnr)
        assertThat(arbeidsgiverSomErSlettet).isNotNull()
        assertThat(arbeidsgiverSomErSlettet?.status).isEqualTo(ArbeidsgiverStatus.SLETTET)
    }

    @Test
    fun slettArbeidsgiver_returnerer_false_når_den_ikke_finnes() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val tilfeldigId = UUID.randomUUID()
        assertThat(db.markerArbeidsgiverSlettet(tilfeldigId, treffId, "testperson")).isFalse()
    }

    @Test
    fun hentArbeidsgivere_filterer_bort_slettet_arbeidsgiver() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val ag1 = LeggTilArbeidsgiver(Orgnr("111111111"), Orgnavn("Synlig Bedrift"), emptyList(), "Fyrstikkalleen 1", "0661", "Oslo")
        val ag2 = LeggTilArbeidsgiver(Orgnr("222222222"), Orgnavn("Skal SLETTES"), emptyList(), "Fyrstikkalleen 1", "0661", "Oslo")
        db.leggTilArbeidsgiverMedHendelse(ag1, treffId, "testperson")
        db.leggTilArbeidsgiverMedHendelse(ag2, treffId, "testperson")

        val alleFør = repository.hentArbeidsgivere(treffId)
        assertThat(alleFør).hasSize(2)

        // Act: Soft-delete den ene arbeidsgiveren via TestDatabase
        val slettesId = alleFør.first { it.orgnr.asString == "222222222" }.arbeidsgiverTreffId.somUuid
        val result = db.markerArbeidsgiverSlettet(slettesId, treffId, "testperson")
        assertThat(result).isTrue()

        // Assert: hentArbeidsgivere returnerer kun ikke-slettet arbeidsgiver
        val etter = repository.hentArbeidsgivere(treffId)
        assertThat(etter).hasSize(1)
        assertThat(etter.first().orgnr.asString).isEqualTo("111111111")

        // Og hendelser inneholder SLETTET for den slettede
        val hendelser = repository.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser.any { it.hendelsestype == ArbeidsgiverHendelsestype.SLETTET }).isTrue()
    }

    @Test
    fun `Hent antall arbeidsgivere`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        val ag1 = LeggTilArbeidsgiver(Orgnr("111111111"), Orgnavn("Bedrift En"), emptyList(), "Fyrstikkalleen 1", "0661", "Oslo")
        val ag2 = LeggTilArbeidsgiver(Orgnr("222222222"), Orgnavn("Bedrift To"), emptyList(), "Fyrstikkalleen 1", "0661", "Oslo")
        db.leggTilArbeidsgiverMedHendelse(ag1, treffId, "testperson")
        db.leggTilArbeidsgiverMedHendelse(ag2, treffId, "testperson")

        val antallArbeidsgivere = repository.hentAntallArbeidsgivere(treffId)

        assertThat(antallArbeidsgivere == 2)
    }
}
