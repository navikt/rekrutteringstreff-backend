package no.nav.toi.jobbsoker

import no.nav.toi.ApplicationContext
import no.nav.toi.TestInfrastructureContext
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

    /** Kjører begge kallene fra samme startsignal og returnerer feilene i stedet for å kaste dem. */
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
        db.dataSource.connection.use {
            Jobbsøkerstatusregler.utledStatus(ctx.jobbsøkerRepository.hentHendelsestyper(it, person))
        }
}
