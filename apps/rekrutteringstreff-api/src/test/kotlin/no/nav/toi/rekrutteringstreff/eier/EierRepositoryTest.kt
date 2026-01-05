package no.nav.toi.rekrutteringstreff.eier

import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EierRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: EierRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
            repository = EierRepository(db.dataSource)
        }
    }

    @AfterEach
    fun slettAlt() {
        db.slettAlt()
    }

    @Test
    fun `hent returnerer eiere for et treff`() {
        val navIdent = "A123456"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        val eiere = repository.hent(treffId)

        assertThat(eiere).isNotNull
        assertThat(eiere).hasSize(1)
        assertThat(eiere!!.tilNavIdenter().first()).isEqualTo(navIdent)
    }

    @Test
    fun `hent returnerer null for treff som ikke finnes`() {
        val ikkeEksisterendeTreff = TreffId("00000000-0000-0000-0000-000000000000")

        val eiere = repository.hent(ikkeEksisterendeTreff)

        assertThat(eiere).isNull()
    }

    @Test
    fun `leggTil legger til nye eiere`() {
        val navIdent = "A123456"
        val nyeEiere = listOf("B654321", "C987654")
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        repository.leggTil(treffId, nyeEiere)

        val eiere = repository.hent(treffId)
        assertThat(eiere).isNotNull
        assertThat(eiere!!.tilNavIdenter()).containsExactlyInAnyOrder(navIdent, "B654321", "C987654")
    }

    @Test
    fun `leggTil legger ikke til duplikater`() {
        val navIdent = "A123456"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        // Legg til samme eier to ganger
        repository.leggTil(treffId, listOf(navIdent))
        repository.leggTil(treffId, listOf(navIdent))

        val eiere = repository.hent(treffId)
        assertThat(eiere).isNotNull
        assertThat(eiere!!.tilNavIdenter()).containsExactly(navIdent)
    }

    @Test
    fun `slett fjerner en eier`() {
        val navIdent = "A123456"
        val andreEier = "B654321"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")
        repository.leggTil(treffId, listOf(andreEier))

        val eiereFør = repository.hent(treffId)
        assertThat(eiereFør!!.tilNavIdenter()).contains(andreEier)

        repository.slett(treffId, andreEier)

        val eiereEtter = repository.hent(treffId)
        assertThat(eiereEtter!!.tilNavIdenter()).doesNotContain(andreEier)
        assertThat(eiereEtter.tilNavIdenter()).contains(navIdent)
    }

    @Test
    fun `slett gjør ingenting når eier ikke finnes`() {
        val navIdent = "A123456"
        val ikkeEksisterendeEier = "Z999999"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        val eiereFør = repository.hent(treffId)
        assertThat(eiereFør).hasSize(1)

        repository.slett(treffId, ikkeEksisterendeEier)

        val eiereEtter = repository.hent(treffId)
        assertThat(eiereEtter).hasSize(1)
    }

    @Test
    fun `tilNavIdenter mapper liste av Eiere til liste av navIdenter`() {
        val navIdent = "A123456"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")
        repository.leggTil(treffId, listOf("B654321"))

        val eiere = repository.hent(treffId)

        val navIdenter = eiere!!.tilNavIdenter()

        assertThat(navIdenter).containsExactlyInAnyOrder("A123456", "B654321")
    }
}

