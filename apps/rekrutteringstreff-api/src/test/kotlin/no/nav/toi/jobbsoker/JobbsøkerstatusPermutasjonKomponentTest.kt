package no.nav.toi.jobbsoker

import io.mockk.every
import io.mockk.mockk
import no.nav.toi.ApplicationContext
import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST
import no.nav.toi.JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT
import no.nav.toi.JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST
import no.nav.toi.JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT
import no.nav.toi.JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.formidling.KandidatKlient
import no.nav.toi.formidling.OpprettFormidlingStillingRespons
import no.nav.toi.formidling.StillingKlient
import no.nav.toi.formidling.dto.ArbeidsgiverDto
import no.nav.toi.formidling.dto.OpprettFormidlingDto
import no.nav.toi.formidling.dto.StillingDto
import no.nav.toi.rekrutteringstreff.Endringsfelttype
import no.nav.toi.rekrutteringstreff.Rekrutteringstreffendringer
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.leggTil
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import no.nav.toi.jobbsoker.JobbsøkerStatus.*
import no.nav.toi.jobbsoker.sok.JobbsøkerSøkRequest

/**
 * Statusoverganger for jobbsøkere. Hver permutasjon kjøres gjennom de samme tjenestene som
 * API-et bruker, mot ekte database, og sjekker status, gjeldende svar og om personen står
 * som møtt i oppmøtelisten.
 *
 * Avklarte regler:
 * - Eier kan svare for en jobbsøker uten invitasjon, og jobbsøkeren kan registreres som møtt
 *   uten svar eller invitasjon.
 * - Svar etter oppmøte endrer svaret, men statusen er fortsatt møtt opp. Fjernes oppmøtet,
 *   blir statusen det nyeste svaret.
 * - «Fått jobb» står til siste aktive formidling er angret.
 * - «Fått jobb» alene regnes ikke som møtt. Oppmøte registreres uavhengig av formidling.
 * - Den som har svart eller møtt uten invitasjon, inviteres ikke senere (invitasjon sender sms).
 * - Når treffet avlyses, får den som har svart ja en SVART_JA_TREFF_AVLYST, også etter oppmøte
 *   eller fått jobb. Den som bare er invitert, får IKKE_SVART_TREFF_AVLYST. Andre får ingen.
 * - Når treffet fullføres, får invitert jobbsøker som har møtt opp, fått jobb eller svart ja
 *   SVART_JA_TREFF_FULLFØRT, uansett svar. Den som bare er invitert, får IKKE_SVART_TREFF_FULLFØRT.
 *   Andre får ingen.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobbsøkerstatusPermutasjonKomponentTest {

    private val db = TestDatabase()
    private val eier = "A100001"
    private val fnr = "12345678901"
    private val orgnrA = "999999991"
    private val orgnrB = "999999992"

    private val stillingKlient = mockk<StillingKlient>(relaxed = true)
    private val kandidatKlient = mockk<KandidatKlient>(relaxed = true)
    private lateinit var ctx: ApplicationContext

    @BeforeAll
    fun setUp() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        ctx = ApplicationContext(
            TestInfrastructureContext(
                dataSource = db.dataSource,
                stillingKlient = stillingKlient,
                kandidatKlient = kandidatKlient,
            )
        )
        every { stillingKlient.opprettFormidlingStillingOgKandidatliste(any(), any()) } answers {
            OpprettFormidlingStillingRespons(stillingsId = UUID.randomUUID(), kandidatlisteId = UUID.randomUUID())
        }
    }

    @AfterEach
    fun rydd() = db.slettAlt()

    sealed class Steg(private val navn: String) {
        final override fun toString() = navn
        data object Inviter : Steg("Inviter")
        data object JaEier : Steg("Ja (eier)")
        data object NeiEier : Steg("Nei (eier)")
        data object FjernSvar : Steg("Fjern svar")
        data object JaBorger : Steg("Ja (borger)")
        data object NeiBorger : Steg("Nei (borger)")
        data object Møtt : Steg("Møtt")
        data object FjernMøtt : Steg("Fjern møtt")
        data object FormidleA : Steg("Fått jobb A")
        data object FormidleB : Steg("Fått jobb B")
        data object AngreA : Steg("Angre A")
        data object AngreB : Steg("Angre B")
        data object Slett : Steg("Slett")
        data object LeggTilIgjen : Steg("Legg til igjen")
    }

    data class Utfall(val status: JobbsøkerStatus, val svar: Boolean?, val iOppmøteliste: Boolean)

    data class Permutasjon(val nr: Int, val steg: List<Steg>, val forventet: Utfall) {
        override fun toString() = "#$nr ${steg.joinToString(" → ").ifEmpty { "(kun lagt til)" }}"
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("permutasjoner")
    fun `permutasjon gir forventet status, svar og oppmøte`(permutasjon: Permutasjon) {
        val treff = opprettTreff()
        var person = leggTil(treff)
        val formidlinger = mutableMapOf<String, UUID>()
        val spor = mutableListOf("LAGT_TIL")

        permutasjon.steg.forEach { steg ->
            val feil = runCatching { person = utfør(steg, treff, person, formidlinger) }.exceptionOrNull()
            spor += db.hentJobbsøkerStatus(person)!!.name + (feil?.let { " (avvist: ${it.message ?: it::class.simpleName})" } ?: "")
        }

        val faktisk = utfall(treff, person)
        println("PERMUTASJON $permutasjon | spor: ${spor.joinToString(" → ")} | svar: ${faktisk.svar} | møtt-avkrysset: ${faktisk.iOppmøteliste} | hendelser: ${hendelser(person)}")
        assertThat(faktisk).isEqualTo(permutasjon.forventet)
        if (faktisk.status != SLETTET) {
            val fraSøk = ctx.jobbsøkerService.søkJobbsøkere(treff, JobbsøkerSøkRequest()).jobbsøkere.single()
            assertThat(fraSøk.status).isEqualTo(faktisk.status)
        }
    }

    data class Avslutningsforløp(
        val nr: Int,
        val steg: List<Steg>,
        val vedAvlysning: JobbsøkerHendelsestype?,
        val vedFullføring: JobbsøkerHendelsestype?,
    ) {
        override fun toString() =
            "#$nr ${steg.joinToString(" → ").ifEmpty { "(kun lagt til)" }} gir " +
                "${vedAvlysning ?: "ingen hendelse"} / ${vedFullføring ?: "ingen hendelse"}"
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("avslutningsforløp")
    fun `avlysning, fullføring og endring gir forventet hendelse`(forløp: Avslutningsforløp) {
        val hendelserVedAvlysning = nyeHendelserEtter(forløp.steg) { treff -> ctx.rekrutteringstreffService.avlys(treff, eier) }
        val hendelserVedFullføring = nyeHendelserEtter(forløp.steg) { treff ->
            db.endreTilTidTilPassert(treff, eier)
            ctx.rekrutteringstreffService.fullfør(treff, eier)
        }
        val hendelserVedEndring = nyeHendelserEtter(forløp.steg) { treff ->
            ctx.rekrutteringstreffService.registrerEndring(treff, Rekrutteringstreffendringer(setOf(Endringsfelttype.NAVN)), eier)
        }

        assertThat(hendelserVedAvlysning).`as`("hendelser ved avlysning").isEqualTo(listOfNotNull(forløp.vedAvlysning))
        assertThat(hendelserVedFullføring).`as`("hendelser ved fullføring").isEqualTo(listOfNotNull(forløp.vedFullføring))
        assertThat(TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON in hendelserVedEndring)
            .`as`("varsel om endring går til den som har svart ja")
            .isEqualTo(forløp.vedAvlysning == SVART_JA_TREFF_AVLYST)
    }

    @org.junit.jupiter.api.Test
    fun `svar på slettet jobbsøker avvises, og ny innlegging gjenbruker personTreffId`() {
        val treff = opprettTreff()
        val person = leggTil(treff)
        ctx.jobbsøkerService.markerSlettet(person, treff, eier)

        assertThat(runCatching { ctx.jobbsøkerService.svarPåVegneAvJobbsøker(person, treff, eier, true) }.exceptionOrNull())
            .isInstanceOf(no.nav.toi.exception.JobbsøkerIkkeFunnetException::class.java)
        assertThat(hendelser(person)).containsExactly("OPPRETTET", "SLETTET")

        val igjen = leggTil(treff)
        assertThat(igjen).isEqualTo(person)
        assertThat(hendelser(igjen)).containsExactly("OPPRETTET", "SLETTET", "OPPRETTET")
        assertThat(db.hentJobbsøkerStatus(igjen)).isEqualTo(LAGT_TIL)
    }

    @org.junit.jupiter.api.Test
    fun `formidling avvises hvis jobbsøkeren slettes mens stilling og kandidatliste opprettes`() {
        val treff = opprettTreff()
        val person = leggTil(treff)
        every { stillingKlient.opprettFormidlingStillingOgKandidatliste(any(), any()) } answers {
            assertThat(ctx.jobbsøkerService.markerSlettet(person, treff, eier)).isEqualTo(MarkerSlettetResultat.OK)
            OpprettFormidlingStillingRespons(stillingsId = UUID.randomUUID(), kandidatlisteId = UUID.randomUUID())
        }

        try {
            assertThat(runCatching { formidle(treff, orgnrA) }.exceptionOrNull())
                .isInstanceOf(no.nav.toi.formidling.JobbsøkerIkkeFunnetPåTreffException::class.java)
        } finally {
            every { stillingKlient.opprettFormidlingStillingOgKandidatliste(any(), any()) } answers {
                OpprettFormidlingStillingRespons(stillingsId = UUID.randomUUID(), kandidatlisteId = UUID.randomUUID())
            }
        }

        assertThat(db.hentJobbsøkerStatus(person)).isEqualTo(SLETTET)
        assertThat(hendelser(person)).containsExactly("OPPRETTET", "SLETTET")
    }

    private fun opprettTreff(): TreffId {
        val treff = db.opprettRekrutteringstreffIDatabase(navIdent = eier)
        ctx.eierRepository.leggTil(treff, eier, "0315")
        listOf(orgnrA, orgnrB).forEach {
            db.leggTilArbeidsgiverMedHendelse(
                LeggTilArbeidsgiver(Orgnr(it), Orgnavn("Testbedrift $it"), emptyList(), null, null, null), treff,
            )
        }
        return treff
    }

    private fun utfør(steg: Steg, treff: TreffId, person: PersonTreffId, formidlinger: MutableMap<String, UUID>): PersonTreffId {
        when (steg) {
            Steg.Inviter -> ctx.jobbsøkerService.inviter(listOf(person), treff, eier)
            Steg.JaEier -> ctx.jobbsøkerService.svarPåVegneAvJobbsøker(person, treff, eier, true)
            Steg.NeiEier -> ctx.jobbsøkerService.svarPåVegneAvJobbsøker(person, treff, eier, false)
            Steg.FjernSvar -> ctx.jobbsøkerService.svarPåVegneAvJobbsøker(person, treff, eier, null)
            Steg.JaBorger -> ctx.jobbsøkerService.svarJaTilInvitasjon(Fødselsnummer(fnr), treff, fnr)
            Steg.NeiBorger -> ctx.jobbsøkerService.svarNeiTilInvitasjon(Fødselsnummer(fnr), treff, fnr)
            Steg.Møtt -> oppmøte(treff, person, true)
            Steg.FjernMøtt -> oppmøte(treff, person, false)
            Steg.FormidleA -> formidlinger[orgnrA] = formidle(treff, orgnrA)
            Steg.FormidleB -> formidlinger[orgnrB] = formidle(treff, orgnrB)
            Steg.AngreA -> angre(treff, formidlinger.getValue(orgnrA))
            Steg.AngreB -> angre(treff, formidlinger.getValue(orgnrB))
            Steg.Slett -> ctx.jobbsøkerService.markerSlettet(person, treff, eier)
                .also { if (it != MarkerSlettetResultat.OK) error(it.name) }
            Steg.LeggTilIgjen -> return leggTil(treff)
        }
        return person
    }

    private fun nyeHendelserEtter(steg: List<Steg>, handling: (TreffId) -> Unit): List<JobbsøkerHendelsestype> {
        val treff = opprettTreff()
        db.publiser(treff, eier)
        var person = leggTil(treff)
        val formidlinger = mutableMapOf<String, UUID>()
        steg.forEach { person = utfør(it, treff, person, formidlinger) }

        val hendelserFør = hendelsestyper(person)
        handling(treff)
        return hendelsestyper(person).drop(hendelserFør.size)
    }

    private fun leggTil(treff: TreffId): PersonTreffId {
        ctx.jobbsøkerService.leggTilJobbsøkere(
            listOf(LeggTilJobbsøker(Fødselsnummer(fnr), Fornavn("Test"), Etternavn("Testesen"))), treff, eier,
        )
        return ctx.jobbsøkerService.hentJobbsøker(treff, Fødselsnummer(fnr))!!.personTreffId
    }

    private fun oppmøte(treff: TreffId, person: PersonTreffId, møtt: Boolean) {
        ctx.oppmøteService.oppdaterOppmøte(treff, OppmøteRequestDto(person.somString, møtt), eier)
    }

    private fun formidle(treff: TreffId, orgnr: String): UUID =
        ctx.formidlingService.opprettFormidling(
            treff,
            OpprettFormidlingDto(
                kontornummer = "1234",
                kontornavn = "Nav Test",
                orgnr = orgnr,
                fødselsnumre = listOf(fnr),
                stilling = StillingDto(
                    employer = ArbeidsgiverDto(
                        name = "Testbedrift", orgnr = orgnr, publicName = "Testbedrift",
                        contactList = emptyList(), locationList = emptyList(), properties = emptyMap(),
                    ),
                ),
                yrkestittel = "Testyrke",
                janzzKonseptId = "1",
            ),
            eier,
            "token",
        ).single().id

    private fun angre(treff: TreffId, formidlingId: UUID) =
        ctx.formidlingService.slett(treff, formidlingId, eier, "token", "1234")

    private fun utfall(treff: TreffId, person: PersonTreffId): Utfall {
        val jobbsøker = ctx.jobbsøkerService.hentJobbsøker(treff, Fødselsnummer(fnr), inkluderUsynlige = true)
        val iOppmøteliste = db.dataSource.connection.use { conn ->
            val treffDbId = conn.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?").use {
                it.setObject(1, treff.somUuid)
                it.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
            person in ctx.oppmøteRepository.hentFremmøtteJobbsøkere(conn, treffDbId)
        }
        return Utfall(db.hentJobbsøkerStatus(person)!!, jobbsøker?.gjeldendeSvar(), iOppmøteliste)
    }

    private fun hendelsestyper(person: PersonTreffId): List<JobbsøkerHendelsestype> = db.dataSource.connection.use { conn ->
        ctx.jobbsøkerRepository.hentHendelsestyper(conn, person)
    }

    private fun hendelser(person: PersonTreffId): List<String> = hendelsestyper(person).map { it.name }

    fun permutasjoner(): List<Permutasjon> {
        var nr = 0
        fun p(vararg steg: Steg, status: JobbsøkerStatus, svar: Boolean? = null) =
            Permutasjon(++nr, steg.toList(), Utfall(status, svar, steg.lastOrNull { it == Steg.Møtt || it == Steg.FjernMøtt } == Steg.Møtt))
        val i = Steg.Inviter
        val ja = Steg.JaEier
        val nei = Steg.NeiEier
        val m = Steg.Møtt
        val fm = Steg.FjernMøtt
        val a = Steg.FormidleA
        val b = Steg.FormidleB
        val aa = Steg.AngreA
        val ab = Steg.AngreB
        return listOf(
            // Grunnflyt
            p(status = LAGT_TIL),
            p(i, status = INVITERT),
            p(i, ja, status = SVART_JA, svar = true),
            p(i, nei, status = SVART_NEI, svar = false),
            p(i, Steg.JaBorger, status = SVART_JA, svar = true),
            p(i, Steg.NeiBorger, status = SVART_NEI, svar = false),
            p(i, ja, Steg.FjernSvar, status = INVITERT),
            p(i, ja, nei, status = SVART_NEI, svar = false),
            p(i, Steg.JaBorger, Steg.NeiEier, status = SVART_NEI, svar = false),
            p(i, ja, i, status = SVART_JA, svar = true),
            // Svar uten invitasjon
            p(ja, status = SVART_JA, svar = true),
            p(Steg.JaBorger, status = SVART_JA, svar = true),
            p(ja, Steg.FjernSvar, status = LAGT_TIL),
            p(ja, i, status = SVART_JA, svar = true), // invitasjonen hoppes over
            p(ja, m, nei, fm, status = SVART_NEI, svar = false),
            p(m, nei, status = MØTT_OPP, svar = false),
            p(m, nei, fm, status = SVART_NEI, svar = false),
            p(m, ja, fm, status = SVART_JA, svar = true),
            p(ja, a, aa, status = SVART_JA, svar = true),
            // Møtt opp og svar
            p(i, ja, m, status = MØTT_OPP, svar = true),
            p(i, ja, m, nei, status = MØTT_OPP, svar = false),
            p(i, ja, m, nei, fm, status = SVART_NEI, svar = false),
            p(i, ja, m, Steg.NeiBorger, fm, status = SVART_NEI, svar = false),
            p(i, ja, m, fm, status = SVART_JA, svar = true),
            p(i, nei, m, status = MØTT_OPP, svar = false),
            p(i, nei, m, fm, status = SVART_NEI, svar = false),
            p(i, nei, m, ja, fm, status = SVART_JA, svar = true),
            p(i, m, status = MØTT_OPP),
            p(i, m, fm, status = INVITERT),
            p(i, m, ja, fm, status = SVART_JA, svar = true),
            p(i, ja, m, Steg.FjernSvar, status = MØTT_OPP),
            p(i, ja, m, Steg.FjernSvar, fm, status = INVITERT),
            p(i, ja, m, nei, ja, fm, status = SVART_JA, svar = true),
            p(i, ja, m, nei, fm, m, status = MØTT_OPP, svar = false),
            p(i, ja, m, fm, nei, m, fm, status = SVART_NEI, svar = false),
            p(m, status = MØTT_OPP),
            p(m, fm, status = LAGT_TIL),
            p(m, i, status = MØTT_OPP), // invitasjonen hoppes over
            p(m, fm, i, status = INVITERT),
            // Fått jobb
            p(a, status = FÅTT_JOBB),
            p(a, aa, status = LAGT_TIL),
            p(i, ja, a, status = FÅTT_JOBB, svar = true),
            p(i, ja, a, aa, status = SVART_JA, svar = true),
            p(i, nei, a, aa, status = SVART_NEI, svar = false),
            p(i, ja, m, a, status = FÅTT_JOBB, svar = true),
            p(i, ja, m, a, aa, status = MØTT_OPP, svar = true),
            p(i, ja, m, a, nei, aa, status = MØTT_OPP, svar = false),
            p(i, ja, m, a, nei, aa, fm, status = SVART_NEI, svar = false),
            p(i, ja, a, nei, status = FÅTT_JOBB, svar = false),
            p(i, ja, a, nei, aa, status = SVART_NEI, svar = false),
            p(i, ja, a, Steg.FjernSvar, aa, status = INVITERT),
            p(i, ja, m, a, fm, status = FÅTT_JOBB, svar = true),
            p(i, ja, m, a, fm, aa, status = SVART_JA, svar = true),
            p(i, ja, a, m, status = FÅTT_JOBB, svar = true),
            p(i, ja, a, m, aa, status = MØTT_OPP, svar = true),
            p(i, ja, a, m, fm, aa, status = SVART_JA, svar = true),
            p(i, ja, a, fm, status = FÅTT_JOBB, svar = true),
            p(i, ja, a, aa, a, status = FÅTT_JOBB, svar = true),
            // To formidlinger
            p(i, ja, a, b, status = FÅTT_JOBB, svar = true),
            p(i, ja, a, b, aa, status = FÅTT_JOBB, svar = true),
            p(i, ja, a, b, ab, status = FÅTT_JOBB, svar = true),
            p(i, ja, a, b, aa, ab, status = SVART_JA, svar = true),
            p(i, ja, m, a, b, ab, status = FÅTT_JOBB, svar = true),
            p(i, ja, m, a, b, ab, aa, status = MØTT_OPP, svar = true),
            p(i, ja, a, b, aa, nei, ab, status = SVART_NEI, svar = false),
            p(i, ja, a, b, aa, a, ab, status = FÅTT_JOBB, svar = true),
            // Sletting
            p(Steg.Slett, status = SLETTET),
            p(Steg.Slett, Steg.LeggTilIgjen, status = LAGT_TIL),
            p(i, Steg.Slett, status = INVITERT),
            p(m, Steg.Slett, status = MØTT_OPP),
            p(a, Steg.Slett, status = FÅTT_JOBB),
            p(a, aa, Steg.Slett, status = SLETTET),
            // Svar på en slettet jobbsøker avvises, så slettet forblir en endestasjon.
            p(Steg.Slett, ja, status = SLETTET),
            // Lagt til på nytt etter sletting: gammel historikk påvirker ikke statusen
            p(m, fm, Steg.Slett, Steg.LeggTilIgjen, status = LAGT_TIL),
            p(ja, Steg.FjernSvar, Steg.Slett, Steg.LeggTilIgjen, status = LAGT_TIL),
            p(a, aa, Steg.Slett, Steg.LeggTilIgjen, i, ja, status = SVART_JA, svar = true),
            p(m, fm, Steg.Slett, Steg.LeggTilIgjen, m, status = MØTT_OPP),
        )
    }

    fun avslutningsforløp(): List<Avslutningsforløp> {
        var nr = 0
        fun f(vararg steg: Steg, avlysning: JobbsøkerHendelsestype?, fullføring: JobbsøkerHendelsestype?) =
            Avslutningsforløp(++nr, steg.toList(), vedAvlysning = avlysning, vedFullføring = fullføring)
        val i = Steg.Inviter
        val ja = Steg.JaEier
        val nei = Steg.NeiEier
        val m = Steg.Møtt
        return listOf(
            f(avlysning = null, fullføring = null),
            f(i, avlysning = IKKE_SVART_TREFF_AVLYST, fullføring = IKKE_SVART_TREFF_FULLFØRT),
            f(i, ja, avlysning = SVART_JA_TREFF_AVLYST, fullføring = SVART_JA_TREFF_FULLFØRT),
            f(i, ja, Steg.FjernSvar, avlysning = IKKE_SVART_TREFF_AVLYST, fullføring = IKKE_SVART_TREFF_FULLFØRT),
            f(i, nei, avlysning = null, fullføring = null),
            // Ved avlysning avgjør svaret for den som har møtt opp eller fått jobb.
            // Ved fullføring får de aktivitetskortet fullført uansett svar.
            f(i, ja, m, avlysning = SVART_JA_TREFF_AVLYST, fullføring = SVART_JA_TREFF_FULLFØRT),
            f(i, ja, Steg.FormidleA, avlysning = SVART_JA_TREFF_AVLYST, fullføring = SVART_JA_TREFF_FULLFØRT),
            f(i, ja, m, nei, avlysning = null, fullføring = SVART_JA_TREFF_FULLFØRT),
            f(i, nei, m, avlysning = null, fullføring = SVART_JA_TREFF_FULLFØRT),
            f(i, m, avlysning = null, fullføring = SVART_JA_TREFF_FULLFØRT),
            f(i, m, Steg.FjernMøtt, avlysning = IKKE_SVART_TREFF_AVLYST, fullføring = IKKE_SVART_TREFF_FULLFØRT),
            f(i, Steg.FormidleA, avlysning = null, fullføring = SVART_JA_TREFF_FULLFØRT),
            // Uten invitasjon har personen ikke aktivitetskort, men den som har svart ja, varsles om avlysningen
            f(ja, avlysning = SVART_JA_TREFF_AVLYST, fullføring = null),
            f(m, avlysning = null, fullføring = null),
            f(Steg.Slett, avlysning = null, fullføring = null),
        )
    }
}
