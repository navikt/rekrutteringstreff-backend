package no.nav.toi.treffgjennomføring

import no.nav.toi.HendelseWriter
import no.nav.toi.JacksonConfig
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.treffgjennomføring.matching.MatchingRepository
import no.nav.toi.treffgjennomføring.matching.MatchingService
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import no.nav.toi.treffgjennomføring.møteplan.MøteplanService
import no.nav.toi.oppfølging.OppfølgingService
import no.nav.toi.oppfølging.Vurderingsvalg
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.oppmøte.OppmøteHarRegistreringerException
import no.nav.toi.jobbsoker.oppmøte.OppmøteService
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.Oppmøte
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomføring.dto.InteresseRequestDto
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomføring.dto.RomDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import no.nav.toi.treffgjennomføring.dto.VurderingDto
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
class TreffgjennomføringKarakteriseringTest {

    private val db = TestDatabase()
    private val mapper = JacksonConfig.mapper
    private val navIdent = "Z999999"

    private val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
    private val arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
    private val rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
    private val kontekstRepository = TreffkontekstRepository()
    private val faseRepository = FaseRepository()
    private val oppmøteRepository = OppmøteRepository()
    private val møteplanRepository = MøteplanRepository()
    private val matchingRepository = MatchingRepository()
    private val oppfølgingRepository = OppfølgingRepository()
    private val reader = TreffgjennomføringReader(
        faseRepository, oppmøteRepository, møteplanRepository, matchingRepository, oppfølgingRepository,
    )
    private val writer = TreffgjennomføringWriter(db.dataSource, kontekstRepository, faseRepository, reader)
    private val hendelser = HendelseWriter(jobbsøkerRepository, arbeidsgiverRepository, rekrutteringstreffRepository, mapper)

    private val service = TreffgjennomføringService(db.dataSource, kontekstRepository, reader)

    private val møteplanService = MøteplanService(writer, møteplanRepository, oppmøteRepository, faseRepository, hendelser)
    private val matchingService = MatchingService(writer, matchingRepository, oppmøteRepository, faseRepository, hendelser)

    private val oppfølgingService = OppfølgingService(
        writer = writer,
        repository = oppfølgingRepository,
        faseRepository = faseRepository,
        hendelser = hendelser,
    )

