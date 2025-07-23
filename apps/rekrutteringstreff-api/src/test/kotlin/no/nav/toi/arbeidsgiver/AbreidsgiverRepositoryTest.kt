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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

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
            Orgnavn("Example Company")
        )
        repository.leggTil(input, treffId, "testperson")
        val arbeidsgivere = repository.hentArbeidsgivere(treffId)
        assertThat(arbeidsgivere).hasSize(1)
        val ag = arbeidsgivere.first()
        assertThat(ag.orgnr.asString).isEqualTo("123456789")
        assertThat(ag.orgnavn.asString).isEqualTo("Example Company")
        assertThat(ag.hendelser).hasSize(1)
        val h = ag.hendelser.first()
        assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETT)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktøridentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun hentArbeidsgivereTest() {
        val treffId1 = db.opprettRekrutteringstreffIDatabase()
        val treffId2 = db.opprettRekrutteringstreffIDatabase()
        val ag1 = Arbeidsgiver(treffId1, Orgnr("111111111"), Orgnavn("Company A"))
        val ag2 = Arbeidsgiver(treffId2, Orgnr("222222222"), Orgnavn("Company B"))
        val ag3 = Arbeidsgiver(treffId2, Orgnr("333333333"), Orgnavn("Company C"))
        db.leggTilArbeidsgivere(listOf(ag1))
        db.leggTilArbeidsgivere(listOf(ag2, ag3))
        val hentet = repository.hentArbeidsgivere(treffId2)
        assertThat(hentet).hasSize(2)
    }

    @Test
    fun hentArbeidsgiverHendelserTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreffHendelser")
        val input = LeggTilArbeidsgiver(
            Orgnr("444444444"),
            Orgnavn("Company D")
        )
        repository.leggTil(input, treffId, "testperson")
        val hendelser = repository.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser).hasSize(1)
        val h = hendelser.first()
        assertThat(h.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETT)
        assertThat(h.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(h.aktøridentifikasjon).isEqualTo("testperson")
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.orgnr.asString).isEqualTo("444444444")
        assertThat(h.orgnavn.asString).isEqualTo("Company D")
    }
}
