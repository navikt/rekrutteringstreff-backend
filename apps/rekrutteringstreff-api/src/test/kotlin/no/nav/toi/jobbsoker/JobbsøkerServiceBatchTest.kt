package no.nav.toi.jobbsoker

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

class JobbsøkerServiceBatchTest {
    @Test
    fun `leggTilJobbsøkere bruker samme batchstørrelse mot kandidatsøk og database`() {
        val dataSource = mockk<DataSource>()
        val connection = mockk<Connection>(relaxed = true)
        val jobbsøkerRepository = mockk<JobbsøkerRepository>()
        val kandidatsøkKlient = mockk<KandidatsøkKlient>()
        val treffId = TreffId(UUID.randomUUID())
        val batchStørrelse = MAKS_ANTALL_JOBBSØKERE_PER_BATCH
        val jobbsøkere = (1..(batchStørrelse + 1)).map(::lagJobbsøker)
        val førsteBatch = jobbsøkere.take(batchStørrelse)
        val andreBatch = jobbsøkere.drop(batchStørrelse)

        every { dataSource.connection } returns connection
        every { jobbsøkerRepository.hentJobbsøkere(treffId) } returns emptyList()
        every { jobbsøkerRepository.hentSlettedeJobbsøkere(treffId) } returns emptyList()
        every { kandidatsøkKlient.erKonfigurert() } returns true
        every { kandidatsøkKlient.hentJobbsokerInfo(førsteBatch.map { it.fødselsnummer }, "innkommende-token") } returns emptyMap()
        every { kandidatsøkKlient.hentJobbsokerInfo(andreBatch.map { it.fødselsnummer }, "innkommende-token") } returns emptyMap()
        every { jobbsøkerRepository.leggTil(connection, førsteBatch, treffId) } returns opprettedeJobbsøkere(førsteBatch.size)
        every { jobbsøkerRepository.leggTil(connection, andreBatch, treffId) } returns opprettedeJobbsøkere(andreBatch.size)
        every {
            jobbsøkerRepository.leggTilOpprettetHendelser(
                connection,
                any(),
                "A123456",
                any(),
                null,
            )
        } just Runs

        val service = JobbsøkerService(
            dataSource = dataSource,
            jobbsøkerRepository = jobbsøkerRepository,
            jobbsøkerSokRepository = JobbsøkerSokRepository(dataSource),
            kandidatsøkKlient = kandidatsøkKlient,
        )

        val resultat = service.leggTilJobbsøkere(
            jobbsøkere = jobbsøkere,
            treffId = treffId,
            navIdent = "A123456",
            innkommendeToken = "innkommende-token",
        )

        assertThat(resultat.antallLagtTil).isEqualTo(batchStørrelse + 1)
        verify(exactly = 1) {
            kandidatsøkKlient.hentJobbsokerInfo(førsteBatch.map(LeggTilJobbsøker::fødselsnummer), "innkommende-token")
            kandidatsøkKlient.hentJobbsokerInfo(andreBatch.map(LeggTilJobbsøker::fødselsnummer), "innkommende-token")
            jobbsøkerRepository.leggTil(connection, førsteBatch, treffId)
            jobbsøkerRepository.leggTil(connection, andreBatch, treffId)
        }
        verify(exactly = 1) {
            jobbsøkerRepository.leggTilOpprettetHendelser(connection, match { it.size == batchStørrelse }, "A123456", any(), null)
            jobbsøkerRepository.leggTilOpprettetHendelser(connection, match { it.size == 1 }, "A123456", any(), null)
        }
    }

    private fun lagJobbsøker(indeks: Int) = LeggTilJobbsøker(
        fødselsnummer = Fødselsnummer(indeks.toString().padStart(11, '0')),
        fornavn = Fornavn("Fornavn$indeks"),
        etternavn = Etternavn("Etternavn$indeks"),
    )

    private fun opprettedeJobbsøkere(antall: Int) = (1..antall).map { indeks ->
        JobbsøkerRepository.OpprettetJobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            jobbsøkerId = indeks.toLong(),
        )
    }
}