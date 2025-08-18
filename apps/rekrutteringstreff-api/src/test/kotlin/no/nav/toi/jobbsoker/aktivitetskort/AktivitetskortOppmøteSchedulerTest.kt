package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.TestRapid
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.Kandidatnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.Navkontor
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortOppmøteSchedulerTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var aktivitetskortRepository: AktivitetskortRepository
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        aktivitetskortRepository = AktivitetskortRepository(db.dataSource)
        rekrutteringstreffRepository =
            RekrutteringstreffRepository(db.dataSource)
    }


    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @Test
    fun `skal sende oppmøte på rapid og markere dem som pollet`() {

        val expectedFnr = Fødselsnummer("12345678901")
        val (rapid, personTreffId, treffId) = opprettPersonOgInviter(fødselsnummer = expectedFnr)
        jobbsøkerRepository.registrerOppmøte(
            listOf(personTreffId),
            treffId,
            expectedFnr.asString
        )
        AktivitetskortOppmøteScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid).behandleSvar()
        Assertions.assertThat(rapid.inspektør.size).isEqualTo(2)
        val melding = rapid.inspektør.message(1)
        Assertions.assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppmøte")
        Assertions.assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        Assertions.assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        Assertions.assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        Assertions.assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
        Assertions.assertThat(melding["oppmøte"].asBoolean()).isTrue

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVAR_JA_TIL_INVITASJON)
        Assertions.assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal sende ikke oppmøte på rapid og markere dem som pollet`() {
        val expectedFnr = Fødselsnummer("12345678901")
        val (rapid, personTreffId, treffId) = opprettPersonOgInviter(fødselsnummer = expectedFnr)
        jobbsøkerRepository.registrerIkkeOppmøte(
            listOf(personTreffId),
            treffId,
            expectedFnr.asString
        )
        AktivitetskortOppmøteScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid).behandleSvar()
        Assertions.assertThat(rapid.inspektør.size).isEqualTo(2)
        val melding = rapid.inspektør.message(1)
        Assertions.assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffoppmøte")
        Assertions.assertThat(melding["fnr"].asText()).isEqualTo(expectedFnr.asString)
        Assertions.assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.somString)
        Assertions.assertThat(melding["endretAv"].asText()).isEqualTo(expectedFnr.asString)
        Assertions.assertThat(melding["endretAvPersonbruker"].asBoolean()).isFalse
        Assertions.assertThat(melding["oppmøte"].asBoolean()).isFalse

        val usendteEtterpå = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVAR_NEI_TIL_INVITASJON)
        Assertions.assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende oppmøte-svar to ganger`() {
        val fødselsnummer = Fødselsnummer("12345678901")
        val (rapid, personTreffid, treffid) = opprettPersonOgInviter(fødselsnummer)
        jobbsøkerRepository.registrerOppmøte(listOf(personTreffid), treffid, fødselsnummer.asString)
        Assertions.assertThat(rapid.inspektør.size).isEqualTo(1)
        AktivitetskortOppmøteScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid).behandleSvar()
        AktivitetskortOppmøteScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid).behandleSvar()
        Assertions.assertThat(rapid.inspektør.size).isEqualTo(2)
    }

    private fun opprettPersonOgInviter(fødselsnummer: Fødselsnummer): Triple<TestRapid, PersonTreffId, TreffId> {
        val rapid = TestRapid()
        val invitasjonScheduler =
            AktivitetskortInvitasjonScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid)
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
        return Triple(rapid, personTreffId, treffId)
    }

    @Test
    fun `skal ikke gjøre noe hvis det ikke er noen usendte invitasjoner`() {
        val rapid = TestRapid()
        val scheduler = AktivitetskortSvarScheduler(aktivitetskortRepository, rekrutteringstreffRepository, rapid)

        scheduler.behandleSvar()

        Assertions.assertThat(rapid.inspektør.size).isEqualTo(0)
    }
}