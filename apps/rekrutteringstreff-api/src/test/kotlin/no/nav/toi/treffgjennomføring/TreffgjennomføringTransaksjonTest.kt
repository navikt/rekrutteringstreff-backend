package no.nav.toi.treffgjennomføring

import io.javalin.http.BadRequestResponse
import no.nav.toi.ApplicationContext
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.arbeidsgiver.ArbeidsgiverKanIkkeSlettesException
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.MarkerSlettetResultat
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.oppmøte.OppmøteKanIkkeFjernesException
import no.nav.toi.låsTreff
import no.nav.toi.medVentendeOperasjon
import no.nav.toi.oppfølging.OppfølgingValidering
import no.nav.toi.oppfølging.Vurderingsvalg
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.InteresseRequestDto
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import no.nav.toi.treffgjennomføring.møteplan.Romfordeler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection

/**
 * Samtidighet og databasefeil i treffgjennomføringen, fra akseptansetesten for WorkOp (13.3 og 14.1.11).
 * Selve låsemekanismen testes i TransaksjonTest. Her viser vi at skriveoperasjonene i gjennomføringen
 * bygger på det som ble lagret mens de ventet, og at en feil midt i lagringen ruller tilbake alt.
 * Se docs/2-arkitektur/transaksjoner.md.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreffgjennomføringTransaksjonTest {

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

    // --- Samtidighet i treffgjennomføringen (akseptansetest 13.3) ------------------------------------------
    // Alle skriveoperasjonene tar trefflåsen og leser treffet på nytt etter ventingen. Testene under viser at
    // den som får låsen sist, bygger på det som ble lagret først, og avvises når regelen krever det.

    @Test
    fun `romflytting fra eldre visning bygger på flyttingen som ble lagret mens den ventet`() {
        val (treffId, a, b) = treffMedMøteplan()

        db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { ctx.møteplanService.flyttJobbsøkerTilRom(treffId, b, 2) },
        ) { connection -> flyttDirekte(connection, treffId, a, 2) }

        assertThat(romFor(treffId)).containsEntry(a.somString, 2).containsEntry(b.somString, 2)
    }

    @Test
    fun `samme person flyttet til ulike rom havner i rommet som ble lagret sist`() {
        val (treffId, a) = treffMedMøteplan()

        db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { ctx.møteplanService.flyttJobbsøkerTilRom(treffId, a, 1) },
        ) { connection -> flyttDirekte(connection, treffId, a, 2) }

        val rom = ctx.treffgjennomføringService.hent(treffId).rom
        assertThat(rom.filter { a.somString in it.jobbsøkere }.map { it.romnummer }).containsExactly(1)
    }

    @Test
    fun `samtidige vurderinger av ulike par lagres på hvert sitt par`() {
        val (treffId, a, b) = treffMedMøteplan()
        val (ag1, ag2) = ctx.treffgjennomføringService.hent(treffId).arbeidsgiverRekkefølge.map { it.arbeidsgiverTreffId }

        db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { ctx.oppfølgingService.lagreVurdering(treffId, vurdering(b, ag2, Vurderingsvalg.KANSKJE), navIdent) },
        ) { connection ->
            val kontekst = ctx.treffkontekstRepository.krevKontekst(connection, treffId)
            val ny = OppfølgingValidering.vurdering(vurdering(a, ag1, Vurderingsvalg.AKTUELL))
            ctx.oppfølgingRepository.lagre(
                connection, kontekst.krevJobbsøkerId(a), kontekst.krevArbeidsgiverId(ArbeidsgiverTreffId(ag1)), ny,
            )
        }

        val vurderinger = ctx.treffgjennomføringService.hent(treffId).vurderinger
            .associate { (it.personTreffId to it.arbeidsgiverTreffId) to it.vurderingsstatus }
        assertThat(vurderinger).containsExactlyInAnyOrderEntriesOf(
            mapOf((a.somString to ag1) to Vurderingsvalg.AKTUELL, (b.somString to ag2) to Vurderingsvalg.KANSKJE),
        )
    }

    @Test
    fun `fjerning av oppmøte avvises når interessen ble lagret mens den ventet`() {
        val treffId = workOpTreff()
        val ag = arbeidsgiver(treffId, 1)
        val person = jobbsøker(treffId, 1)
        oppmøte(treffId, person, møtt = true)

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { runCatching { oppmøte(treffId, person, møtt = false) }.exceptionOrNull() },
        ) { connection -> settInteresseDirekte(connection, treffId, person, ag) }

        assertThat(feil).isInstanceOf(OppmøteKanIkkeFjernesException::class.java)
        val gjennomføring = ctx.treffgjennomføringService.hent(treffId)
        assertThat(gjennomføring.oppmøte).containsExactly(person.somString)
        assertThat(gjennomføring.interesser.map { it.personTreffId }).containsExactly(person.somString)
    }

    @Test
    fun `interesse avvises når oppmøtet ble fjernet mens den ventet`() {
        val treffId = workOpTreff()
        val ag = arbeidsgiver(treffId, 1)
        val person = jobbsøker(treffId, 1)
        oppmøte(treffId, person, møtt = true)

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { runCatching { interesse(treffId, person, ag) }.exceptionOrNull() },
        ) { connection -> jobbsøkerhendelse(connection, person, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET) }

        assertThat(feil).isInstanceOf(BadRequestResponse::class.java)
        val gjennomføring = ctx.treffgjennomføringService.hent(treffId)
        assertThat(gjennomføring.oppmøte).isEmpty()
        assertThat(gjennomføring.interesser).isEmpty()
    }

    @Test
    fun `arbeidsgiversletting avvises når interessen ble lagret mens den ventet`() {
        val treffId = workOpTreff()
        val ag = arbeidsgiver(treffId, 1)
        arbeidsgiver(treffId, 2)
        val person = jobbsøker(treffId, 1)
        oppmøte(treffId, person, møtt = true)

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = {
                runCatching { ctx.arbeidsgiverService.markerArbeidsgiverSlettet(ag.somUuid, treffId, navIdent) }
                    .exceptionOrNull()
            },
        ) { connection -> settInteresseDirekte(connection, treffId, person, ag) }

        assertThat(feil).isInstanceOf(ArbeidsgiverKanIkkeSlettesException::class.java)
        assertThat(ctx.arbeidsgiverService.hentArbeidsgivere(treffId).map { it.arbeidsgiverTreffId }).contains(ag)
    }

    @Test
    fun `interesse avvises når arbeidsgiveren ble slettet mens den ventet`() {
        val treffId = workOpTreff()
        val ag = arbeidsgiver(treffId, 1)
        arbeidsgiver(treffId, 2)
        val person = jobbsøker(treffId, 1)
        oppmøte(treffId, person, møtt = true)

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { runCatching { interesse(treffId, person, ag) }.exceptionOrNull() },
        ) { connection -> ctx.arbeidsgiverRepository.markerSlettet(connection, ag.somUuid) }

        assertThat(feil).isInstanceOf(BadRequestResponse::class.java)
        assertThat(ctx.treffgjennomføringService.hent(treffId).interesser).isEmpty()
    }

    @Test
    fun `jobbsøkersletting avvises når oppmøtet ble registrert mens den ventet`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId, 1)

        val resultat = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { ctx.jobbsøkerService.markerSlettet(person, treffId, navIdent) },
        ) { connection -> jobbsøkerhendelse(connection, person, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE) }

        assertThat(resultat).isEqualTo(MarkerSlettetResultat.IKKE_TILLATT)
        assertThat(lagretStatus(person)).isEqualTo(JobbsøkerStatus.MØTT_OPP)
        assertThat(hendelsestyper(person)).doesNotContain(JobbsøkerHendelsestype.SLETTET)
    }

    @Test
    fun `oppmøte avvises når jobbsøkeren ble slettet mens det ventet`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId, 1)

        val feil = db.dataSource.medVentendeOperasjon(
            lås = { it.låsTreff(treffId) },
            operasjon = { runCatching { oppmøte(treffId, person, møtt = true) }.exceptionOrNull() },
        ) { connection -> jobbsøkerhendelse(connection, person, JobbsøkerHendelsestype.SLETTET) }

        assertThat(feil).isInstanceOf(BadRequestResponse::class.java)
        assertThat(lagretStatus(person)).isEqualTo(JobbsøkerStatus.SLETTET)
        assertThat(hendelsestyper(person)).doesNotContain(JobbsøkerHendelsestype.REGISTRERT_OPPMØTE)
        assertThat(ctx.treffgjennomføringService.hent(treffId).deltakernummer).isEmpty()
    }

    // --- Databasefeil midt i en sammensatt lagring (akseptansetest 14.1.11) -----------------------------

    @Test
    fun `databasefeil når møteplanen opprettes ruller tilbake oppsett, rom, rotasjon og steg`() {
        val treffId = workOpTreff()
        arbeidsgiver(treffId, 1)
        arbeidsgiver(treffId, 2)
        val person = jobbsøker(treffId, 1)
        oppmøte(treffId, person, møtt = true)
        val før = ctx.treffgjennomføringService.hent(treffId)

        // Hendelsen skrives til slutt, så alt annet er lagret i transaksjonen når feilen kommer.
        medDatabasefeilVedInsert("rekrutteringstreff_hendelse") {
            assertThatThrownBy { møteoppsett(treffId) }.hasStackTraceContaining(SIMULERT_DATABASEFEIL)
        }

        assertThat(ctx.treffgjennomføringService.hent(treffId)).isEqualTo(før)
        assertThat(antallRaderForTreff("moteoppsett", treffId, via = "treffgjennomforing")).isZero()
        assertThat(antallRaderForTreff("jobbsoker_romtildeling", treffId)).isZero()
        assertThat(antallRaderForTreff("arbeidsgiver_rotasjon", treffId, via = "arbeidsgiver")).isZero()

        møteoppsett(treffId)
        assertThat(ctx.rekrutteringstreffRepository.hentHendelser(treffId).map { it.hendelsestype })
            .containsOnlyOnce(RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_OPPRETTET)
    }

    @Test
    fun `databasefeil når oppmøtet registreres ruller tilbake deltakernummeret`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId, 1)

        medDatabasefeilVedInsert("jobbsoker_hendelse") {
            assertThatThrownBy { oppmøte(treffId, person, møtt = true) }.hasStackTraceContaining(SIMULERT_DATABASEFEIL)
        }

        assertThat(ctx.treffgjennomføringService.hent(treffId).deltakernummer).isEmpty()
        assertThat(lagretStatus(person)).isEqualTo(JobbsøkerStatus.LAGT_TIL)

        oppmøte(treffId, person, møtt = true)
        assertThat(ctx.treffgjennomføringService.hent(treffId).deltakernummer.map { it.deltakernummer }).containsExactly(1)
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

    private fun arbeidsgiver(treffId: TreffId, nummer: Int): ArbeidsgiverTreffId =
        db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr("99999999$nummer"), Orgnavn("Testbedrift $nummer"), emptyList(), null, null, null),
            treffId,
        )

    /** WorkOp med to arbeidsgivere, to fremmøtte og møteplan. Begge jobbsøkerne starter i rom 1. */
    private fun treffMedMøteplan(): Triple<TreffId, PersonTreffId, PersonTreffId> {
        val treffId = workOpTreff()
        arbeidsgiver(treffId, 1)
        arbeidsgiver(treffId, 2)
        val a = jobbsøker(treffId, 1)
        val b = jobbsøker(treffId, 2)
        oppmøte(treffId, a, møtt = true)
        oppmøte(treffId, b, møtt = true)
        møteoppsett(treffId)
        ctx.møteplanService.flyttJobbsøkerTilRom(treffId, a, 1)
        ctx.møteplanService.flyttJobbsøkerTilRom(treffId, b, 1)
        return Triple(treffId, a, b)
    }

    private fun møteoppsett(treffId: TreffId) {
        ctx.møteplanService.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 10), navIdent)
    }

    private fun interesse(treffId: TreffId, person: PersonTreffId, ag: ArbeidsgiverTreffId) {
        ctx.matchingService.settInteresse(treffId, InteresseRequestDto(person.somString, ag.somString, interessert = true))
    }

    private fun vurdering(person: PersonTreffId, ag: String, valg: Vurderingsvalg) =
        VurderingDto(personTreffId = person.somString, arbeidsgiverTreffId = ag, vurderingsstatus = valg)

    private fun romFor(treffId: TreffId): Map<String, Int> =
        ctx.treffgjennomføringService.hent(treffId).rom
            .flatMap { rom -> rom.jobbsøkere.map { it to rom.romnummer } }
            .toMap()

    private fun flyttDirekte(connection: Connection, treffId: TreffId, person: PersonTreffId, romnummer: Int) {
        val kontekst = ctx.treffkontekstRepository.krevKontekst(connection, treffId)
        val oppmøte = ctx.oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
        val rom = ctx.møteplanRepository.hentMøteplan(connection, kontekst, oppmøte).rom
        ctx.møteplanRepository.erstattRomfordeling(connection, kontekst, Romfordeler.flytt(rom, person, romnummer))
    }

    private fun settInteresseDirekte(connection: Connection, treffId: TreffId, person: PersonTreffId, ag: ArbeidsgiverTreffId) {
        val kontekst = ctx.treffkontekstRepository.krevKontekst(connection, treffId)
        ctx.matchingRepository.settInteresse(connection, kontekst.krevJobbsøkerId(person), kontekst.krevArbeidsgiverId(ag), true)
    }

    private fun jobbsøkerhendelse(connection: Connection, person: PersonTreffId, hendelsestype: JobbsøkerHendelsestype) {
        ctx.hendelseWriter.forJobbsøker(connection, person, hendelsestype, navIdent)
        ctx.jobbsøkerService.oppdaterStatusFraHendelser(connection, person)
    }

    /** Lar alle INSERT i [tabell] feile mens [block] kjører. */
    private fun medDatabasefeilVedInsert(tabell: String, block: () -> Unit) {
        db.dataSource.connection.use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE FUNCTION simulert_databasefeil() RETURNS trigger AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION '$SIMULERT_DATABASEFEIL'; END
                    ${'$'}${'$'} LANGUAGE plpgsql
                    """.trimIndent()
                )
                stmt.execute("CREATE TRIGGER simulert_databasefeil BEFORE INSERT ON $tabell FOR EACH ROW EXECUTE FUNCTION simulert_databasefeil()")
            }
        }
        try {
            block()
        } finally {
            db.dataSource.connection.use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("DROP TRIGGER simulert_databasefeil ON $tabell")
                    stmt.execute("DROP FUNCTION simulert_databasefeil()")
                }
            }
        }
    }

    /** Teller rader i [tabell] for treffet, direkte eller via treffgjennomføringen eller arbeidsgiveren. */
    private fun antallRaderForTreff(tabell: String, treffId: TreffId, via: String? = null): Int {
        val kobling = when (via) {
            null -> "t.rekrutteringstreff_id = x.rekrutteringstreff_id"
            "treffgjennomforing" ->
                "t.rekrutteringstreff_id = (SELECT g.rekrutteringstreff_id FROM treffgjennomforing g WHERE g.treffgjennomforing_id = x.treffgjennomforing_id)"
            "arbeidsgiver" ->
                "t.rekrutteringstreff_id = (SELECT a.rekrutteringstreff_id FROM arbeidsgiver a WHERE a.arbeidsgiver_id = x.arbeidsgiver_id)"
            else -> error("Ukjent kobling $via")
        }
        return db.dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM $tabell x JOIN rekrutteringstreff t ON $kobling WHERE t.id = ?").use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.executeQuery().use { it.next(); it.getInt(1) }
            }
        }
    }

    private companion object {
        const val SIMULERT_DATABASEFEIL = "Simulert databasefeil"
    }
}
