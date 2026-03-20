package no.nav.toi.rekrutteringstreff.sok

import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffSokServiceTest {
    companion object {
        private val db = TestDatabase()
        private lateinit var sokRepository: RekrutteringstreffSokRepository
        private lateinit var service: RekrutteringstreffSokService

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()
            sokRepository = RekrutteringstreffSokRepository(db.dataSource)
            service = RekrutteringstreffSokService(sokRepository)
        }
    }

    @AfterEach
    fun tearDown() {
        db.slettAlt()
    }

    private fun opprettTreff(
        navIdent: String = "A123456",
        tittel: String = "TestTreff",
        status: RekrutteringstreffStatus = RekrutteringstreffStatus.PUBLISERT,
        kontorId: String = "0315",
    ): TreffId =
        db.opprettRekrutteringstreffMedEierOgKontor(
            navIdent = navIdent,
            tittel = tittel,
            status = status,
            kontorId = kontorId,
        )

    @Test
    fun `sok returnerer respons med treff og aggregeringer`() {
        opprettTreff(tittel = "Pub 1", status = RekrutteringstreffStatus.PUBLISERT, kontorId = "0315")
        opprettTreff(tittel = "Pub 2", status = RekrutteringstreffStatus.PUBLISERT, kontorId = "1201")
        opprettTreff(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST, kontorId = "0315")

        val respons = service.sok(
            request = RekrutteringstreffSokRequest(),
            navIdent = "A123456",
            kontorId = "0315",
        )

        assertThat(respons.treff).hasSize(3)
        assertThat(respons.antallTotalt).isEqualTo(3)
        assertThat(respons.side).isEqualTo(1)
        assertThat(respons.antallPerSide).isEqualTo(20)
        assertThat(respons.statusaggregering).isNotEmpty()
    }

    @Test
    fun `sok med statusfilter gir treff kun for valgt status og komplett statusaggregering`() {
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "0315")
        opprettTreff(status = RekrutteringstreffStatus.UTKAST, kontorId = "0315")

        val respons = service.sok(
            request = RekrutteringstreffSokRequest(statuser = listOf(SokStatus.PUBLISERT)),
            navIdent = "A123456",
            kontorId = "0315",
        )

        assertThat(respons.treff).hasSize(1)
        assertThat(respons.statusaggregering).hasSize(2)
    }

    @Test
    fun `sok med kontorfilter gir treff kun for valgt kontor`() {
        opprettTreff(kontorId = "0315")
        opprettTreff(kontorId = "1201")

        val respons = service.sok(
            request = RekrutteringstreffSokRequest(kontorer = listOf("0315")),
            navIdent = "A123456",
            kontorId = "0315",
        )

        assertThat(respons.treff).hasSize(1)
    }

    @Test
    fun `sok med visning MINE filtrerer treff og aggregeringer`() {
        opprettTreff(navIdent = "A123456", tittel = "Mitt")
        opprettTreff(navIdent = "B654321", tittel = "Andres")

        val respons = service.sok(
            request = RekrutteringstreffSokRequest(visning = Visning.MINE),
            navIdent = "A123456",
            kontorId = "0315",
        )

        assertThat(respons.treff).hasSize(1)
        assertThat(respons.treff.first().tittel).isEqualTo("Mitt")
        assertThat(respons.statusaggregering.sumOf { it.antall }).isEqualTo(1)
    }

    @Test
    fun `paginering settes korrekt i respons`() {
        repeat(5) { opprettTreff(tittel = "Treff $it") }

        val respons = service.sok(
            request = RekrutteringstreffSokRequest(side = 1, antallPerSide = 2),
            navIdent = "A123456",
            kontorId = "0315",
        )

        assertThat(respons.treff).hasSize(2)
        assertThat(respons.antallTotalt).isEqualTo(5)
        assertThat(respons.side).isEqualTo(1)
        assertThat(respons.antallPerSide).isEqualTo(2)
    }

    @Test
    fun `statusaggregering ekskluderer statusfilteret men inkluderer kontorfilteret`() {
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "0315")
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "1201")
        opprettTreff(status = RekrutteringstreffStatus.UTKAST, kontorId = "0315")

        val respons = service.sok(
            request = RekrutteringstreffSokRequest(
                statuser = listOf(SokStatus.PUBLISERT),
                kontorer = listOf("0315"),
            ),
            navIdent = "A123456",
            kontorId = "0315",
        )

        assertThat(respons.treff).hasSize(1)

        val statusPub = respons.statusaggregering.find { it.verdi == "publisert_apen" }
        val statusUtkast = respons.statusaggregering.find { it.verdi == "utkast" }
        assertThat(statusPub?.antall).isEqualTo(1)
        assertThat(statusUtkast?.antall).isEqualTo(1)
    }
}