    private val oppmøteService = OppmøteService(
        treffgjennomføringWriter = writer,
        oppmøteRepository = oppmøteRepository,
        matchingRepository = matchingRepository,
        møteplanRepository = møteplanRepository,
        oppfølgingRepository = oppfølgingRepository,
        hendelseWriter = hendelser,
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

        val aggregat: TreffgjennomføringDto = service.hent(s.treffId)

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
        assertThat(antallTreffgjennomføringsrader()).isEqualTo(0)
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

        møteplanService.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 15), navIdent)
        assertThat(service.hent(treffId).fase).isEqualTo(TreffgjennomføringFase.ROM)

        interesse(treffId, person, ag)
        assertThat(service.hent(treffId).fase).isEqualTo(TreffgjennomføringFase.INTERESSE)

        matchingService.fordelIntervjuer(treffId, navIdent)
        assertThat(service.hent(treffId).fase).isEqualTo(TreffgjennomføringFase.FORDELING)

        oppfølgingService.lagreVurdering(
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
        assertThat(jobbsøkerhendelser(treffId).filter { it == "REGISTRERT_OPPMØTE" }).hasSize(1)
        assertThat(hendelsedata("REGISTRERT_OPPMØTE").single()).contains("\"deltakernummer\": 1")

        ikkeMøtt(treffId, person)
        assertThat(jobbsøkerhendelser(treffId).filter { it == "REGISTRERT_OPPMØTE_FJERNET" }).hasSize(1)
        assertThat(hendelsedata("REGISTRERT_OPPMØTE_FJERNET").single())
            .contains("\"interesser\"", "\"intervjuplasser\"", "\"vurderinger\"")
    }

    @Test
    fun `interesse endrer bare current state, ingen hendelser skrives`() {
        val treffId = vanligTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)
        val førJobbsøker = jobbsøkerhendelser(treffId).size
        val førArbeidsgiver = arbeidsgiverhendelser(treffId).size

        interesse(treffId, person, ag)

        assertThat(service.hent(treffId).interesser).hasSize(1)
        assertThat(jobbsøkerhendelser(treffId)).hasSize(førJobbsøker)
        assertThat(arbeidsgiverhendelser(treffId)).hasSize(førArbeidsgiver)

        interesse(treffId, person, ag, interessert = false)

        assertThat(service.hent(treffId).interesser).isEmpty()
        assertThat(jobbsøkerhendelser(treffId)).hasSize(førJobbsøker)
        assertThat(arbeidsgiverhendelser(treffId)).hasSize(førArbeidsgiver)
    }

    @Test
    fun `uendret interesse er idempotent`() {
        val treffId = vanligTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)

        interesse(treffId, person, ag)
        interesse(treffId, person, ag)

        assertThat(service.hent(treffId).interesser).hasSize(1)
    }

    @Test
    fun `vurdering skriver én hendelse per endret felt`() {
        val treffId = vanligTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)

        oppfølgingService.lagreVurdering(
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

        oppfølgingService.lagreVurdering(
            treffId,
            VurderingDto(person.somString, ag.somString, Vurderingsvalg.AKTUELL),
            navIdent,
        )
        assertThat(service.hent(treffId).vurderinger).hasSize(1)

        oppfølgingService.lagreVurdering(treffId, VurderingDto(person.somString, ag.somString), navIdent)

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
        matchingService.fordelIntervjuer(treffId, navIdent)

        interesse(treffId, p2, ag)

        val fordeling = service.hent(treffId).intervjufordelinger.single()
        assertThat(fordeling.inkludertePersonTreffIder).contains(p2.somString)

        interesse(treffId, p2, ag, interessert = false)

        val etter = service.hent(treffId).intervjufordelinger.single()
        assertThat(etter.inkludertePersonTreffIder).doesNotContain(p2.somString)
        assertThat(etter.ekskludertePersonTreffIder).doesNotContain(p2.somString)
    }

    @Test
    fun `oppfølgingen overlever at interessen fjernes`() {
        val treffId = workOpTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)
        interesse(treffId, person, ag)
        matchingService.fordelIntervjuer(treffId, navIdent)
        oppfølgingService.lagreVurdering(
            treffId,
            VurderingDto(
                personTreffId = person.somString,
                arbeidsgiverTreffId = ag.somString,
                vurdering = Vurderingsvalg.AKTUELL,
                notater = listOf("AG_GODT_INNTRYKK", "JS_POSITIV"),
                andregangsintervju = true,
                andregangsintervjuDato = "2026-09-01",
                jobbtilbud = true,
            ),
            navIdent,
        )

        interesse(treffId, person, ag, interessert = false)

        val etter = service.hent(treffId)
        assertThat(etter.interesser).isEmpty()
        assertThat(etter.intervjufordelinger.flatMap { it.inkludertePersonTreffIder + it.ekskludertePersonTreffIder })
            .doesNotContain(person.somString)

        val vurdering = etter.vurderinger.single()
        assertThat(vurdering.personTreffId).isEqualTo(person.somString)
        assertThat(vurdering.arbeidsgiverTreffId).isEqualTo(ag.somString)
        assertThat(vurdering.vurdering).isEqualTo(Vurderingsvalg.AKTUELL)
        assertThat(vurdering.notater).containsExactlyInAnyOrder("AG_GODT_INNTRYKK", "JS_POSITIV")
        assertThat(vurdering.andregangsintervju).isTrue()
        assertThat(vurdering.andregangsintervjuDato).isEqualTo("2026-09-01")
        assertThat(vurdering.jobbtilbud).isTrue()
        assertThat(antallRader("vurdering", jobbsøkerDbId(person))).isEqualTo(1)
    }

    @Test
    fun `oppfølgingen overlever at jobbsøkeren tas ut av intervjufordelingen`() {
        val treffId = workOpTreff()
        val ag = arbeidsgivere(treffId).single()
        val person = jobbsøker(treffId)
        møtt(treffId, person)
        interesse(treffId, person, ag)
        matchingService.fordelIntervjuer(treffId, navIdent)
        oppfølgingService.lagreVurdering(
            treffId,
            VurderingDto(
                personTreffId = person.somString,
                arbeidsgiverTreffId = ag.somString,
                notater = listOf("AG_VIL_MØTE_FLERE"),
            ),
            navIdent,
        )

        matchingService.lagreIntervjufordeling(
            treffId,
            ArbeidsgiverIntervjufordelingDto(
                arbeidsgiverTreffId = ag.somString,
                inkludertePersonTreffIder = emptyList(),
                ekskludertePersonTreffIder = listOf(person.somString),
            ),
        )

        val vurdering = service.hent(treffId).vurderinger.single()
        assertThat(vurdering.notater).containsExactly("AG_VIL_MØTE_FLERE")
    }

    @Test
    fun `rom normaliseres etter oppmøte ved lesing`() {
        val treffId = workOpTreff()
        val p1 = jobbsøker(treffId, "11111111111")
        val p2 = jobbsøker(treffId, "22222222222")
        møtt(treffId, p1)
        møtt(treffId, p2)
        møteplanService.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 15), navIdent)
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

        assertThatThrownBy { møteplanService.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 15), navIdent) }
            .hasMessageContaining("Steget finnes bare på treff av kategorien WORKOP")
    }

    @Test
    fun `oppmøtekolonnen settes ved registrering og angring`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)
        assertThat(oppmøtekolonne(person)).isNull()

        møtt(treffId, person)
        assertThat(oppmøtekolonne(person)).isEqualTo(Oppmøte.REGISTRERT_OPPMØTE)

        ikkeMøtt(treffId, person)
        assertThat(oppmøtekolonne(person)).isEqualTo(Oppmøte.REGISTRERT_OPPMØTE_FJERNET)

        møtt(treffId, person)
        assertThat(oppmøtekolonne(person)).isEqualTo(Oppmøte.REGISTRERT_OPPMØTE)
    }

    @Test
    fun `kolonnen og hendelsene gir samme svar for alle jobbsøkere`() {
        val s = fulltScenario()
        ikkeMøtt(s.treffId, s.p2)

        assertThat(antallAvvikMellomKolonneOgHendelser()).isZero()
    }

    @Test
    fun `jobbsøker uten oppmøteregistrering har null i kolonnen og er ikke fremmøtt`() {
        val treffId = workOpTreff()
        val registrert = jobbsøker(treffId, "11111111111")
        val urørt = jobbsøker(treffId, "22222222222")

        møtt(treffId, registrert)

        assertThat(oppmøtekolonne(urørt)).isNull()
        assertThat(service.hent(treffId).oppmøte).containsExactly(registrert.somString)
    }

    private fun fulltScenario(): Scenario {
        val treffId = workOpTreff(antallArbeidsgivere = 2)
        val ag = arbeidsgivere(treffId)
        val p1 = jobbsøker(treffId, "11111111111")
        val p2 = jobbsøker(treffId, "22222222222")

        møtt(treffId, p1)
        møtt(treffId, p2)
        møteplanService.lagreMøteoppsett(treffId, MøteoppsettRequestDto("09:00", 15), navIdent)
        møteplanService.lagreRomfordeling(
            treffId,
            listOf(RomDto(1, listOf(p1.somString)), RomDto(2, listOf(p2.somString))),
        )
        interesse(treffId, p1, ag[0])
        interesse(treffId, p2, ag[0])
        matchingService.lagreIntervjufordeling(
            treffId,
            ArbeidsgiverIntervjufordelingDto(ag[0].somString, listOf(p1.somString), listOf(p2.somString)),
        )
        oppfølgingService.lagreVurdering(
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
        oppmøteService.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, true), navIdent)

    private fun ikkeMøtt(treffId: TreffId, person: PersonTreffId, bekreft: Boolean = true) =
        oppmøteService.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, false, bekreft), navIdent)

    private fun interesse(
        treffId: TreffId,
        person: PersonTreffId,
        arbeidsgiver: ArbeidsgiverTreffId,
        interessert: Boolean = true,
    ) = matchingService.settInteresse(
        treffId,
        InteresseRequestDto(person.somString, arbeidsgiver.somString, interessert),
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

    private fun antallTreffgjennomføringsrader(): Int = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT COUNT(*) FROM treffgjennomforing").executeQuery().use {
            it.next(); it.getInt(1)
        }
    }

    private fun oppmøtekolonne(person: PersonTreffId): Oppmøte? = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT oppmote FROM jobbsoker WHERE id = ?").use { stmt ->
            stmt.setObject(1, java.util.UUID.fromString(person.somString))
            stmt.executeQuery().use { rs ->
                rs.next()
                Oppmøte.fraDatabase(rs.getString(1))
            }
        }
    }

    private fun antallAvvikMellomKolonneOgHendelser(): Int = db.dataSource.connection.use { conn ->
        val sql = """
            SELECT COUNT(*)
            FROM jobbsoker j
            LEFT JOIN LATERAL (
                SELECT jh.hendelsestype
                FROM jobbsoker_hendelse jh
                WHERE jh.jobbsoker_id = j.jobbsoker_id
                  AND jh.hendelsestype IN ('REGISTRERT_OPPMØTE', 'REGISTRERT_OPPMØTE_FJERNET')
                ORDER BY jh.tidspunkt DESC, jh.jobbsoker_hendelse_id DESC
                LIMIT 1
            ) h ON TRUE
            WHERE (h.hendelsestype = 'REGISTRERT_OPPMØTE') IS DISTINCT FROM (j.oppmote = ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, Oppmøte.REGISTRERT_OPPMØTE.name)
            stmt.executeQuery().use { it.next(); it.getInt(1) }
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
