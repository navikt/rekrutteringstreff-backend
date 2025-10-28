package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.TestRapid
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortSvartJaTreffstatusSchedulerTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var aktivitetskortRepository: AktivitetskortRepository
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var rekrutteringstreffService: RekrutteringstreffService
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        aktivitetskortRepository = AktivitetskortRepository(db.dataSource)
        rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
        rekrutteringstreffService = RekrutteringstreffService(db.dataSource, rekrutteringstreffRepository, jobbsøkerRepository)
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @Test
    fun `skal sende avlyst-status paa rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val (rapid, treffId) = opprettPersonOgInviter(fødselsnummer = expectedFnr)

        // Personen svarer ja slik at hendelsen blir opprettet ved avlysning
        jobbsøkerRepository.svarJaTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)

        // Avlys rekrutteringstreffet som lager jobbsøker-hendelse SVART_JA_TREFF_AVLYST
        rekrutteringstreffService.avlys(treffId, expectedFnr.asString)

        AktivitetskortSvartJaTreffstatusScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid)
            .behandleStatusendringer()

        // 1 melding fra invitasjon + 1 fra treffstatus-endring
        Assertions.assertThat(rapid.inspektør.size).isEqualTo(2)
        val melding = rapid.inspektør.message(1)
        Assertions.assertThat(melding["@event_name"].asText()).isEqualTo("svartJaTreffstatusEndret")
        Assertions.assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        Assertions.assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        Assertions.assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        Assertions.assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
        Assertions.assertThat(melding["status"].asText()).isEqualTo("avlyst")

        val usendteEtterpaa = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
        Assertions.assertThat(usendteEtterpaa).isEmpty()
    }

    @Test
    fun `skal sende fullfort-status paa rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val (rapid, treffId) = opprettPersonOgInviter(fødselsnummer = expectedFnr)

        // Personen svarer ja slik at hendelsen blir opprettet ved fullføring
        jobbsøkerRepository.svarJaTilInvitasjon(expectedFnr, treffId, expectedFnr.asString)

        // Fullfør rekrutteringstreffet som lager jobbsøker-hendelse SVART_JA_TREFF_FULLFØRT
        rekrutteringstreffService.fullfor(treffId, expectedFnr.asString)

        AktivitetskortSvartJaTreffstatusScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid)
            .behandleStatusendringer()

        // 1 melding fra invitasjon + 1 fra treffstatus-endring
        Assertions.assertThat(rapid.inspektør.size).isEqualTo(2)
        val melding = rapid.inspektør.message(1)
        Assertions.assertThat(melding["@event_name"].asText()).isEqualTo("svartJaTreffstatusEndret")
        Assertions.assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        Assertions.assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        Assertions.assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        Assertions.assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
        Assertions.assertThat(melding["status"].asText()).isEqualTo("fullført")

        val usendteEtterpaa = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)
        Assertions.assertThat(usendteEtterpaa).isEmpty()
    }

    @Test
    fun `skal ikke sende samme treffstatus to ganger`() {
        val fnr = Fødselsnummer("12345678901")
        val (rapid, treffId) = opprettPersonOgInviter(fnr)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr, treffId, fnr.asString)
        rekrutteringstreffService.avlys(treffId, fnr.asString)

        // Førstegangskjøring
        AktivitetskortSvartJaTreffstatusScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid)
            .behandleStatusendringer()
        // Andre gang skal ikke sende på nytt
        AktivitetskortSvartJaTreffstatusScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid)
            .behandleStatusendringer()

        // 1 fra invitasjon + 1 fra første status-endring
        Assertions.assertThat(rapid.inspektør.size).isEqualTo(2)
    }

    @Test
    fun `skal ikke gjoere noe hvis det ikke er noen usendte treffstatus-endringer`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortSvartJaTreffstatusScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid)

        scheduler.behandleStatusendringer()

        Assertions.assertThat(rapid.inspektør.size).isEqualTo(0)
    }

    private fun opprettPersonOgInviter(fødselsnummer: Fødselsnummer): Pair<TestRapid, TreffId> {
        val rapid = TestRapid()
        val invitasjonScheduler = AktivitetskortInvitasjonScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, "Z123456")

        invitasjonScheduler.behandleInvitasjoner()
        return Pair(rapid, treffId)
    }
}
