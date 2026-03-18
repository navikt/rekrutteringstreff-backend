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
class RekrutteringstreffSokRepositoryTest {
    companion object {
        private val db = TestDatabase()
        private lateinit var repository: RekrutteringstreffSokRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()
            repository = RekrutteringstreffSokRepository(db.dataSource)
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
    fun `sok returnerer tomme resultater når ingen treff finnes`() {
        val (treff, totalt) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(treff).isEmpty()
        assertThat(totalt).isEqualTo(0)
    }

    @Test
    fun `sok returnerer alle treff uten filtre`() {
        opprettTreff(tittel = "Treff 1")
        opprettTreff(tittel = "Treff 2")

        val (treff, totalt) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(treff).hasSize(2)
        assertThat(totalt).isEqualTo(2)
    }

    @Test
    fun `sok med visning MINE returnerer kun egne treff`() {
        opprettTreff(navIdent = "A123456", tittel = "Mitt")
        opprettTreff(navIdent = "B654321", tittel = "Andres")

        val (treff, _) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = null,
            visning = Visning.MINE, side = 1, antallPerSide = 25
        )
        assertThat(treff).hasSize(1)
        assertThat(treff.first().tittel).isEqualTo("Mitt")
    }

    @Test
    fun `sok med visning MITT_KONTOR returnerer kun treff fra eget kontor`() {
        opprettTreff(kontorId = "0315", tittel = "Mitt kontor")
        opprettTreff(kontorId = "1201", tittel = "Annet kontor")

        val (treff, _) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = null,
            visning = Visning.MITT_KONTOR, side = 1, antallPerSide = 25
        )
        assertThat(treff).hasSize(1)
        assertThat(treff.first().tittel).isEqualTo("Mitt kontor")
    }

    @Test
    fun `sok filtrerer på visningsstatus`() {
        opprettTreff(tittel = "Pub", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST)

        val (treff, _) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = listOf(Visningsstatus.PUBLISERT),
            kontorer = null, visning = Visning.ALLE,
            side = 1, antallPerSide = 25
        )
        assertThat(treff).hasSize(1)
        assertThat(treff.first().tittel).isEqualTo("Pub")
    }

    @Test
    fun `sok filtrerer på flere statuser`() {
        opprettTreff(tittel = "Pub", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST)
        opprettTreff(tittel = "Avlyst", status = RekrutteringstreffStatus.AVLYST)

        val (treff, _) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = listOf(Visningsstatus.PUBLISERT, Visningsstatus.UTKAST),
            kontorer = null, visning = Visning.ALLE,
            side = 1, antallPerSide = 25
        )
        assertThat(treff).hasSize(2)
    }

    @Test
    fun `sok filtrerer på kontorer`() {
        opprettTreff(tittel = "Oslo", kontorId = "0315")
        opprettTreff(tittel = "Bergen", kontorId = "1201")

        val (treff, _) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = listOf("0315"),
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(treff).hasSize(1)
        assertThat(treff.first().tittel).isEqualTo("Oslo")
    }

    @Test
    fun `sok ekskluderer slettede treff`() {
        opprettTreff(tittel = "Synlig", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(tittel = "Slettet", status = RekrutteringstreffStatus.SLETTET)

        val (treff, totalt) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(treff).hasSize(1)
        assertThat(totalt).isEqualTo(1)
        assertThat(treff.first().tittel).isEqualTo("Synlig")
    }

    @Test
    fun `sok paginerer korrekt`() {
        repeat(5) { opprettTreff(tittel = "Treff $it") }

        val (side0, totalt) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 2
        )
        assertThat(side0).hasSize(2)
        assertThat(totalt).isEqualTo(5)

        val (side2, _) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = null,
            visning = Visning.ALLE, side = 3, antallPerSide = 2
        )
        assertThat(side2).hasSize(1)
    }

    @Test
    fun `sok mapper alle felter korrekt`() {
        opprettTreff(tittel = "Fullt treff", navIdent = "A123456", kontorId = "0315")

        val (treff, _) = repository.sok(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(treff).hasSize(1)
        val t = treff.first()
        assertThat(t.tittel).isEqualTo("Fullt treff")
        assertThat(t.beskrivelse).isNotNull()
        assertThat(t.visningsstatus).isEqualTo(Visningsstatus.PUBLISERT)
        assertThat(t.fraTid).isNotNull()
        assertThat(t.tilTid).isNotNull()
        assertThat(t.gateadresse).isEqualTo("Testgata 123")
        assertThat(t.postnummer).isEqualTo("0484")
        assertThat(t.poststed).isEqualTo("OSLO")
        assertThat(t.kommune).isEqualTo("Oslo")
        assertThat(t.fylke).isEqualTo("Oslo")
        assertThat(t.eiere).contains("A123456")
        assertThat(t.kontorer).contains("0315")
        assertThat(t.opprettetAvTidspunkt).isNotNull()
        assertThat(t.sistEndret).isNotNull()
    }

    @Test
    fun `statusaggregering teller per visningsstatus`() {
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(status = RekrutteringstreffStatus.UTKAST)

        val agg = repository.statusaggregering(
            navIdent = "A123456", kontorId = "0315",
            kontorer = null, visning = Visning.ALLE
        )
        val publisert = agg.find { it.verdi == "publisert" }
        val utkast = agg.find { it.verdi == "utkast" }
        assertThat(publisert?.antall).isEqualTo(2)
        assertThat(utkast?.antall).isEqualTo(1)
    }

    @Test
    fun `statusaggregering respekterer kontorfilter`() {
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "0315")
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "1201")
        opprettTreff(status = RekrutteringstreffStatus.UTKAST, kontorId = "0315")

        val agg = repository.statusaggregering(
            navIdent = "A123456", kontorId = "0315",
            kontorer = listOf("0315"), visning = Visning.ALLE
        )
        val publisert = agg.find { it.verdi == "publisert" }
        val utkast = agg.find { it.verdi == "utkast" }
        assertThat(publisert?.antall).isEqualTo(1)
        assertThat(utkast?.antall).isEqualTo(1)
    }

    @Test
    fun `statusaggregering ekskluderer statusfilter`() {
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(status = RekrutteringstreffStatus.UTKAST)

        val agg = repository.statusaggregering(
            navIdent = "A123456", kontorId = "0315",
            kontorer = null, visning = Visning.ALLE
        )
        assertThat(agg).hasSize(2)
    }

}
