package no.nav.toi

import no.nav.toi.arbeidsgiver.Ansettelsesform
import no.nav.toi.arbeidsgiver.ArbeidsgiversBehov
import no.nav.toi.arbeidsgiver.BehovTag
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.exception.UlovligOppdateringException
import no.nav.toi.jobbsoker.AktuellForTreffStatus
import no.nav.toi.jobbsoker.EndreAktuellForTreffStatusResultat
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.Endringsfelttype
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.Rekrutteringstreffendringer
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.text.Normalizer

/**
 * Hver samtidighetstest holder en lås i en egen transaksjon og starter en operasjon som må vente på
 * den. Mens operasjonen venter, endrer testen det operasjonen bestemmer ut fra. Testen krever at
 * operasjonen ser endringen når den får låsen, altså at den låser før den leser.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LåsingTest {

    private val db = TestDatabase()
    private val ctx = ApplicationContext(TestInfrastructureContext(dataSource = db.dataSource))
    private val navIdent = "Z999999"

    @BeforeAll
    fun migrer() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    @Test
    fun `bare låsefila låser rader`() {
        val radlås = Regex("""\bFOR\s+(NO\s+KEY\s+UPDATE|UPDATE|KEY\s+SHARE|SHARE)\b""", RegexOption.IGNORE_CASE)

        val filerMedRadlås = File("src/main/kotlin").walk()
            .filter { it.isFile && it.extension == "kt" && radlås.containsMatchIn(it.readText()) }
            .map { Normalizer.normalize(it.invariantSeparatorsPath, Normalizer.Form.NFC) }
            .toList()

        assertThat(filerMedRadlås).containsExactly(Normalizer.normalize("src/main/kotlin/no/nav/toi/låsing.kt", Normalizer.Form.NFC))
    }

    @Test
    fun `transaksjon inne i en annen transaksjon stoppes med en gang`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()

        assertThatThrownBy {
            db.dataSource.medLåstTreff(treffId) {
                db.dataSource.executeInTransaction { }
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("inne i en annen transaksjon")

        assertThat(db.dataSource.executeInReadOnlyTransaction { true }).isTrue()
    }

    @Test
    fun `publisering venter på trefflåsen og avviser treff som ble slettet mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { runCatching { ctx.rekrutteringstreffService.publiser(treffId, navIdent) }.exceptionOrNull() },
        ) { connection ->
            ctx.rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.SLETTET)
        }

        assertThat(feil).isInstanceOf(UlovligOppdateringException::class.java)
        assertThat(ctx.rekrutteringstreffRepository.hent(treffId)!!.status).isEqualTo(RekrutteringstreffStatus.SLETTET)
        assertThat(treffhendelser(treffId)).doesNotContain(RekrutteringstreffHendelsestype.PUBLISERT)
    }

    @Test
    fun `sletting av treff venter på trefflåsen og avviser treff som fikk jobbsøker mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { runCatching { ctx.rekrutteringstreffService.markerSlettet(treffId, navIdent) }.exceptionOrNull() },
        ) { connection ->
            ctx.jobbsøkerRepository.leggTil(connection, listOf(jobbsøker(1)), treffId)
        }

        assertThat(feil).isInstanceOf(UlovligOppdateringException::class.java)
        assertThat(ctx.rekrutteringstreffRepository.hent(treffId)!!.status).isEqualTo(RekrutteringstreffStatus.UTKAST)
        assertThat(treffhendelser(treffId)).doesNotContain(RekrutteringstreffHendelsestype.SLETTET)
    }

    @Test
    fun `registrering av endring venter på jobbsøkerlåsen og varsler jobbsøker som svarte ja mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)
        db.publiser(treffId, navIdent)
        val person = db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker(1)), treffId).single()
        db.inviterJobbsøkere(listOf(person), treffId, navIdent)
        val endringer = Rekrutteringstreffendringer(endredeFelter = setOf(Endringsfelttype.NAVN))

        db.dataSource.medVentendeOperasjon(
            lås = { it.låsJobbsøkere(treffId, listOf(person)) },
            operasjon = { ctx.rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent) },
        ) { connection ->
            ctx.jobbsøkerRepository.leggTilHendelse(
                connection, person, JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON, AktørType.JOBBSØKER, navIdent,
            )
            ctx.jobbsøkerService.oppdaterStatusFraHendelser(connection, person)
        }

        assertThat(jobbsøkerhendelser(person)).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `tillegg av jobbsøkere venter på trefflåsen og hopper over jobbsøker som ble lagt til mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)

        val resultat = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { ctx.jobbsøkerService.leggTilJobbsøkere(listOf(jobbsøker(1)), treffId, navIdent) },
        ) { connection ->
            ctx.jobbsøkerRepository.leggTil(connection, listOf(jobbsøker(1)), treffId)
        }

        assertThat(resultat.antallLagtTil).isZero()
        assertThat(db.hentJobbsøkereForTreff(treffId)).hasSize(1)
    }

    @Test
    fun `endring av behov venter på trefflåsen og avviser arbeidsgiver som ble slettet mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)
        val arbeidsgiver = ctx.arbeidsgiverService.leggTilArbeidsgiver(
            LeggTilArbeidsgiver(Orgnr("999999999"), Orgnavn("Fiktiv testbedrift"), emptyList(), null, null, null),
            treffId,
            navIdent,
        )
        val behov = ArbeidsgiversBehov(
            listOf(BehovTag("Fiktivt testyrke", "YRKESTITTEL", 1)), listOf("Norsk"), 1, listOf(Ansettelsesform.FAST),
        )

        val oppdatert = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { ctx.arbeidsgiverService.oppdaterBehov(arbeidsgiver, treffId, behov, navIdent) },
        ) { connection ->
            ctx.arbeidsgiverRepository.markerSlettet(connection, arbeidsgiver.somUuid)
        }

        assertThat(oppdatert).isNull()
        assertThat(db.dataSource.connection.use { ctx.arbeidsgiverRepository.hentBehov(it, arbeidsgiver) }).isNull()
    }

    @Test
    fun `aktuell-status venter på jobbsøkerlåsen og skriver ingen ny hendelse når statusen ble satt mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)
        val person = db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker(1)), treffId).single()
        val før = db.hentJobbsøkereForTreff(treffId).single().aktuellForTreffStatus

        val resultat = db.dataSource.medVentendeOperasjon(
            lås = { it.låsJobbsøkere(treffId, listOf(person)) },
            operasjon = {
                ctx.jobbsøkerService.endreAktuellForTreffStatus(treffId, person, AktuellForTreffStatus.IKKE_AKTUELL, navIdent)
            },
        ) { connection ->
            ctx.jobbsøkerRepository.endreAktuellForTreffStatus(connection, person, AktuellForTreffStatus.IKKE_AKTUELL)
            ctx.jobbsøkerRepository.leggTilAktuellForTreffStatusHendelse(
                connection, person, AktuellForTreffStatus.IKKE_AKTUELL, før, navIdent,
            )
        }

        assertThat(resultat).isEqualTo(EndreAktuellForTreffStatusResultat.OK)
        assertThat(jobbsøkerhendelser(person).filter { it == JobbsøkerHendelsestype.AKTUELL_FOR_TREFF_STATUS_ENDRET })
            .hasSize(1)
    }

    private fun jobbsøker(nummer: Int) =
        LeggTilJobbsøker(Fødselsnummer("%011d".format(nummer)), Fornavn("Test"), Etternavn("Testesen"))

    private fun treffhendelser(treffId: TreffId): List<RekrutteringstreffHendelsestype> =
        ctx.rekrutteringstreffRepository.hentHendelser(treffId).map { it.hendelsestype }

    private fun jobbsøkerhendelser(person: PersonTreffId): List<JobbsøkerHendelsestype> =
        db.dataSource.connection.use { ctx.jobbsøkerRepository.hentHendelsestyper(it, person) }
}
