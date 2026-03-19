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
        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).isEmpty()
        assertThat(resultat.antallTotalt).isEqualTo(0)
    }

    @Test
    fun `sok returnerer alle treff uten filtre`() {
        opprettTreff(tittel = "Treff 1")
        opprettTreff(tittel = "Treff 2")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(2)
        assertThat(resultat.antallTotalt).isEqualTo(2)
    }

    @Test
    fun `sok med visning MINE returnerer kun egne treff`() {
        opprettTreff(navIdent = "A123456", tittel = "Mitt")
        opprettTreff(navIdent = "B654321", tittel = "Andres")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.MINE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Mitt")
    }

    @Test
    fun `sok med visning MITT_KONTOR returnerer kun treff fra eget kontor`() {
        opprettTreff(kontorId = "0315", tittel = "Mitt kontor")
        opprettTreff(kontorId = "1201", tittel = "Annet kontor")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.MITT_KONTOR, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Mitt kontor")
    }

    @Test
    fun `sok filtrerer på status`() {
        opprettTreff(tittel = "Pub", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST)

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = listOf(SokStatus.PUBLISERT), apenForSokere = null,
            kontorer = null, visning = Visning.ALLE,
            side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Pub")
    }

    @Test
    fun `sok filtrerer på flere statuser`() {
        opprettTreff(tittel = "Pub", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST)
        opprettTreff(tittel = "Avlyst", status = RekrutteringstreffStatus.AVLYST)

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = listOf(SokStatus.PUBLISERT, SokStatus.UTKAST), apenForSokere = null,
            kontorer = null, visning = Visning.ALLE,
            side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(2)
    }

    @Test
    fun `sok filtrerer på kontorer`() {
        opprettTreff(tittel = "Oslo", kontorId = "0315")
        opprettTreff(tittel = "Bergen", kontorId = "1201")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = listOf("0315"),
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Oslo")
    }

    @Test
    fun `sok ekskluderer slettede treff`() {
        opprettTreff(tittel = "Synlig", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(tittel = "Slettet", status = RekrutteringstreffStatus.SLETTET)

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.antallTotalt).isEqualTo(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Synlig")
    }

    @Test
    fun `sok paginerer korrekt`() {
        repeat(5) { opprettTreff(tittel = "Treff $it") }

        val resultat1 = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 2
        )
        assertThat(resultat1.treff).hasSize(2)
        assertThat(resultat1.antallTotalt).isEqualTo(5)

        val resultat3 = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = 3, antallPerSide = 2
        )
        assertThat(resultat3.treff).hasSize(1)
    }

    @Test
    fun `sok handterer store sidetall uten overflow`() {
        opprettTreff(tittel = "Treff 1")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = Int.MAX_VALUE, antallPerSide = 100
        )

        assertThat(resultat.treff).isEmpty()
        assertThat(resultat.antallTotalt).isEqualTo(1)
    }

    @Test
    fun `sok mapper alle felter korrekt`() {
        opprettTreff(tittel = "Fullt treff", navIdent = "A123456", kontorId = "0315")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        val t = resultat.treff.first()
        assertThat(t.tittel).isEqualTo("Fullt treff")
        assertThat(t.beskrivelse).isNotNull()
        assertThat(t.status).isEqualTo(SokStatus.PUBLISERT)
        assertThat(t.apenForSokere).isTrue()
        assertThat(t.fraTid).isNotNull()
        assertThat(t.tilTid).isNotNull()
        assertThat(t.gateadresse).isEqualTo("Testgata 123")
        assertThat(t.postnummer).isEqualTo("0484")
        assertThat(t.poststed).isEqualTo("OSLO")
        assertThat(t.eiere).contains("A123456")
        assertThat(t.kontorer).contains("0315")
        assertThat(t.opprettetAvTidspunkt).isNotNull()
        assertThat(t.sistEndret).isNotNull()
    }

    @Test
    fun `statusaggregering teller per status`() {
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(status = RekrutteringstreffStatus.UTKAST)

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        val publisert = resultat.statusaggregering.find { it.verdi == "publisert" }
        val utkast = resultat.statusaggregering.find { it.verdi == "utkast" }
        assertThat(publisert?.antall).isEqualTo(2)
        assertThat(utkast?.antall).isEqualTo(1)
    }

    @Test
    fun `statusaggregering respekterer kontorfilter`() {
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "0315")
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "1201")
        opprettTreff(status = RekrutteringstreffStatus.UTKAST, kontorId = "0315")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = listOf("0315"),
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        val publisert = resultat.statusaggregering.find { it.verdi == "publisert" }
        val utkast = resultat.statusaggregering.find { it.verdi == "utkast" }
        assertThat(publisert?.antall).isEqualTo(1)
        assertThat(utkast?.antall).isEqualTo(1)
    }

    @Test
    fun `statusaggregering ekskluderer statusfilter`() {
        opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(status = RekrutteringstreffStatus.UTKAST)

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            statuser = null, apenForSokere = null, kontorer = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.statusaggregering).hasSize(2)
    }

}
