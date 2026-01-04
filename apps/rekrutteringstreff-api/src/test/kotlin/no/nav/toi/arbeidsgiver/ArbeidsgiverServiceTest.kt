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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArbeidsgiverServiceTest {

    companion object {
        private val db = TestDatabase()
        private val mapper = JacksonConfig.mapper
        private lateinit var arbeidsgiverRepository: ArbeidsgiverRepository
        private lateinit var arbeidsgiverService: ArbeidsgiverService

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
            arbeidsgiverService = ArbeidsgiverService(db.dataSource, arbeidsgiverRepository)
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
    fun `leggTilArbeidsgiver skal opprette arbeidsgiver med hendelse i samme transaksjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiver = LeggTilArbeidsgiver(
            orgnr = Orgnr("123456789"),
            orgnavn = Orgnavn("Test AS"),
            næringskoder = listOf(Næringskode("47.111", "Detaljhandel")),
            gateadresse = "Testveien 1",
            postnummer = "0123",
            poststed = "Oslo"
        )

        arbeidsgiverService.leggTilArbeidsgiver(arbeidsgiver, treffId, "testperson")

        val hentedeArbeidsgivere = arbeidsgiverService.hentArbeidsgivere(treffId)
        assertThat(hentedeArbeidsgivere).hasSize(1)

        val hentet = hentedeArbeidsgivere.first()
        assertThat(hentet.orgnr.asString).isEqualTo("123456789")
        assertThat(hentet.orgnavn.asString).isEqualTo("Test AS")
        assertThat(hentet.status).isEqualTo(ArbeidsgiverStatus.AKTIV)

        val hendelser = arbeidsgiverService.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser).hasSize(1)
        val hendelse = hendelser.first()
        assertThat(hendelse.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETTET)
        assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
        assertThat(hendelse.aktøridentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun `slettArbeidsgiver skal legge til hendelse og endre status i samme transaksjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiver = LeggTilArbeidsgiver(
            orgnr = Orgnr("123456789"),
            orgnavn = Orgnavn("Test AS"),
            næringskoder = emptyList(),
            gateadresse = null,
            postnummer = null,
            poststed = null
        )
        arbeidsgiverService.leggTilArbeidsgiver(arbeidsgiver, treffId, "testperson")
        val arbeidsgiverId = arbeidsgiverService.hentArbeidsgivere(treffId).first().arbeidsgiverTreffId.somUuid

        val resultat = arbeidsgiverService.slettArbeidsgiver(arbeidsgiverId, treffId, "testperson")

        assertThat(resultat).isTrue()

        // Verifiser at arbeidsgiver ikke lenger returneres (har status SLETTET)
        val arbeidsgivere = arbeidsgiverService.hentArbeidsgivere(treffId)
        assertThat(arbeidsgivere).isEmpty()

        // Verifiser at begge hendelser er registrert (OPPRETTET og SLETTET)
        val hendelser = arbeidsgiverService.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser).hasSize(2)
        assertThat(hendelser.any { it.hendelsestype == ArbeidsgiverHendelsestype.OPPRETTET }).isTrue()
        assertThat(hendelser.any { it.hendelsestype == ArbeidsgiverHendelsestype.SLETTET }).isTrue()
    }

    @Test
    fun `slettArbeidsgiver skal returnere false når arbeidsgiver ikke finnes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val ikkeEksisterendeId = UUID.randomUUID()

        val resultat = arbeidsgiverService.slettArbeidsgiver(ikkeEksisterendeId, treffId, "testperson")

        assertThat(resultat).isFalse()
    }

    @Test
    fun `hentArbeidsgiver skal returnere null når arbeidsgiver ikke finnes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")

        val arbeidsgiver = arbeidsgiverService.hentArbeidsgiver(treffId, Orgnr("999999999"))

        assertThat(arbeidsgiver).isNull()
    }

    @Test
    fun `hentArbeidsgiver skal returnere arbeidsgiver når den finnes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val orgnr = Orgnr("123456789")
        val arbeidsgiver = LeggTilArbeidsgiver(
            orgnr = orgnr,
            orgnavn = Orgnavn("Test AS"),
            næringskoder = emptyList(),
            gateadresse = null,
            postnummer = null,
            poststed = null
        )
        arbeidsgiverService.leggTilArbeidsgiver(arbeidsgiver, treffId, "testperson")

        val hentet = arbeidsgiverService.hentArbeidsgiver(treffId, orgnr)

        assertThat(hentet).isNotNull
        assertThat(hentet!!.orgnr.asString).isEqualTo("123456789")
    }

    @Test
    fun `hentArbeidsgiverHendelser skal returnere hendelser for alle arbeidsgivere på treff`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiver1 = LeggTilArbeidsgiver(Orgnr("111111111"), Orgnavn("Firma 1"), emptyList(), null, null, null)
        val arbeidsgiver2 = LeggTilArbeidsgiver(Orgnr("222222222"), Orgnavn("Firma 2"), emptyList(), null, null, null)

        arbeidsgiverService.leggTilArbeidsgiver(arbeidsgiver1, treffId, "testperson")
        arbeidsgiverService.leggTilArbeidsgiver(arbeidsgiver2, treffId, "testperson")

        val hendelser = arbeidsgiverService.hentArbeidsgiverHendelser(treffId)

        assertThat(hendelser).hasSize(2)
        hendelser.forEach { hendelse ->
            assertThat(hendelse.hendelsestype).isEqualTo(ArbeidsgiverHendelsestype.OPPRETTET)
        }
    }
}

