package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.rekrutteringstreff.*
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.time.ZoneOffset
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KiLoggRepositoryTest {

    private val db = TestDatabase()
    private lateinit var repo: KiLoggRepository

    @BeforeAll
    fun setup() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        repo = KiLoggRepository(db.dataSource)
    }

    @AfterEach
    fun cleanup() {
        db.slettAlt()
    }

    @Test
    fun kan_lagre_logg_og_faa_id() {
        val treffDbId = hentTreffDbId(db.opprettRekrutteringstreffIDatabase("A123456"))
        val id = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "tittel",
                sporringFraFrontend = "Original tekst",
                sporringFiltrert = "Filtrert tekst",
                systemprompt = "prompt",
                ekstraParametre = mapOf("nøkkel" to "verdi"),
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )
        assertThat(id).isNotNull()
        assertThat(id.toString()).isNotBlank()
    }

    @Test
    fun kan_hente_logg_med_id() {
        val treffDbId = hentTreffDbId(db.opprettRekrutteringstreffIDatabase("A123456"))
        val id = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "innlegg",
                sporringFraFrontend = "Hei verden",
                sporringFiltrert = "Hei",
                systemprompt = null,
                ekstraParametre = null,
                bryterRetningslinjer = true,
                begrunnelse = "Begrunnelse",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 321
            )
        )

        val row = repo.findById(id)
        assertThat(row).isNotNull
        row!!
        assertThat(row.id).isEqualTo(id)
        assertThat(row.treffDbId).isEqualTo(treffDbId)
        assertThat(row.feltType).isEqualTo("innlegg")
        assertThat(row.sporringFraFrontend).isEqualTo("Hei verden")
        assertThat(row.sporringFiltrert).isEqualTo("Hei")
        assertThat(row.systemprompt).isNull()
        assertThat(row.ekstraParametreJson).isNull()
        assertThat(row.bryterRetningslinjer).isTrue()
        assertThat(row.begrunnelse).isEqualTo("Begrunnelse")
        assertThat(row.kiNavn).isEqualTo("gpt-4o")
        assertThat(row.kiVersjon).isEqualTo("2025-01-01")
        assertThat(row.svartidMs).isEqualTo(321)
        assertThat(row.lagret).isFalse()
        assertThat(row.manuellKontrollBryterRetningslinjer).isNull()
        assertThat(row.manuellKontrollUtfortAv).isNull()
        assertThat(row.manuellKontrollTidspunkt).isNull()
    }

    @Test
    fun kan_markere_logg_som_lagret() {
        val treffDbId = hentTreffDbId(db.opprettRekrutteringstreffIDatabase("A123456"))
        val id = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "tittel",
                sporringFraFrontend = "A",
                sporringFiltrert = "A",
                systemprompt = null,
                ekstraParametre = null,
                bryterRetningslinjer = false,
                begrunnelse = null,
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 10
            )
        )

        val updated = repo.setLagret(id, true)
        assertThat(updated).isEqualTo(1)

        val row = repo.findById(id)!!
        assertThat(row.lagret).isTrue()
    }

    @Test
    fun kan_registrere_manuell_kontroll() {
        val treffDbId = hentTreffDbId(db.opprettRekrutteringstreffIDatabase("A123456"))
        val id = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "tittel",
                sporringFraFrontend = "Tekst",
                sporringFiltrert = "Tekst",
                systemprompt = null,
                ekstraParametre = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 11
            )
        )

        val nå = ZonedDateTime.now(ZoneOffset.UTC)
        val updated = repo.setManuellKontroll(id, true, "Z123456", nå)
        assertThat(updated).isEqualTo(1)

        val row = repo.findById(id)!!
        assertThat(row.manuellKontrollBryterRetningslinjer).isTrue()
        assertThat(row.manuellKontrollUtfortAv).isEqualTo("Z123456")
        assertThat(row.manuellKontrollTidspunkt).isNotNull()
    }

    @Test
    fun kan_liste_logg_for_treff_med_filter_og_paginering() {
        val treffDbId = hentTreffDbId(db.opprettRekrutteringstreffIDatabase("A123456"))

        val id1 = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "tittel",
                sporringFraFrontend = "1",
                sporringFiltrert = "1",
                systemprompt = null,
                ekstraParametre = null,
                bryterRetningslinjer = false,
                begrunnelse = null,
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 1
            )
        )
        Thread.sleep(5)
        val id2 = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "innlegg",
                sporringFraFrontend = "2",
                sporringFiltrert = "2",
                systemprompt = null,
                ekstraParametre = null,
                bryterRetningslinjer = true,
                begrunnelse = "B",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 2
            )
        )
        Thread.sleep(5)
        val id3 = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "tittel",
                sporringFraFrontend = "3",
                sporringFiltrert = "3",
                systemprompt = null,
                ekstraParametre = null,
                bryterRetningslinjer = false,
                begrunnelse = "C",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 3
            )
        )

        val alle = repo.listByTreff(treffDbId, feltType = null, limit = 50, offset = 0)
        assertThat(alle.map { it.id }).containsExactly(id3, id2, id1)

        val bareTittel = repo.listByTreff(treffDbId, feltType = "tittel", limit = 50, offset = 0)
        assertThat(bareTittel.map { it.id }).containsExactly(id3, id1)

        val side1 = repo.listByTreff(treffDbId, feltType = null, limit = 1, offset = 0)
        val side2 = repo.listByTreff(treffDbId, feltType = null, limit = 1, offset = 1)
        assertThat(side1.map { it.id }).containsExactly(id3)
        assertThat(side2.map { it.id }).containsExactly(id2)
    }

    private fun hentTreffDbId(treffId: TreffId): Long =
        db.dataSource.connection.use { c ->
            c.prepareStatement("select db_id from rekrutteringstreff where id = ?").use { ps ->
                ps.setObject(1, treffId.somUuid)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }
}