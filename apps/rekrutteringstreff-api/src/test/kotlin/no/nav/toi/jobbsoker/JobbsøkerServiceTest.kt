package no.nav.toi.jobbsoker

import no.nav.toi.AktørType
import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
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
class JobbsøkerServiceTest {

    companion object {
        private val db = TestDatabase()
        private val mapper = JacksonConfig.mapper
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var jobbsøkerService: JobbsøkerService

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
            jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
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
    fun `leggTilJobbsøkere skal opprette jobbsøkere med hendelse i samme transaksjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(
                Fødselsnummer("12345678901"),
                Fornavn("Ola"),
                Etternavn("Nordmann"),
                Navkontor("NAV Oslo"),
                VeilederNavn("Kari Veileder"),
                VeilederNavIdent("V123")
            ),
            LeggTilJobbsøker(
                Fødselsnummer("10987654321"),
                Fornavn("Kari"),
                Etternavn("Nordmann"),
                null,
                null,
                null
            )
        )

        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")

        val hentedeJobbsøkere = jobbsøkerService.hentJobbsøkere(treffId)
        assertThat(hentedeJobbsøkere).hasSize(2)

        hentedeJobbsøkere.forEach { jobbsøker ->
            assertThat(jobbsøker.hendelser).hasSize(1)
            val hendelse = jobbsøker.hendelser.first()
            assertThat(hendelse.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
            assertThat(hendelse.opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
            assertThat(hendelse.aktørIdentifikasjon).isEqualTo("testperson")
        }
    }

    @Test
    fun `inviter skal opprette hendelser og oppdatere status i samme transaksjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(
                Fødselsnummer("12345678901"),
                Fornavn("Ola"),
                Etternavn("Nordmann"),
                null,
                null,
                null
            )
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val opprettedeJobbsøkere = jobbsøkerService.hentJobbsøkere(treffId)
        val personTreffIds = opprettedeJobbsøkere.map { it.personTreffId }

        jobbsøkerService.inviter(personTreffIds, treffId, "testperson")

        val oppdaterteJobbsøkere = jobbsøkerService.hentJobbsøkere(treffId)
        assertThat(oppdaterteJobbsøkere).hasSize(1)
        val jobbsøker = oppdaterteJobbsøkere.first()

        // Verifiser at status er oppdatert
        assertThat(jobbsøker.status).isEqualTo(JobbsøkerStatus.INVITERT)

        // Verifiser at hendelse er lagt til
        assertThat(jobbsøker.hendelser).hasSize(2)
        val invitasjonHendelse = jobbsøker.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.INVITERT }
        assertThat(invitasjonHendelse).isNotNull
    }

    @Test
    fun `svarJaTilInvitasjon skal opprette hendelse og oppdatere status i samme transaksjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffIds = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }
        jobbsøkerService.inviter(personTreffIds, treffId, "testperson")

        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, fnr.asString)

        val oppdatertJobbsøker = jobbsøkerService.hentJobbsøker(treffId, fnr)
        assertThat(oppdatertJobbsøker).isNotNull
        assertThat(oppdatertJobbsøker!!.status).isEqualTo(JobbsøkerStatus.SVART_JA)

        val svarJaHendelse = oppdatertJobbsøker.hendelser.find {
            it.hendelsestype == JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON
        }
        assertThat(svarJaHendelse).isNotNull
        assertThat(svarJaHendelse!!.opprettetAvAktørType).isEqualTo(AktørType.JOBBSØKER)
    }

    @Test
    fun `svarNeiTilInvitasjon skal opprette hendelse og oppdatere status i samme transaksjon`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffIds = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }
        jobbsøkerService.inviter(personTreffIds, treffId, "testperson")

        jobbsøkerService.svarNeiTilInvitasjon(fnr, treffId, fnr.asString)

        val oppdatertJobbsøker = jobbsøkerService.hentJobbsøker(treffId, fnr)
        assertThat(oppdatertJobbsøker).isNotNull
        assertThat(oppdatertJobbsøker!!.status).isEqualTo(JobbsøkerStatus.SVART_NEI)

        val svarNeiHendelse = oppdatertJobbsøker.hendelser.find {
            it.hendelsestype == JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON
        }
        assertThat(svarNeiHendelse).isNotNull
        assertThat(svarNeiHendelse!!.opprettetAvAktørType).isEqualTo(AktørType.JOBBSØKER)
    }

    @Test
    fun `fjernJobbsøker skal returnere OK og opprette SLETTET-hendelse når jobbsøker har status LAGT_TIL`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffId = jobbsøkerService.hentJobbsøkere(treffId).first().personTreffId

        val resultat = jobbsøkerService.fjernJobbsøker(personTreffId, treffId, "testperson")

        assertThat(resultat).isEqualTo(FjernJobbsøkerResultat.OK)

        // Verifiser at jobbsøker ikke lenger returneres (har status SLETTET)
        val jobbsøkere2 = jobbsøkerService.hentJobbsøkere(treffId)
        assertThat(jobbsøkere2).isEmpty()

        // Verifiser at SLETTET-hendelse ble opprettet
        val hendelser = jobbsøkerService.hentJobbsøkerHendelser(treffId)
        val slettetHendelse = hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.SLETTET }
        assertThat(slettetHendelse).isNotNull
        assertThat(slettetHendelse!!.aktørIdentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun `fjernJobbsøker skal returnere IKKE_TILLATT når jobbsøker har status INVITERT`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffId = jobbsøkerService.hentJobbsøkere(treffId).first().personTreffId
        jobbsøkerService.inviter(listOf(personTreffId), treffId, "testperson")

        val resultat = jobbsøkerService.fjernJobbsøker(personTreffId, treffId, "testperson")

        assertThat(resultat).isEqualTo(FjernJobbsøkerResultat.IKKE_TILLATT)
    }

    @Test
    fun `fjernJobbsøker skal returnere IKKE_FUNNET når jobbsøker ikke finnes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val ikkeEksisterendeId = PersonTreffId(UUID.randomUUID())

        val resultat = jobbsøkerService.fjernJobbsøker(ikkeEksisterendeId, treffId, "testperson")

        assertThat(resultat).isEqualTo(FjernJobbsøkerResultat.IKKE_FUNNET)
    }

    @Test
    fun `hentJobbsøkerHendelser skal returnere hendelser for alle jobbsøkere på treff`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(Fødselsnummer("12345678901"), Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
            LeggTilJobbsøker(Fødselsnummer("10987654321"), Fornavn("Kari"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")

        val hendelser = jobbsøkerService.hentJobbsøkerHendelser(treffId)

        assertThat(hendelser).hasSize(2)
        hendelser.forEach { hendelse ->
            assertThat(hendelse.hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
        }
    }

    @Test
    fun `finnJobbsøkereMedAktivtSvarJa skal filtrere jobbsøkere som har svart ja og ikke svart nei etterpå`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr1 = Fødselsnummer("12345678901")
        val fnr2 = Fødselsnummer("10987654321")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr1, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
            LeggTilJobbsøker(fnr2, Fornavn("Kari"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffIds = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }
        jobbsøkerService.inviter(personTreffIds, treffId, "testperson")

        // Kun fnr1 svarer ja
        jobbsøkerService.svarJaTilInvitasjon(fnr1, treffId, fnr1.asString)

        val alleJobbsøkere = jobbsøkerService.hentJobbsøkere(treffId)
        val jobbsøkereMedAktivtSvarJa = jobbsøkerService.finnJobbsøkereMedAktivtSvarJa(alleJobbsøkere)

        assertThat(jobbsøkereMedAktivtSvarJa).hasSize(1)
        assertThat(jobbsøkereMedAktivtSvarJa.first().fødselsnummer).isEqualTo(fnr1)
    }

    @Test
    fun `finnJobbsøkereSomIkkeSvart skal filtrere jobbsøkere som er invitert men ikke har svart`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr1 = Fødselsnummer("12345678901")
        val fnr2 = Fødselsnummer("10987654321")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr1, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null),
            LeggTilJobbsøker(fnr2, Fornavn("Kari"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffIds = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }
        jobbsøkerService.inviter(personTreffIds, treffId, "testperson")

        // Kun fnr1 svarer
        jobbsøkerService.svarJaTilInvitasjon(fnr1, treffId, fnr1.asString)

        val alleJobbsøkere = jobbsøkerService.hentJobbsøkere(treffId)
        val jobbsøkereSomIkkeSvart = jobbsøkerService.finnJobbsøkereSomIkkeSvart(alleJobbsøkere)

        assertThat(jobbsøkereSomIkkeSvart).hasSize(1)
        assertThat(jobbsøkereSomIkkeSvart.first().fødselsnummer).isEqualTo(fnr2)
    }

    @Test
    fun `skalVarslesOmEndringer skal returnere true for jobbsøker med INVITERT som siste relevante hendelse`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffIds = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }
        jobbsøkerService.inviter(personTreffIds, treffId, "testperson")

        val jobbsøker = jobbsøkerService.hentJobbsøker(treffId, fnr)
        val skalVarsles = jobbsøkerService.skalVarslesOmEndringer(jobbsøker!!.hendelser)

        assertThat(skalVarsles).isTrue()
    }

    @Test
    fun `skalVarslesOmEndringer skal returnere true for jobbsøker som har svart ja`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffIds = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }
        jobbsøkerService.inviter(personTreffIds, treffId, "testperson")
        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, fnr.asString)

        val jobbsøker = jobbsøkerService.hentJobbsøker(treffId, fnr)
        val skalVarsles = jobbsøkerService.skalVarslesOmEndringer(jobbsøker!!.hendelser)

        assertThat(skalVarsles).isTrue()
    }

    @Test
    fun `skalVarslesOmEndringer skal returnere false for jobbsøker som har svart nei`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")
        val personTreffIds = jobbsøkerService.hentJobbsøkere(treffId).map { it.personTreffId }
        jobbsøkerService.inviter(personTreffIds, treffId, "testperson")
        Thread.sleep(10) // Sikre at tidspunktet for neste hendelse er forskjellig
        jobbsøkerService.svarNeiTilInvitasjon(fnr, treffId, fnr.asString)

        val jobbsøker = jobbsøkerService.hentJobbsøker(treffId, fnr)

        assertThat(jobbsøker!!.status).isEqualTo(JobbsøkerStatus.SVART_NEI)
        assertThat(jobbsøker.hendelser.any { it.hendelsestype == JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON }).isTrue()

        val skalVarsles = jobbsøkerService.skalVarslesOmEndringer(jobbsøker.hendelser)
        assertThat(skalVarsles).isFalse()
    }

    @Test
    fun `skalVarslesOmEndringer skal returnere false for jobbsøker som kun er lagt til men ikke invitert`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val fnr = Fødselsnummer("12345678901")
        val jobbsøkere = listOf(
            LeggTilJobbsøker(fnr, Fornavn("Ola"), Etternavn("Nordmann"), null, null, null)
        )
        jobbsøkerService.leggTilJobbsøkere(jobbsøkere, treffId, "testperson")

        val jobbsøker = jobbsøkerService.hentJobbsøker(treffId, fnr)
        val skalVarsles = jobbsøkerService.skalVarslesOmEndringer(jobbsøker!!.hendelser)

        assertThat(skalVarsles).isFalse()
    }
}

