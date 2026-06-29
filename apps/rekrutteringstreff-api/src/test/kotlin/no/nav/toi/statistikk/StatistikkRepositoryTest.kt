package no.nav.toi.statistikk

import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.executeInTransaction
import no.nav.toi.formidling.FormidlingRepository
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatistikkRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: StatistikkRepository
        private lateinit var formidlingRepository: FormidlingRepository
        private val OSLO = ZoneId.of("Europe/Oslo")

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
            repository = StatistikkRepository(db.dataSource)
            formidlingRepository = FormidlingRepository(db.dataSource)
        }
    }

    @AfterEach
    fun slettAlt() {
        db.slettAlt()
    }

    @Test
    fun `teller formidlinger for kontor i periode fordelt på alder og innsatsgruppe`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)

        val ungIkkeStandard = jobbsøker(treffId, "11111111101", alder = 25, innsatsgruppe = "BATT", kontornummer = "1000")
        val eldreStandard = jobbsøker(treffId, "11111111102", alder = 50, innsatsgruppe = "STANDARD_INNSATS", kontornummer = "1000")
        val annetKontor = jobbsøker(treffId, "11111111103", alder = 22, innsatsgruppe = "BATT", kontornummer = "2000")
        db.leggTilJobbsøkere(listOf(ungIkkeStandard, eldreStandard, annetKontor))

        val iPerioden = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, OSLO)
        opprettSendtFormidling(treffId, ungIkkeStandard.personTreffId, arbeidsgiverTreffId, iPerioden)
        opprettSendtFormidling(treffId, eldreStandard.personTreffId, arbeidsgiverTreffId, iPerioden)
        opprettSendtFormidling(treffId, annetKontor.personTreffId, arbeidsgiverTreffId, iPerioden)

        val statistikk = repository.hentFåttJobbStatistikk("1000", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(statistikk.totalt).isEqualTo(2)
        assertThat(statistikk.under30år).isEqualTo(1)
        assertThat(statistikk.innsatsgruppeIkkeStandard).isEqualTo(1)
    }

    @Test
    fun `teller ikke formidlinger utenfor perioden`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)

        val før = jobbsøker(treffId, "11111111104", alder = 25, innsatsgruppe = "BATT", kontornummer = "1000")
        val etter = jobbsøker(treffId, "11111111105", alder = 25, innsatsgruppe = "BATT", kontornummer = "1000")
        val iPerioden = jobbsøker(treffId, "11111111106", alder = 25, innsatsgruppe = "BATT", kontornummer = "1000")
        db.leggTilJobbsøkere(listOf(før, etter, iPerioden))

        opprettSendtFormidling(treffId, før.personTreffId, arbeidsgiverTreffId, ZonedDateTime.of(2026, 5, 31, 23, 0, 0, 0, OSLO))
        opprettSendtFormidling(treffId, etter.personTreffId, arbeidsgiverTreffId, ZonedDateTime.of(2026, 7, 1, 1, 0, 0, 0, OSLO))
        opprettSendtFormidling(treffId, iPerioden.personTreffId, arbeidsgiverTreffId, ZonedDateTime.of(2026, 6, 30, 23, 0, 0, 0, OSLO))

        val statistikk = repository.hentFåttJobbStatistikk("1000", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(statistikk.totalt).isEqualTo(1)
    }

    @Test
    fun `teller ikke slettede formidlinger eller formidlinger uten sendt utfall`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val arbeidsgiverTreffId = leggTilArbeidsgiver(treffId)

        val slettet = jobbsøker(treffId, "11111111107", alder = 25, innsatsgruppe = "BATT", kontornummer = "1000")
        val utenUtfall = jobbsøker(treffId, "11111111108", alder = 25, innsatsgruppe = "BATT", kontornummer = "1000")
        val gyldig = jobbsøker(treffId, "11111111109", alder = 25, innsatsgruppe = "BATT", kontornummer = "1000")
        db.leggTilJobbsøkere(listOf(slettet, utenUtfall, gyldig))

        val iPerioden = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, OSLO)
        val slettetId = opprettSendtFormidling(treffId, slettet.personTreffId, arbeidsgiverTreffId, iPerioden)
        val erMarkertSlettet = db.dataSource.connection.use { formidlingRepository.markerSlettet(it, slettetId) }
        assertThat(erMarkertSlettet).isTrue
        opprettFormidling(treffId, utenUtfall.personTreffId, arbeidsgiverTreffId, utfallSendtTidspunkt = null)
        opprettSendtFormidling(treffId, gyldig.personTreffId, arbeidsgiverTreffId, iPerioden)

        val statistikk = repository.hentFåttJobbStatistikk("1000", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(statistikk.totalt).isEqualTo(1)
    }

    private fun leggTilArbeidsgiver(treffId: TreffId) =
        db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("123456789"), Orgnavn("Test AS"), emptyList(), null, null, null),
            treffId, "testperson"
        )

    private fun jobbsøker(treffId: TreffId, fødselsnummer: String, alder: Int, innsatsgruppe: String, kontornummer: String) =
        Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer(fødselsnummer),
            fornavn = Fornavn("Test"),
            etternavn = Etternavn("Testesen"),
            kontor = Kontor(kontornummer = kontornummer, kontornavn = "Nav Test"),
            veilederNavn = null,
            veilederNavIdent = null,
            status = JobbsøkerStatus.FÅTT_JOBB,
            alder = alder,
            innsatsgruppe = Innsatsgruppe(innsatsgruppe),
        )

    private fun opprettSendtFormidling(
        treffId: TreffId,
        personTreffId: PersonTreffId,
        arbeidsgiverTreffId: no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId,
        utfallSendtTidspunkt: ZonedDateTime,
    ) = opprettFormidling(treffId, personTreffId, arbeidsgiverTreffId, utfallSendtTidspunkt)

    private fun opprettFormidling(
        treffId: TreffId,
        personTreffId: PersonTreffId,
        arbeidsgiverTreffId: no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId,
        utfallSendtTidspunkt: ZonedDateTime?,
    ): Long = db.dataSource.executeInTransaction { connection ->
        formidlingRepository.opprett(
            connection,
            treffId,
            personTreffId,
            arbeidsgiverTreffId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            utfallSendtTidspunkt = utfallSendtTidspunkt,
        )
    }
}
