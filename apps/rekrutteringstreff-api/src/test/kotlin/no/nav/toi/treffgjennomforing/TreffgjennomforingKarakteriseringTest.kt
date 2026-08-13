package no.nav.toi.treffgjennomforing

import no.nav.toi.JacksonConfig
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomforing.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomforing.dto.InteresseRequestDto
import no.nav.toi.treffgjennomforing.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomforing.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomforing.dto.RomDto
import no.nav.toi.treffgjennomforing.dto.TreffgjennomforingDto
import no.nav.toi.treffgjennomforing.dto.VurderingDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreffgjennomforingKarakteriseringTest {

    private val db = TestDatabase()
    private val mapper = JacksonConfig.mapper
    private val navIdent = "Z999999"

    private val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
    private val arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
    private val rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)

    private val service = TreffgjennomforingService(
        dataSource = db.dataSource,
        kontekstRepository = TreffkontekstRepository(),
        repository = TreffgjennomforingRepository(),
        jobbsøkerRepository = jobbsøkerRepository,
        arbeidsgiverRepository = arbeidsgiverRepository,
        rekrutteringstreffRepository = rekrutteringstreffRepository,
        mapper = mapper,
    )

    @BeforeAll
    fun migrer() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    @Test
    fun `aggregatet med data i alle ni tabeller har uendret innhold`() {
        val s = fulltScenario()

        val aggregat: TreffgjennomforingDto = service.hent(s.treffId)

        assertThat(aggregat.rekrutteringstreffId).isEqualTo(s.treffId.somString)
        assertThat(aggregat.fase).isEqualTo(TreffgjennomføringFase.VURDERING)
        assertThat(aggregat.antallRom).isEqualTo(2)
        assertThat(aggregat.starttidspunkt).isEqualTo("09:00")
        assertThat(aggregat.varighetPerMøteMinutter).isEqualTo(15)
        assertThat(aggregat.oppmøte).containsExactlyInAnyOrder(s.p1.somString, s.p2.somString)

        assertThat(aggregat.deltakernummer.map { it.nummer }).containsExactly(1, 2)
        assertThat(aggregat.rom.map { it.romnummer }).containsExactly(1, 2)
        assertThat(aggregat.rom.flatMap { it.jobbsøkere })
            .containsExactlyInAnyOrder(s.p1.somString, s.p2.somString)

        assertThat(aggregat.arbeidsgiverRekkefølge.map { it.startPosisjon }).containsExactly(0, 1)
        assertThat(aggregat.arbeidsgiverRekkefølge.map { it.arbeidsgiverTreffId })
            .containsExactly(s.ag1.somString, s.ag2.somString)

        assertThat(aggregat.interesser).hasSize(2)
        assertThat(aggregat.interesser.map { it.arbeidsgiverTreffId }).containsOnly(s.ag1.somString)

        val fordeling = aggregat.intervjufordelinger.single()
        assertThat(fordeling.arbeidsgiverTreffId).isEqualTo(s.ag1.somString)
        assertThat(fordeling.inkludertePersonTreffIder).containsExactly(s.p1.somString)
        assertThat(fordeling.ekskludertePersonTreffIder).containsExactly(s.p2.somString)

        val vurdering = aggregat.vurderinger.single()
        assertThat(vurdering.personTreffId).isEqualTo(s.p1.somString)
        assertThat(vurdering.arbeidsgiverTreffId).isEqualTo(s.ag1.somString)
        assertThat(vurdering.vurdering).isEqualTo(Vurderingsvalg.AKTUELL)
        assertThat(vurdering.notater).containsExactlyInAnyOrder("AG_GODT_INNTRYKK", "JS_POSITIV")
        assertThat(vurdering.andregangsintervju).isTrue()
        assertThat(vurdering.andregangsintervjuDato).isEqualTo("2026-09-01")
        assertThat(vurdering.jobbtilbud).isTrue()
    }

    @Test
    fun `skriveoperasjon returnerer samme aggregat som en etterfølgende lesing`() {
        val s = fulltScenario()

        val fraSkriving = interesse(s.treffId, s.p2, s.ag2)
        val fraLesing = service.hent(s.treffId)

        assertThat(mapper.writeValueAsString(fraSkriving))
            .isEqualTo(mapper.writeValueAsString(fraLesing))
    }

    @Test
    fun `tomt aggregat har standardverdier og oppretter ingenting`() {
        val treffId = workOpTreff()

        val aggregat = service.hent(treffId)

        assertThat(aggregat.fase).isEqualTo(TreffgjennomføringFase.OPPMØTE)
        assertThat(aggregat.starttidspunkt).isEqualTo("10:00")
        assertThat(aggregat.varighetPerMøteMinutter).isEqualTo(10)
        assertThat(aggregat.oppmøte).isEmpty()
        assertThat(aggregat.rom).isEmpty()
        assertThat(aggregat.interesser).isEmpty()
        assertThat(aggregat.intervjufordelinger).isEmpty()
        assertThat(aggregat.vurderinger).isEmpty()
        assertThat(antallTreffgjennomforingsrader()).isEqualTo(0)
    }

    @Test
    fun `angret oppmøte tømmer alle fire registreringstabellene for bare den ene personen`() {
        val s = fulltScenario()
        interesse(s.treffId, s.p2, s.ag2)
        val id1 = jobbsøkerDbId(s.p1)
        val id2 = jobbsøkerDbId(s.p2)

        ikkeMøtt(s.treffId, s.p1)

        assertThat(antallRader("interesse", id1)).isEqualTo(0)
        assertThat(antallRader("intervju_fordeling", id1)).isEqualTo(0)
        assertThat(antallRader("vurdering", id1)).isEqualTo(0)
        assertThat(antallRader("jobbsoker_rom_tildeling", id1)).isEqualTo(0)

        assertThat(antallRader("interesse", id2)).isGreaterThan(0)
        assertThat(antallRader("intervju_fordeling", id2)).isGreaterThan(0)
        assertThat(antallRader("jobbsoker_rom_tildeling", id2)).isEqualTo(1)
    }

    @Test
    fun `fjerning av oppmøte med registreringer krever bekreftelse`() {
        val s = fulltScenario()
        val id1 = jobbsøkerDbId(s.p1)

        assertThatThrownBy { ikkeMøtt(s.treffId, s.p1, bekreft = false) }
            .isInstanceOf(OppmøteHarRegistreringerException::class.java)

        assertThat(service.hent(s.treffId).oppmøte).contains(s.p1.somString)
        assertThat(antallRader("vurdering", id1)).isEqualTo(1)
    }

    @Test
    fun `fjerning uten registreringer trenger ingen bekreftelse`() {
        val treffId = vanligTreff()
        val person = jobbsøker(treffId)
        møtt(treffId, person)

        ikkeMøtt(treffId, person, bekreft = false)

        assertThat(service.hent(treffId).oppmøte).isEmpty()
    }

    @Test
    fun `fasen går bare framover - angret oppmøte senker den ikke`() {
        val s = fulltScenario()
        assertThat(service.hent(s.treffId).fase).isEqualTo(TreffgjennomføringFase.VURDERING)

        ikkeMøtt(s.treffId, s.p1)
        ikkeMøtt(s.treffId, s.p2)

        assertThat(service.hent(s.treffId).fase).isEqualTo(TreffgjennomføringFase.VURDERING)
    }

    @Test
    fun `fasen settes av hvert steg i rekkefølge`() {
        val treffId = workOpTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)

        møtt(treffId, person)
        assertThat(service.hent(treffId).fase).isEqualTo(TreffgjennomføringFase.OPPMØTE)

        service.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 15), navIdent)
        assertThat(service.hent(treffId).fase).isEqualTo(TreffgjennomføringFase.ROM)

        interesse(treffId, person, ag)
        assertThat(service.hent(treffId).fase).isEqualTo(TreffgjennomføringFase.INTERESSE)

        service.fordelIntervjuer(treffId, navIdent)
        assertThat(service.hent(treffId).fase).isEqualTo(TreffgjennomføringFase.FORDELING)

        service.lagreVurdering(
            treffId,
            VurderingDto(person.somString, ag.somString, Vurderingsvalg.AKTUELL),
            navIdent,
        )
        assertThat(service.hent(treffId).fase).isEqualTo(TreffgjennomføringFase.VURDERING)
    }

    @Test
    fun `samtidige oppmøteregistreringer gir ulike deltakernummer`() {
        val treffId = workOpTreff()
        val personer = (1..6).map { jobbsøker(treffId, "1234567890$it") }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(personer.size)

        try {
            val oppgaver = personer.map { person ->
                pool.submit {
                    start.await()
                    møtt(treffId, person)
                }
            }
            start.countDown()
            oppgaver.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        val numre = service.hent(treffId).deltakernummer.map { it.nummer }
        assertThat(numre).hasSize(personer.size)
        assertThat(numre.toSet()).hasSize(personer.size)
        assertThat(numre.sorted()).isEqualTo((1..personer.size).toList())
    }

    @Test
    fun `deltakernummer gjenbrukes ikke - samme person får sitt opprinnelige tilbake`() {
        val treffId = workOpTreff()
        val p1 = jobbsøker(treffId, "11111111111")
        val p2 = jobbsøker(treffId, "22222222222")

        møtt(treffId, p1)
        møtt(treffId, p2)
        ikkeMøtt(treffId, p1)
        møtt(treffId, p1)

        val numre = service.hent(treffId).deltakernummer.associate { it.personTreffId to it.nummer }
        assertThat(numre[p1.somString]).isEqualTo(1)
        assertThat(numre[p2.somString]).isEqualTo(2)
    }

    @Test
    fun `vanlig treff tildeler ikke deltakernummer`() {
        val treffId = vanligTreff()
        val person = jobbsøker(treffId)

        møtt(treffId, person)

        assertThat(service.hent(treffId).deltakernummer).isEmpty()
        assertThat(service.hent(treffId).oppmøte).containsExactly(person.somString)
    }

    @Test
    fun `oppmøte og angring skriver én hendelse hver, med deltakernummer i dataene`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)

        møtt(treffId, person)
        assertThat(jobbsøkerhendelser(treffId).filter { it == "MØTT_OPP" }).hasSize(1)
        assertThat(hendelsedata("MØTT_OPP").single()).contains("\"deltakernummer\": 1")

        ikkeMøtt(treffId, person)
        assertThat(jobbsøkerhendelser(treffId).filter { it == "ANGRE_MØTT_OPP" }).hasSize(1)
        assertThat(hendelsedata("ANGRE_MØTT_OPP").single())
            .contains("\"interesser\"", "\"intervjuplasser\"", "\"vurderinger\"")
    }

    @Test
    fun `interesse skriver hendelse hos både jobbsøker og arbeidsgiver`() {
        val treffId = vanligTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)

        interesse(treffId, person, ag)

        assertThat(jobbsøkerhendelser(treffId).filter { it == "INTERESSE_REGISTRERT" }).hasSize(1)
        assertThat(arbeidsgiverhendelser(treffId).filter { it == "INTERESSE_REGISTRERT" }).hasSize(1)

        interesse(treffId, person, ag, interessert = false)

        assertThat(jobbsøkerhendelser(treffId).filter { it == "ANGRE_INTERESSE_REGISTRERT" }).hasSize(1)
        assertThat(arbeidsgiverhendelser(treffId).filter { it == "ANGRE_INTERESSE_REGISTRERT" }).hasSize(1)
    }

    @Test
    fun `uendret interesse er idempotent og skriver ingen ny hendelse`() {
        val treffId = vanligTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)

        interesse(treffId, person, ag)
        interesse(treffId, person, ag)

        assertThat(jobbsøkerhendelser(treffId).filter { it == "INTERESSE_REGISTRERT" }).hasSize(1)
        assertThat(service.hent(treffId).interesser).hasSize(1)
    }

    @Test
    fun `vurdering skriver én hendelse per endret felt`() {
        val treffId = vanligTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)

        service.lagreVurdering(
            treffId,
            VurderingDto(
                personTreffId = person.somString,
                arbeidsgiverTreffId = ag.somString,
                vurdering = Vurderingsvalg.AKTUELL,
                notater = listOf("AG_GODT_INNTRYKK"),
                andregangsintervju = true,
                andregangsintervjuDato = "2026-09-01",
                jobbtilbud = true,
            ),
            navIdent,
        )

        val hendelser = jobbsøkerhendelser(treffId)
        assertThat(hendelser.filter { it == "VURDERT" }).hasSize(1)
        assertThat(hendelser.filter { it == "NOTAT_LAGT_TIL" }).hasSize(1)
        assertThat(hendelser.filter { it == "ANDREGANGSINTERVJU_AVTALT" }).hasSize(1)
        assertThat(hendelser.filter { it == "JOBBTILBUD_GITT" }).hasSize(1)
    }

    @Test
    fun `tom vurdering sletter raden i stedet for å lagre den`() {
        val treffId = vanligTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)

        service.lagreVurdering(
            treffId,
            VurderingDto(person.somString, ag.somString, Vurderingsvalg.AKTUELL),
            navIdent,
        )
        assertThat(service.hent(treffId).vurderinger).hasSize(1)

        service.lagreVurdering(treffId, VurderingDto(person.somString, ag.somString), navIdent)

        assertThat(service.hent(treffId).vurderinger).isEmpty()
    }

    @Test
    fun `interesse speiles inn i en eksisterende intervjufordeling`() {
        val treffId = workOpTreff()
        val ag = arbeidsgivere(treffId).single()
        val p1 = jobbsøker(treffId, "11111111111")
        val p2 = jobbsøker(treffId, "22222222222")
        møtt(treffId, p1)
        møtt(treffId, p2)
        interesse(treffId, p1, ag)
        service.fordelIntervjuer(treffId, navIdent)

        interesse(treffId, p2, ag)

        val fordeling = service.hent(treffId).intervjufordelinger.single()
        assertThat(fordeling.inkludertePersonTreffIder).contains(p2.somString)

        interesse(treffId, p2, ag, interessert = false)

        val etter = service.hent(treffId).intervjufordelinger.single()
        assertThat(etter.inkludertePersonTreffIder).doesNotContain(p2.somString)
        assertThat(etter.ekskludertePersonTreffIder).doesNotContain(p2.somString)
    }

    @Test
    fun `rom normaliseres etter oppmøte ved lesing`() {
        val treffId = workOpTreff()
        val p1 = jobbsøker(treffId, "11111111111")
        val p2 = jobbsøker(treffId, "22222222222")
        møtt(treffId, p1)
        møtt(treffId, p2)
        service.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 15), navIdent)
        assertThat(service.hent(treffId).rom.flatMap { it.jobbsøkere }).hasSize(2)

        ikkeMøtt(treffId, p2)

        val rom = service.hent(treffId).rom
        assertThat(rom.flatMap { it.jobbsøkere }).containsExactly(p1.somString)
    }

    @Test
    fun `interesse krever at jobbsøkeren har møtt opp`() {
        val treffId = vanligTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)

        assertThatThrownBy { interesse(treffId, person, ag) }
            .hasMessageContaining("Bare fremmøtte jobbsøkere kan registrere interesse")
    }

    @Test
    fun `WorkOp-steg avvises på vanlig treff`() {
        val treffId = vanligTreff()
        jobbsøker(treffId).also { møtt(treffId, it) }

        assertThatThrownBy { service.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 15), navIdent) }
            .hasMessageContaining("Steget finnes bare på treff av kategorien WORKOP")
    }

    private fun fulltScenario(): Scenario {
        val treffId = workOpTreff(antallArbeidsgivere = 2)
        val ag = arbeidsgivere(treffId)
        val p1 = jobbsøker(treffId, "11111111111")
        val p2 = jobbsøker(treffId, "22222222222")

        møtt(treffId, p1)
        møtt(treffId, p2)
        service.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 15), navIdent)
        service.lagreRomfordeling(
            treffId,
            listOf(RomDto(1, listOf(p1.somString)), RomDto(2, listOf(p2.somString))),
            navIdent,
        )
        interesse(treffId, p1, ag[0])
        interesse(treffId, p2, ag[0])
        service.lagreIntervjufordeling(
            treffId,
            ArbeidsgiverIntervjufordelingDto(ag[0].somString, listOf(p1.somString), listOf(p2.somString)),
            navIdent,
        )
        service.lagreVurdering(
            treffId,
            VurderingDto(
                personTreffId = p1.somString,
                arbeidsgiverTreffId = ag[0].somString,
                vurdering = Vurderingsvalg.AKTUELL,
                notater = listOf("AG_GODT_INNTRYKK", "JS_POSITIV"),
                andregangsintervju = true,
                andregangsintervjuDato = "2026-09-01",
                jobbtilbud = true,
            ),
            navIdent,
        )
        return Scenario(treffId, p1, p2, ag[0], ag[1])
    }

    private data class Scenario(
        val treffId: TreffId,
        val p1: PersonTreffId,
        val p2: PersonTreffId,
        val ag1: ArbeidsgiverTreffId,
        val ag2: ArbeidsgiverTreffId,
    )

    private fun workOpTreff(antallArbeidsgivere: Int = 1): TreffId =
        treff(RekrutteringstreffKategori.WORKOP, antallArbeidsgivere)

    private fun vanligTreff(antallArbeidsgivere: Int = 1): TreffId =
        treff(RekrutteringstreffKategori.REKRUTTERINGSTREFF, antallArbeidsgivere)

    private fun treff(kategori: RekrutteringstreffKategori, antallArbeidsgivere: Int): TreffId {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, kategori = kategori)
        repeat(antallArbeidsgivere) { arbeidsgiver(treffId, "99999999${it + 1}") }
        return treffId
    }

    private fun arbeidsgiver(treffId: TreffId, orgnr: String = "999999991"): ArbeidsgiverTreffId =
        db.leggTilArbeidsgiverMedHendelse(
            LeggTilArbeidsgiver(Orgnr(orgnr), Orgnavn("Testbedrift $orgnr"), emptyList(), null, null, null),
            treffId,
        )

    private fun jobbsøker(treffId: TreffId, fnr: String = "12345678901"): PersonTreffId =
        db.leggTilJobbsøkereMedHendelse(
            listOf(LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Testesen"))),
            treffId,
        ).first()

    private fun arbeidsgivere(treffId: TreffId): List<ArbeidsgiverTreffId> =
        db.dataSource.connection.use { conn ->
            val sql = """
                SELECT a.id::text
                FROM arbeidsgiver a
                JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = a.rekrutteringstreff_id
                WHERE rt.id = ? AND a.status = 'AKTIV'
                ORDER BY a.arbeidsgiver_id
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) ArbeidsgiverTreffId(rs.getString(1)) else null }.toList()
                }
            }
        }

    private fun møtt(treffId: TreffId, person: PersonTreffId) =
        service.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, true), navIdent)

    private fun ikkeMøtt(treffId: TreffId, person: PersonTreffId, bekreft: Boolean = true) =
        service.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, false, bekreft), navIdent)

    private fun interesse(
        treffId: TreffId,
        person: PersonTreffId,
        arbeidsgiver: ArbeidsgiverTreffId,
        interessert: Boolean = true,
    ) = service.settInteresse(
        treffId,
        InteresseRequestDto(person.somString, arbeidsgiver.somString, interessert),
        navIdent,
    )

    private fun antallRader(tabell: String, jobbsøkerId: Long): Int = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM $tabell WHERE jobbsoker_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.executeQuery().use { it.next(); it.getInt(1) }
        }
    }

    private fun jobbsøkerDbId(person: PersonTreffId): Long = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT jobbsoker_id FROM jobbsoker WHERE id = ?").use { stmt ->
            stmt.setObject(1, java.util.UUID.fromString(person.somString))
            stmt.executeQuery().use { it.next(); it.getLong(1) }
        }
    }

    private fun antallTreffgjennomforingsrader(): Int = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM treffgjennomforing").executeQuery().use {
            it.next(); it.getInt(1)
        }
    }

    private fun jobbsøkerhendelser(treffId: TreffId): List<String> =
        db.hentJobbsøkerHendelser(treffId).map { it.hendelsestype.name }

    private fun arbeidsgiverhendelser(treffId: TreffId): List<String> =
        db.hentArbeidsgiverHendelser(treffId).map { it.hendelsestype.name }

    private fun hendelsedata(hendelsestype: String): List<String> = db.dataSource.connection.use { conn ->
        val sql = """
            SELECT hendelse_data::text
            FROM jobbsoker_hendelse
            WHERE hendelsestype = ?
            ORDER BY jobbsoker_hendelse_id
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, hendelsestype)
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) ?: "null" else null }.toList()
            }
        }
    }
}
