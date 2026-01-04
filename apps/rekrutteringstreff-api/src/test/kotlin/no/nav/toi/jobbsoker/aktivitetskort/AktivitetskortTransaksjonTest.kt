package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortTransaksjonTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var aktivitetskortRepository: AktivitetskortRepository
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var rekrutteringstreffService: RekrutteringstreffService
        private lateinit var arbeidsgiverRepository: ArbeidsgiverRepository
        private lateinit var jobbsøkerService: JobbsøkerService
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        aktivitetskortRepository = AktivitetskortRepository(db.dataSource)
        rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
        arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
        jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
        rekrutteringstreffService = RekrutteringstreffService(db.dataSource, rekrutteringstreffRepository, jobbsøkerRepository, arbeidsgiverRepository, jobbsøkerService)
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @Test
    fun `skal rulle tilbake databaseendringer dersom kafka feiler ved invitasjon`() {
        val failingRapid = FailingRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, failingRapid, mapper)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        db.leggTilJobbsøkereMedService(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerService.inviter(listOf(personTreffId), treffId, "Z123456")

        scheduler.behandleJobbsøkerHendelser()

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        assertThat(usendteEtterpå).isNotEmpty()
        assertThat(usendteEtterpå).hasSize(1)
    }

    @Test
    fun `skal rulle tilbake databaseendringer dersom kafka feiler ved treff endret`() {
        val failingRapid = FailingRapid()
        val scheduler = AktivitetskortJobbsøkerScheduler(db.dataSource, aktivitetskortRepository, rekrutteringstreffRepository, failingRapid, mapper)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        db.leggTilJobbsøkereMedService(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerService.inviter(listOf(personTreffId), treffId, "Z123456")

        // Mark invitation as handled so we don't fail on that
        val invitasjoner = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.INVITERT)
        invitasjoner.forEach { aktivitetskortRepository.lagrePollingstatus(it.jobbsokerHendelseDbId) }

        val endringer = no.nav.toi.rekrutteringstreff.Rekrutteringstreffendringer(
            navn = no.nav.toi.rekrutteringstreff.Endringsfelt(gammelVerdi = "Gammel", nyVerdi = "Ny")
        )
        db.registrerTreffEndretNotifikasjon(treffId, fødselsnummer, endringer)

        scheduler.behandleJobbsøkerHendelser()

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
        assertThat(usendteEtterpå).isNotEmpty()
        assertThat(usendteEtterpå).hasSize(1)
    }

    class FailingRapid : RapidsConnection() {
        override fun publish(message: String) {
            throw RuntimeException("Kafka failure")
        }
        override fun publish(key: String, message: String) {
            throw RuntimeException("Kafka failure")
        }
        override fun rapidName(): String = "FailingRapid"
        override fun start() {}
        override fun stop() {}
    }
}
