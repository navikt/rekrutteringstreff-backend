package no.nav.toi.jobbsoker

import no.nav.toi.ApplicationContext
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.exception.UlovligOppdateringException
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Oppmøte låser treffet, mens svar låser jobbsøkeren. Begge skriver en hendelse og utleder
 * statusen fra hele loggen. Testene kjører de to samtidig på samme person, med samme
 * isolasjonsnivå som produksjonspoolen (`REPEATABLE READ`), og krever at begge kallene lykkes
 * og at den lagrede statusen er den samme som om loggen ble lest på nytt.
 *
 * Avlys og fullfør velger én hendelse per jobbsøker ut fra statusen. Testene for dem krever at
 * valget stemmer med hendelsene som ligger før i loggen, også når jobbsøkerne svarer samtidig.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobbsøkerstatusSamtidighetTest {

    private val db = TestDatabase()
    private val dataSourceSomIProduksjon = object : DataSource by db.dataSource {
        override fun getConnection(): Connection = db.dataSource.connection.apply {
            transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        }
    }
    private val ctx = ApplicationContext(TestInfrastructureContext(dataSource = dataSourceSomIProduksjon))
    private val navIdent = "Z999999"
    private val antallRunder = 15
    private val antallTreff = 5
    private val jobbsøkerePerTreff = 4
    private val avlysningshendelser = setOf(
        JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST,
        JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST,
    )
    private val fullføringshendelser = setOf(
        JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT,
        JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT,
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
    fun `svar nei samtidig med fjernet oppmøte gir status fra hele loggen`() {
        val treffId = workOpTreff()
        val personer = (1..antallRunder).map { jobbsøker(treffId, it) }
        personer.forEach { person ->
            svar(person, true)
            oppmøte(treffId, person, true)
        }

        val feil = personer.flatMap { person ->
            samtidig({ svar(person, false) }, { oppmøte(treffId, person, false) })
        }

        assertThat(feil).isEmpty()
        personer.forEach { person ->
            assertThat(lagretStatus(person)).isEqualTo(utledetStatus(person))
            assertThat(lagretStatus(person)).isEqualTo(JobbsøkerStatus.SVART_NEI)
        }
    }

    @Test
    fun `svar ja samtidig med registrert oppmøte gir status fra hele loggen`() {
        val treffId = workOpTreff()
        val personer = (1..antallRunder).map { jobbsøker(treffId, it) }

        val feil = personer.flatMap { person ->
            samtidig({ svar(person, true) }, { oppmøte(treffId, person, true) })
        }

        assertThat(feil).isEmpty()
        personer.forEach { person ->
            assertThat(lagretStatus(person)).isEqualTo(utledetStatus(person))
            assertThat(lagretStatus(person)).isEqualTo(JobbsøkerStatus.MØTT_OPP)
        }
    }

    @Test
    fun `avlys samtidig med svar ja velger hendelse ut fra svaret som kom først`() {
        repeat(antallTreff) { runde ->
            val (treffId, personer) = publisertTreffMedInviterte(runde)

            val feil = samtidig(
                { ctx.rekrutteringstreffService.avlys(treffId, navIdent) },
                *personer.map { person -> { svar(person, true) } }.toTypedArray(),
            )

            assertThat(feil).isEmpty()
            personer.forEach { person ->
                assertThat(lagretStatus(person)).isEqualTo(utledetStatus(person))
                assertTreffslutthendelseStemmerMedLoggenFør(person, avslutninger = avlysningshendelser) { før ->
                    Jobbsøkerstatusregler.hendelseNårTreffetAvlyses(
                        Jobbsøkerstatusregler.utledStatus(før),
                        Jobbsøkerstatusregler.gjeldendeSvar(før) == true,
                    )
                }
            }
        }
    }

    @Test
    fun `fullfør samtidig med svar ja velger hendelse ut fra svaret som kom først`() {
        repeat(antallTreff) { runde ->
            val (treffId, personer) = publisertTreffMedInviterte(runde)
            db.endreTilTidTilPassert(treffId, navIdent)

            val feil = samtidig(
                { ctx.rekrutteringstreffService.fullfør(treffId, navIdent) },
                *personer.map { person -> { svar(person, true) } }.toTypedArray(),
            )

            assertThat(feil).isEmpty()
            personer.forEach { person ->
                assertThat(lagretStatus(person)).isEqualTo(utledetStatus(person))
                assertTreffslutthendelseStemmerMedLoggenFør(person, avslutninger = fullføringshendelser) { før ->
                    Jobbsøkerstatusregler.hendelseNårTreffetFullføres(
                        Jobbsøkerstatusregler.utledStatus(før),
                        JobbsøkerHendelsestype.INVITERT in før,
                    )
                }
            }
        }
    }

    @Test
    fun `to samtidige avlysninger gir én avlysning`() {
        repeat(antallTreff) { runde ->
            val (treffId, personer) = publisertTreffMedInviterte(runde)

            val feil = samtidig(
                { ctx.rekrutteringstreffService.avlys(treffId, navIdent) },
                { ctx.rekrutteringstreffService.avlys(treffId, navIdent) },
            )

            assertThat(feil).hasSize(1)
            assertThat(feil.single()).isInstanceOf(UlovligOppdateringException::class.java)
            personer.forEach { person ->
                assertThat(hendelsestyper(person).filter { it in avlysningshendelser }).hasSize(1)
            }
        }
    }

    private fun publisertTreffMedInviterte(runde: Int): Pair<TreffId, List<PersonTreffId>> {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent)
        db.publiser(treffId, navIdent)
        val personer = (1..jobbsøkerePerTreff).map { jobbsøker(treffId, runde * 100 + it) }
        db.inviterJobbsøkere(personer, treffId, navIdent)
        return treffId to personer
    }

    /** Hendelsen avlys eller fullfør skrev, må være den regelen gir for hendelsene før den. */
    private fun assertTreffslutthendelseStemmerMedLoggenFør(
        person: PersonTreffId,
        avslutninger: Set<JobbsøkerHendelsestype>,
        regel: (List<JobbsøkerHendelsestype>) -> JobbsøkerHendelsestype?,
    ) {
        val hendelser = hendelsestyper(person)
        val indeks = hendelser.indexOfFirst { it in avslutninger }
        assertThat(indeks).describedAs("avslutningshendelse for $person i $hendelser").isNotNegative()
        assertThat(hendelser[indeks]).describedAs("hendelser: $hendelser").isEqualTo(regel(hendelser.subList(0, indeks)))
    }

    /** Kjører alle kallene fra samme startsignal og returnerer feilene i stedet for å kaste dem. */
    private fun samtidig(vararg kall: () -> Unit): List<Throwable> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(kall.size)
        try {
            val oppgaver = kall.map { k ->
                pool.submit<Throwable?> {
                    check(start.await(10, TimeUnit.SECONDS))
                    runCatching(k).exceptionOrNull()
                }
            }
            start.countDown()
            return oppgaver.mapNotNull { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun workOpTreff(): TreffId =
        db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, kategori = RekrutteringstreffKategori.WORKOP)

    private fun jobbsøker(treffId: TreffId, nummer: Int): PersonTreffId =
        db.leggTilJobbsøkereMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    Fødselsnummer("%011d".format(nummer)),
                    Fornavn("Test"),
                    Etternavn("Testesen"),
                )
            ),
            treffId,
        ).first()

    private fun svar(person: PersonTreffId, svar: Boolean) =
        ctx.jobbsøkerService.svarPåVegneAvJobbsøker(person, navIdent, svar)

    private fun oppmøte(treffId: TreffId, person: PersonTreffId, møtt: Boolean) {
        ctx.oppmøteService.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, møtt), navIdent)
    }

    private fun lagretStatus(person: PersonTreffId): JobbsøkerStatus? =
        db.dataSource.connection.use { ctx.jobbsøkerRepository.hentStatus(it, person) }

    private fun utledetStatus(person: PersonTreffId): JobbsøkerStatus =
        Jobbsøkerstatusregler.utledStatus(hendelsestyper(person))

    private fun hendelsestyper(person: PersonTreffId): List<JobbsøkerHendelsestype> =
        db.dataSource.connection.use { ctx.jobbsøkerRepository.hentHendelsestyper(it, person) }
}
