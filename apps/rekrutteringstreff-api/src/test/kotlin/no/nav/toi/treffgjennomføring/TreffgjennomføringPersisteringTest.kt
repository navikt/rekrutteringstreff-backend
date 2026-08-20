package no.nav.toi.treffgjennomføring

import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.treffgjennomføring.matching.MatchingRepository
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import no.nav.toi.treffgjennomføring.møteplan.Rom
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import no.nav.toi.jobbsoker.sok.JobbsøkerSøkRequest
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreffgjennomføringPersisteringTest {

    private val db = TestDatabase()
    private val kontekstRepository = TreffkontekstRepository()
    private val stegRepository = StegRepository()
    private val oppmøteRepository = OppmøteRepository()
    private val møteplanRepository = MøteplanRepository()
    private val matchingRepository = MatchingRepository()
    private val oppfølgingRepository = OppfølgingRepository()
    private val reader = TreffgjennomføringReader(
        stegRepository, oppmøteRepository, møteplanRepository, matchingRepository, oppfølgingRepository,
    )
    private val sokRepository = JobbsøkerSokRepository(db.dataSource)

    @BeforeAll
    fun migrer() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    @Test
    fun `tomt aggregat har standardverdier og ingen lagrede rader`() {
        val treff = opprettTreff()

        val aggregat = les(treff)

        assertThat(aggregat.gjeldendeSteg).isEqualTo(TreffgjennomføringSteg.OPPMØTE)
        assertThat(aggregat.starttidspunkt).isEqualTo("10:00")
        assertThat(aggregat.varighetPerMøteMinutter).isEqualTo(10)
        assertThat(aggregat.oppmøte).isEmpty()
        assertThat(aggregat.rom).isEmpty()
    }

    @Test
    fun `oppmøtehendelse uten statusendring gir ikke fremmøtt`() {
        val treff = opprettTreff()
        val person = jobbsøker(treff)

        leggTilOppmøtehendelse(person, "REGISTRERT_OPPMØTE", Instant.now())

        assertThat(les(treff).oppmøte).isEmpty()
    }

    @Test
    fun `statusen bestemmer tilstanden, uavhengig av hendelsesrekkefølge`() {
        val treff = opprettTreff()
        val person = jobbsøker(treff)

        registrerOppmøte(person)
        assertThat(les(treff).oppmøte).containsExactly(person.somString)

        registrerOppmøte(person, JobbsøkerStatus.LAGT_TIL)
        assertThat(les(treff).oppmøte).isEmpty()

        registrerOppmøte(person)
        assertThat(les(treff).oppmøte).containsExactly(person.somString)
    }

    @Test
    fun `deltakernummer gjenbrukes aldri, men beholdes for samme person`() {
        val treff = opprettTreff(RekrutteringstreffKategori.WORKOP)
        val første = jobbsøker(treff, "11111111111")
        val andre = jobbsøker(treff, "22222222222")

        val nummerFørste = db.dataSource.connection.use { conn ->
            val treffDbId = treffDbId(treff)
            oppmøteRepository.tildelDeltakernummer(conn, treffDbId, jobbsøkerDbId(første))
        }
        val nummerFørsteIgjen = db.dataSource.connection.use { conn ->
            oppmøteRepository.tildelDeltakernummer(conn, treffDbId(treff), jobbsøkerDbId(første))
        }
        val nummerAndre = db.dataSource.connection.use { conn ->
            oppmøteRepository.tildelDeltakernummer(conn, treffDbId(treff), jobbsøkerDbId(andre))
        }

        assertThat(nummerFørste).isEqualTo(1)
        assertThat(nummerFørsteIgjen).isEqualTo(1)
        assertThat(nummerAndre).isEqualTo(2)
    }

    @Test
    fun `romfordeling lagres og leses tilbake sortert på deltakernummer`() {
        val treff = opprettTreff(RekrutteringstreffKategori.WORKOP)
        arbeidsgiver(treff, "999999991")
        arbeidsgiver(treff, "999999992")
        val p1 = jobbsøker(treff, "11111111111")
        val p2 = jobbsøker(treff, "22222222222")
        listOf(p1, p2).forEach { registrerOppmøte(it) }

        db.dataSource.connection.use { conn ->
            val kontekst = kontekstRepository.hentTreffkontekst(conn, treff)!!
            møteplanRepository.erstattRomfordeling(
                conn, kontekst.treffDbId,
                listOf(Rom(1, listOf(p2, p1)), Rom(2, emptyList())),
                kontekst,
            )
        }

        val rom = les(treff).rom
        assertThat(rom).hasSize(2)
        assertThat(rom.first { it.romnummer == 1 }.jobbsøkere).containsExactly(p1.somString, p2.somString)
        assertThat(rom.first { it.romnummer == 2 }.jobbsøkere).isEmpty()
    }

    @Test
    fun `jobbsøkersøket beriker bare personene på den returnerte siden`() {
        val treff = opprettTreff(RekrutteringstreffKategori.WORKOP)
        val personer = (1..5).map { jobbsøker(treff, "1111111111$it", etternavn = "Person$it") }
        personer.forEach { registrerOppmøte(it) }

        val side = sokRepository.sok(treff, JobbsøkerSøkRequest(side = 1, antallPerSide = 2))

        assertThat(side.totalt).isEqualTo(5L)
        assertThat(side.jobbsøkere).hasSize(2)
        side.jobbsøkere.forEach { treffrad ->
            assertThat(treffrad.status).isEqualTo(JobbsøkerStatus.MØTT_OPP)
        }
    }

    @Test
    fun `jobbsøkersøket teller fremmøtte via statusen`() {
        val treff = opprettTreff(RekrutteringstreffKategori.WORKOP)
        val arbeidsgiverId = arbeidsgiver(treff, "999999991")
        val person = jobbsøker(treff)
        registrerOppmøte(person)

        db.dataSource.connection.use { conn ->
            matchingRepository.settInteresse(conn, jobbsøkerDbId(person), arbeidsgiverId, true)
        }

        val rad = sokRepository.sok(treff, JobbsøkerSøkRequest()).jobbsøkere.single()

        assertThat(rad.status).isEqualTo(JobbsøkerStatus.MØTT_OPP)
        assertThat(sokRepository.sok(treff, JobbsøkerSøkRequest()).antallPerStatus[JobbsøkerStatus.MØTT_OPP])
            .isEqualTo(1)
    }

    @Test
    fun `jobbsøker uten oppmøtehendelse er ikke møtt`() {
        val treff = opprettTreff()
        jobbsøker(treff)

        val rad = sokRepository.sok(treff, JobbsøkerSøkRequest()).jobbsøkere.single()

        assertThat(rad.status).isNotEqualTo(JobbsøkerStatus.MØTT_OPP)
    }

    private fun les(treff: TreffId) = db.dataSource.connection.use { conn ->
        reader.les(conn, kontekstRepository.hentTreffkontekst(conn, treff)!!)
    }

    private fun opprettTreff(
        kategori: RekrutteringstreffKategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF,
    ): TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A100001", kategori = kategori)

    private fun jobbsøker(
        treff: TreffId,
        fnr: String = "12345678901",
        etternavn: String = "Testesen",
    ): PersonTreffId = db.leggTilJobbsøkereMedHendelse(
        listOf(LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn(etternavn))),
        treff,
    ).first()

    private fun arbeidsgiver(treff: TreffId, orgnr: String): Long {
        val id = db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Testbedrift $orgnr"), emptyList(), null, null, null),
            treff,
        )
        return db.dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT arbeidsgiver_id FROM arbeidsgiver WHERE id = ?").use { stmt ->
                stmt.setObject(1, id.somUuid)
                stmt.executeQuery().use { it.next(); it.getLong(1) }
            }
        }
    }

    private fun treffDbId(treff: TreffId): Long = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?").use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { it.next(); it.getLong(1) }
        }
    }

    private fun jobbsøkerDbId(personTreffId: PersonTreffId): Long = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT jobbsoker_id FROM jobbsoker WHERE id = ?").use { stmt ->
            stmt.setObject(1, personTreffId.somUuid)
            stmt.executeQuery().use { it.next(); it.getLong(1) }
        }
    }

    private fun leggTilOppmøtehendelse(personTreffId: PersonTreffId, type: String, tidspunkt: Instant) {
        val sql = """
            INSERT INTO jobbsoker_hendelse
              (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
            VALUES (?, (SELECT jobbsoker_id FROM jobbsoker WHERE id = ?), ?, ?, 'MARKEDSKONTAKT_ELLER_VEILEDER', 'A100001')
        """.trimIndent()
        db.dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, UUID.randomUUID())
                stmt.setObject(2, personTreffId.somUuid)
                stmt.setTimestamp(3, Timestamp.from(tidspunkt))
                stmt.setString(4, type)
                stmt.executeUpdate()
            }
        }
    }

    private fun registrerOppmøte(
        personTreffId: PersonTreffId,
        status: JobbsøkerStatus = JobbsøkerStatus.MØTT_OPP,
    ) {
        val hendelsestype =
            if (status == JobbsøkerStatus.MØTT_OPP) "REGISTRERT_OPPMØTE" else "REGISTRERT_OPPMØTE_FJERNET"
        leggTilOppmøtehendelse(personTreffId, hendelsestype, Instant.now())
        db.dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE jobbsoker SET status = ? WHERE id = ?").use { stmt ->
                stmt.setString(1, status.name)
                stmt.setObject(2, personTreffId.somUuid)
                stmt.executeUpdate()
            }
        }
    }
}
