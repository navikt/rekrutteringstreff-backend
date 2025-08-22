package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.*
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.time.ZoneOffset
import java.time.ZonedDateTime
import com.fasterxml.jackson.module.kotlin.readValue


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
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Original tekst",
                spørringFiltrert = "Filtrert tekst",
                systemprompt = "prompt",
                ekstraParametreJson = null,
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
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "Hei verden",
                spørringFiltrert = "Hei",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = true,
                begrunnelse = "Begrunnelse",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 321
            )
        )

        val row = repo.findById(id)!!
        assertThat(row.id).isEqualTo(id)
        assertThat(row.treffId).isEqualTo(treffId)
        assertThat(row.feltType).isEqualTo("innlegg")
        assertThat(row.spørringFraFrontend).isEqualTo("Hei verden")
        assertThat(row.spørringFiltrert).isEqualTo("Hei")
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
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "A",
                spørringFiltrert = "A",
                systemprompt = null,
                ekstraParametreJson = null,
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
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = null,
                ekstraParametreJson = null,
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
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid

        val id1 = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "1",
                spørringFiltrert = "1",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = null,
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 1
            )
        )
        val id2 = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "2",
                spørringFiltrert = "2",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = true,
                begrunnelse = "B",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 2
            )
        )
        val id3 = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "3",
                spørringFiltrert = "3",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "C",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 3
            )
        )

        val alle = repo.list(treffId, feltType = null, limit = 50, offset = 0)
        assertThat(alle.map { it.id }).containsExactly(id3, id2, id1)

        val bareTittel = repo.list(treffId, feltType = "tittel", limit = 50, offset = 0)
        assertThat(bareTittel.map { it.id }).containsExactly(id3, id1)

        val side1 = repo.list(treffId, feltType = null, limit = 1, offset = 0)
        val side2 = repo.list(treffId, feltType = null, limit = 1, offset = 1)
        assertThat(side1.map { it.id }).containsExactly(id3)
        assertThat(side2.map { it.id }).containsExactly(id2)
    }

    @Test
    fun kan_liste_logg_for_alle_treff_nar_treffId_er_null() {
        val treffId1 = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val treffId2 = db.opprettRekrutteringstreffIDatabase("B654321").somUuid

        val id1 = repo.insert(
            KiLoggInsert(
                treffId = treffId1,
                feltType = "tittel",
                spørringFraFrontend = "fra1",
                spørringFiltrert = "fil1",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = null,
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 1
            )
        )
        val id2 = repo.insert(
            KiLoggInsert(
                treffId = treffId2,
                feltType = "innlegg",
                spørringFraFrontend = "fra2",
                spørringFiltrert = "fil2",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = true,
                begrunnelse = "B",
                kiNavn = "gpt-4o",
                kiVersjon = "2025-01-01",
                svartidMs = 2
            )
        )

        val alle = repo.list(treffId = null, feltType = null, limit = 50, offset = 0)

        assertThat(alle.map { it.id }).containsExactly(id2, id1)
        assertThat(alle.map { it.treffId }.toSet())
            .containsExactlyInAnyOrder(treffId1, treffId2)
    }

    @Test
    fun lagrer_og_henter_ekstra_parametre_json() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val ekstraJson = """
        {
          "promptVersjonsnummer": "${SystemPrompt.versjonsnummer}",
          "promptEndretTidspunkt": "${SystemPrompt.endretTidspunkt}",
          "promptHash": "${SystemPrompt.hash}"
        }
    """.trimIndent()

        val id = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = ekstraJson,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "toi-gpt-4o",
                svartidMs = 42
            )
        )

        val row = repo.findById(id)!!
        assertThat(row.ekstraParametreJson).isNotNull()

        val meta: EkstraMetaDbJson = JacksonConfig.mapper.readValue(row.ekstraParametreJson!!)
        assertThat(meta.promptVersjonsnummer).isEqualTo(SystemPrompt.versjonsnummer)
        assertThat(meta.promptHash).isEqualTo(SystemPrompt.hash)

        val parsed = ZonedDateTime.parse(meta.promptEndretTidspunkt)
        assertThat(parsed.toLocalDate()).isEqualTo(SystemPrompt.endretTidspunkt.toLocalDate())
    }
}
