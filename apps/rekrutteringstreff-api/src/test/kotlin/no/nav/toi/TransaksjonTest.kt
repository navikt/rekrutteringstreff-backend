package no.nav.toi

import io.javalin.http.BadRequestResponse
import no.nav.toi.exception.UlovligOppdateringException
import no.nav.toi.jobbsoker.AktuellForTreffStatus
import no.nav.toi.jobbsoker.EndreAktuellForTreffStatusResultat
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import no.nav.toi.rekrutteringstreff.eier.leggTil
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomføring.krevKontekst
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
 * Tester transaksjonshjelperne og låsene i transactionManager.kt og låsing.kt. Vi tester mekanismen her
 * og ikke hver operasjon som bruker den. Se docs/2-arkitektur/transaksjoner.md.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransaksjonTest {

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
    fun `Error i transaksjonen ruller tilbake og slipper låsen`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)

        assertThatThrownBy {
            db.dataSource.medLåstTreff(treffId) { connection ->
                ctx.rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.SLETTET)
                throw StackOverflowError("Simulert feil")
            }
        }.isInstanceOf(StackOverflowError::class.java)

        assertThat(ctx.rekrutteringstreffRepository.hent(treffId)!!.status).isEqualTo(RekrutteringstreffStatus.UTKAST)
        assertThat(db.dataSource.medLåstTreff(treffId) { true }).isTrue()
    }

    @Test
    fun `medLåstTreff venter på trefflåsen og ser endringen som ble lagret mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { runCatching { ctx.rekrutteringstreffService.publiser(treffId, navIdent) }.exceptionOrNull() },
        ) { connection ->
            ctx.rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.SLETTET)
        }

        assertThat(feil).isInstanceOf(UlovligOppdateringException::class.java)
        assertThat(ctx.rekrutteringstreffRepository.hent(treffId)!!.status).isEqualTo(RekrutteringstreffStatus.SLETTET)
        assertThat(ctx.rekrutteringstreffRepository.hentHendelser(treffId).map { it.hendelsestype })
            .doesNotContain(RekrutteringstreffHendelsestype.PUBLISERT)
    }

    @Test
    fun `medLåsteJobbsøkere venter på jobbsøkerlåsen og ser endringen som ble lagret mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)
        val person = jobbsøker(treffId, 1)
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
        assertThat(hendelsestyper(person).filter { it == JobbsøkerHendelsestype.AKTUELL_FOR_TREFF_STATUS_ENDRET }).hasSize(1)
    }

    /** Svar låser bare jobbsøkeren. Derfor må oppmøte låse jobbsøkeren i tillegg til treffet. */
    @Test
    fun `oppmøte venter på jobbsøkerlåsen og ser svaret som ble lagret mens den ventet`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId, 1)
        ctx.jobbsøkerService.svarPåVegneAvJobbsøker(person, treffId, navIdent, true)
        oppmøte(treffId, person, møtt = true)

        db.dataSource.medVentendeOperasjon(
            lås = { it.låsJobbsøkere(treffId, listOf(person)) },
            operasjon = { oppmøte(treffId, person, møtt = false) },
        ) { connection ->
            ctx.hendelseWriter.forJobbsøker(connection, person, JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER, navIdent)
            ctx.jobbsøkerService.oppdaterStatusFraHendelser(connection, person)
        }

        assertThat(lagretStatus(person)).isEqualTo(JobbsøkerStatus.SVART_NEI)
    }

    /** Avlys og fullfør låser alle jobbsøkerne på treffet før de velger hendelse ut fra statusen. */
    @Test
    fun `avlys venter på jobbsøkerlåsen og velger hendelse ut fra svaret som ble lagret mens den ventet`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)
        db.publiser(treffId, navIdent)
        val person = jobbsøker(treffId, 1)
        db.inviterJobbsøkere(listOf(person), treffId, navIdent)

        db.dataSource.medVentendeOperasjon(
            lås = { it.låsJobbsøkere(treffId, listOf(person)) },
            operasjon = { ctx.rekrutteringstreffService.avlys(treffId, navIdent) },
        ) { connection ->
            ctx.hendelseWriter.forJobbsøker(connection, person, JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER, navIdent)
            ctx.jobbsøkerService.oppdaterStatusFraHendelser(connection, person)
        }

        assertThat(hendelsestyper(person))
            .contains(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
            .doesNotContain(JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST)
    }

    /** Neste deltakernummer telles fra dem som allerede finnes, så tellingen må skje under trefflåsen. */
    @Test
    fun `oppmøte venter på trefflåsen og teller med deltakernummer som ble tildelt mens den ventet`() {
        val treffId = workOpTreff()
        val første = jobbsøker(treffId, 1)
        val andre = jobbsøker(treffId, 2)

        db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { oppmøte(treffId, andre, møtt = true) },
        ) { connection ->
            val kontekst = ctx.treffkontekstRepository.krevKontekst(connection, treffId)
            ctx.oppmøteRepository.tildelDeltakernummer(connection, kontekst.treffDbId, kontekst.krevJobbsøkerId(første))
        }

        val deltakernumre = ctx.treffgjennomføringService.hent(treffId).deltakernummer
            .associate { it.personTreffId to it.deltakernummer }
        assertThat(deltakernumre).containsExactlyInAnyOrderEntriesOf(mapOf(første.somString to 1, andre.somString to 2))
    }

    /** To samtidige slettinger kan hver for seg se at det finnes en annen eier. Trefflåsen hindrer at begge slettes. */
    @Test
    fun `eiersletting venter på trefflåsen og beholder siste eier`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        ctx.eierRepository.leggTil(treffId, "B654321", "0315")

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { runCatching { ctx.eierService.slettEier(treffId, "A123456", navIdent) }.exceptionOrNull() },
        ) { connection ->
            ctx.eierRepository.slett(connection, treffId, "B654321")
        }

        assertThat(feil).isInstanceOf(BadRequestResponse::class.java)
        assertThat(ctx.eierService.hentEiere(treffId).tilNavIdenter()).containsExactly("A123456")
    }

    private fun workOpTreff(): TreffId =
        db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, kategori = RekrutteringstreffKategori.WORKOP)

    private fun jobbsøker(treffId: TreffId, nummer: Int): PersonTreffId =
        db.leggTilJobbsøkereMedHendelse(
            listOf(LeggTilJobbsøker(Fødselsnummer("%011d".format(nummer)), Fornavn("Test"), Etternavn("Testesen"))),
            treffId,
        ).single()

    private fun oppmøte(treffId: TreffId, person: PersonTreffId, møtt: Boolean) {
        ctx.oppmøteService.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, møtt), navIdent)
    }

    private fun lagretStatus(person: PersonTreffId): JobbsøkerStatus? =
        db.dataSource.connection.use { ctx.jobbsøkerRepository.hentStatus(it, person) }

    private fun hendelsestyper(person: PersonTreffId): List<JobbsøkerHendelsestype> =
        db.dataSource.connection.use { ctx.jobbsøkerRepository.hentHendelsestyper(it, person) }
}
