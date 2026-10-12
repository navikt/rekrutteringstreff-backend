package no.nav.toi.arbeidsgiver

import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.JacksonConfig
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.TreffkontekstRepository
import no.nav.toi.treffgjennomføring.RegistreringerRepository
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import no.nav.toi.treffgjennomføring.møteplan.Møteplansynk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.*
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArbeidsgiverServiceTest {

    companion object {
        private val db = TestDatabase()
        private val mapper = JacksonConfig.mapper
        private const val FIKTIVT_ORGNR = "000000001"
        private const val EKSTRA_ORGNR = "000000002"
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
            arbeidsgiverService = lagArbeidsgiverService(db.dataSource, arbeidsgiverRepository)
        }

        private fun lagArbeidsgiverService(dataSource: DataSource, repository: ArbeidsgiverRepository) =
            ArbeidsgiverService(
                dataSource, repository, mapper, TreffkontekstRepository(), MøteplanRepository(),
                Møteplansynk(TreffkontekstRepository(), MøteplanRepository(), OppmøteRepository()),
                RegistreringerRepository(),
            )
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
        leggTilEkstraArbeidsgiver(treffId)
        val arbeidsgiverId = arbeidsgiverService.hentArbeidsgiver(treffId, Orgnr("123456789"))!!.arbeidsgiverTreffId.somUuid

        val resultat = arbeidsgiverService.markerArbeidsgiverSlettet(arbeidsgiverId, treffId, "testperson")

        assertThat(resultat).isTrue()

        // Verifiser at arbeidsgiver ikke lenger returneres (har status SLETTET)
        val arbeidsgivere = arbeidsgiverService.hentArbeidsgivere(treffId)
        assertThat(arbeidsgivere.map { it.orgnr.asString }).containsExactly(EKSTRA_ORGNR)

        // Verifiser at hendelsene er registrert (OPPRETTET for begge og SLETTET)
        val hendelser = arbeidsgiverService.hentArbeidsgiverHendelser(treffId)
        assertThat(hendelser).hasSize(3)
        assertThat(hendelser.any { it.hendelsestype == ArbeidsgiverHendelsestype.OPPRETTET }).isTrue()
        assertThat(hendelser.any { it.hendelsestype == ArbeidsgiverHendelsestype.SLETTET }).isTrue()
    }

    @Test
    fun `siste arbeidsgiver kan ikke slettes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        leggTilEkstraArbeidsgiver(treffId)
        val arbeidsgiverId = arbeidsgiverService.hentArbeidsgivere(treffId).single().arbeidsgiverTreffId.somUuid

        assertThatThrownBy { arbeidsgiverService.markerArbeidsgiverSlettet(arbeidsgiverId, treffId, "testperson") }
            .isInstanceOf(SisteArbeidsgiverKanIkkeSlettesException::class.java)

        assertThat(arbeidsgiverService.hentArbeidsgivere(treffId)).hasSize(1)
        assertThat(arbeidsgiverService.hentArbeidsgiverHendelser(treffId).map { it.hendelsestype })
            .containsExactly(ArbeidsgiverHendelsestype.OPPRETTET)
    }

    @Test
    fun `slettArbeidsgiver skal returnere false når arbeidsgiver ikke finnes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val ikkeEksisterendeId = UUID.randomUUID()

        val resultat = arbeidsgiverService.markerArbeidsgiverSlettet(ikkeEksisterendeId, treffId, "testperson")

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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `tillegg uten møteplan bruker bare en eksistenssjekk etter trefflåsen`(medBehov: Boolean) {
        val treff = db.opprettRekrutteringstreffIDatabase(navIdent = "TESTPERSON", tittel = "Fiktivt treff")

        val sql = sporSql { service -> leggTilFiktivArbeidsgiver(service, treff, medBehov) }

        assertThat(sql[0]).endsWith("FOR NO KEY UPDATE")
        assertThat(sql[1]).startsWith("SELECT EXISTS").contains("moteoppsett", "jobbsoker_romtildeling")
        // Med behov slås også en eventuell slettet arbeidsgiver opp for reaktivering.
        assertThat(sql.filter { it.startsWith("SELECT") }).hasSize(if (medBehov) 3 else 2)
        assertThat(sql.filter { it.startsWith("INSERT") }).noneMatch {
            it.contains("romtildeling") || it.contains("arbeidsgiver_rotasjon")
        }
        assertThat(arbeidsgiverService.hentArbeidsgivere(treff)).hasSize(1)
    }

    @Test
    fun `reaktivering uten møteplan bruker samme korte lesevei`() {
        val treff = db.opprettRekrutteringstreffIDatabase(navIdent = "TESTPERSON", tittel = "Fiktivt treff")
        leggTilFiktivArbeidsgiver(arbeidsgiverService, treff, medBehov = true)
        leggTilEkstraArbeidsgiver(treff)
        val arbeidsgiver = arbeidsgiverService.hentArbeidsgiver(treff, Orgnr(FIKTIVT_ORGNR))!!.arbeidsgiverTreffId
        arbeidsgiverService.markerArbeidsgiverSlettet(arbeidsgiver.somUuid, treff, "TESTPERSON")

        val sql = sporSql { service -> leggTilFiktivArbeidsgiver(service, treff, medBehov = true) }

        assertThat(sql.filter { it.startsWith("SELECT") }).hasSize(3)
        assertThat(arbeidsgiverService.hentArbeidsgiver(treff, Orgnr(FIKTIVT_ORGNR))!!.arbeidsgiverTreffId).isEqualTo(arbeidsgiver)
    }

    @Test
    fun `sletting uten møteplan hopper over møteplanlesing men beholder registreringssjekkene`() {
        val treff = db.opprettRekrutteringstreffIDatabase(navIdent = "TESTPERSON", tittel = "Fiktivt treff")
        leggTilFiktivArbeidsgiver(arbeidsgiverService, treff, medBehov = false)
        leggTilEkstraArbeidsgiver(treff)
        val arbeidsgiver = arbeidsgiverService.hentArbeidsgiver(treff, Orgnr(FIKTIVT_ORGNR))!!.arbeidsgiverTreffId

        val sql = sporSql { service ->
            assertThat(service.markerArbeidsgiverSlettet(arbeidsgiver.somUuid, treff, "TESTPERSON")).isTrue()
        }

        assertThat(sql.filter { it.startsWith("SELECT") }).hasSize(8)
        assertThat(sql.joinToString()).contains(
            "SELECT COUNT(*) FROM interesse", "SELECT COUNT(*) FROM intervjufordeling", "SELECT COUNT(*) FROM vurdering",
        )
        assertThat(sql).noneMatch { it.contains("deltakernummer") }
        assertThat(arbeidsgiverService.hentArbeidsgivere(treff).map { it.orgnr.asString }).containsExactly(EKSTRA_ORGNR)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `manglende treff avvises av låsen før tillegg forsøkes`(medBehov: Boolean) {
        val treff = TreffId(UUID.randomUUID())
        val sql = sporSql { service ->
            assertThatThrownBy { leggTilFiktivArbeidsgiver(service, treff, medBehov) }
                .isInstanceOf(RekrutteringstreffIkkeFunnetException::class.java)
        }

        assertThat(sql).hasSize(1)
        assertThat(sql.single()).endsWith("FOR NO KEY UPDATE")
    }

    private fun leggTilFiktivArbeidsgiver(service: ArbeidsgiverService, treff: TreffId, medBehov: Boolean) {
        val arbeidsgiver = LeggTilArbeidsgiver(
            Orgnr(FIKTIVT_ORGNR), Orgnavn("Fiktiv testbedrift"), emptyList(), null, null, null,
        )
        if (medBehov) {
            val behov = ArbeidsgiversBehov(
                listOf(BehovTag("Fiktivt testyrke", "YRKESTITTEL", 1)),
                listOf("Norsk"), 1, listOf(Ansettelsesform.FAST),
            )
            service.leggTilArbeidsgiverMedBehov(arbeidsgiver, behov, treff, "TESTPERSON")
        } else {
            service.leggTilArbeidsgiver(arbeidsgiver, treff, "TESTPERSON")
        }
    }

    private fun leggTilEkstraArbeidsgiver(treff: TreffId) {
        arbeidsgiverService.leggTilArbeidsgiver(
            LeggTilArbeidsgiver(Orgnr(EKSTRA_ORGNR), Orgnavn("Fiktiv ekstrabedrift"), emptyList(), null, null, null),
            treff,
            "TESTPERSON",
        )
    }

    private fun sporSql(block: (ArbeidsgiverService) -> Unit): List<String> {
        val spørringer = mutableListOf<String>()
        val dataSource = object : DataSource by db.dataSource {
            override fun getConnection(): Connection {
                val connection = db.dataSource.connection
                return object : Connection by connection {
                    override fun prepareStatement(sql: String): PreparedStatement {
                        spørringer.add(sql.trimIndent())
                        return connection.prepareStatement(sql)
                    }
                }
            }
        }
        val service = lagArbeidsgiverService(dataSource, ArbeidsgiverRepository(dataSource, mapper))
        block(service)
        return spørringer
    }
}
