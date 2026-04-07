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

    private val testDatabase = TestDatabase()
    private lateinit var kiLoggRepository: KiLoggRepository

    @BeforeAll
    fun setup() {
        Flyway.configure().dataSource(testDatabase.dataSource).load().migrate()
        kiLoggRepository = KiLoggRepository(testDatabase.dataSource)
    }

    @AfterEach
    fun cleanup() {
        testDatabase.slettAlt()
    }

    @Test
    fun kan_lagre_logg_og_faa_id() {
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Original tekst",
                spørringFiltrert = "Filtrert tekst",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )
        assertThat(id).isNotNull()
        assertThat(id.toString()).isNotBlank()
    }

    @Test
    fun kan_hente_logg_med_id() {
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "Hei verden",
                spørringFiltrert = "Hei",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = true,
                begrunnelse = "Begrunnelse",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 321
            )
        )

        val row = kiLoggRepository.findById(id)!!
        assertThat(row.id).isEqualTo(id)
        assertThat(row.treffId).isEqualTo(treffId)
        assertThat(row.feltType).isEqualTo("innlegg")
        assertThat(row.spørringFraFrontend).isEqualTo("Hei verden")
        assertThat(row.spørringFiltrert).isEqualTo("Hei")
        assertThat(row.systemprompt).isNull()
        assertThat(row.ekstraParametreJson).isNull()
        assertThat(row.bryterRetningslinjer).isTrue()
        assertThat(row.begrunnelse).isEqualTo("Begrunnelse")
        assertThat(row.kiNavn).isEqualTo("azure-openai")
        assertThat(row.kiVersjon).isEqualTo("2025-01-01")
        assertThat(row.svartidMs).isEqualTo(321)
        assertThat(row.lagret).isFalse()
        assertThat(row.manuellKontrollBryterRetningslinjer).isNull()
        assertThat(row.manuellKontrollUtfortAv).isNull()
        assertThat(row.manuellKontrollTidspunkt).isNull()
    }

    @Test
    fun kan_markere_logg_som_lagret() {
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "A",
                spørringFiltrert = "A",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = null,
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 10
            )
        )

        val updated = kiLoggRepository.setLagret(id, true)
        assertThat(updated).isEqualTo(1)

        val row = kiLoggRepository.findById(id)!!
        assertThat(row.lagret).isTrue()
    }

    @Test
    fun kan_registrere_manuell_kontroll() {
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 11
            )
        )

        val nå = ZonedDateTime.now(ZoneOffset.UTC)
        val updated = kiLoggRepository.setManuellKontroll(id, true, "Z123456", nå)
        assertThat(updated).isEqualTo(1)

        val row = kiLoggRepository.findById(id)!!
        assertThat(row.manuellKontrollBryterRetningslinjer).isTrue()
        assertThat(row.manuellKontrollUtfortAv).isEqualTo("Z123456")
        assertThat(row.manuellKontrollTidspunkt).isNotNull()
    }

    @Test
    fun kan_nullstille_manuell_kontroll_til_null() {
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val id = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 11
            )
        )

        val nå = ZonedDateTime.now(ZoneOffset.UTC)
        assertThat(kiLoggRepository.setManuellKontroll(id, true, "Z123456", nå)).isEqualTo(1)
        val s1 = kiLoggRepository.findById(id)!!
        assertThat(s1.manuellKontrollBryterRetningslinjer).isTrue()
        assertThat(s1.manuellKontrollUtfortAv).isEqualTo("Z123456")
        assertThat(s1.manuellKontrollTidspunkt).isNotNull()

        assertThat(kiLoggRepository.setManuellKontroll(id, null, null, null)).isEqualTo(1)
        val s2 = kiLoggRepository.findById(id)!!
        assertThat(s2.manuellKontrollBryterRetningslinjer).isNull()
        assertThat(s2.manuellKontrollUtfortAv).isNull()
        assertThat(s2.manuellKontrollTidspunkt).isNull()
    }

    @Test
    fun kan_liste_logg_for_treff_med_filter_og_paginering() {
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid

        val id1 = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "1",
                spørringFiltrert = "1",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = null,
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 1
            )
        )
        val id2 = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "2",
                spørringFiltrert = "2",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = true,
                begrunnelse = "B",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 2
            )
        )
        val id3 = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "3",
                spørringFiltrert = "3",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "C",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 3
            )
        )

        val alle = kiLoggRepository.list(treffId, feltType = null, limit = 50, offset = 0)
        assertThat(alle.map { it.id }).containsExactly(id3, id2, id1)

        val bareTittel = kiLoggRepository.list(treffId, feltType = "tittel", limit = 50, offset = 0)
        assertThat(bareTittel.map { it.id }).containsExactly(id3, id1)

        val side1 = kiLoggRepository.list(treffId, feltType = null, limit = 1, offset = 0)
        val side2 = kiLoggRepository.list(treffId, feltType = null, limit = 1, offset = 1)
        assertThat(side1.map { it.id }).containsExactly(id3)
        assertThat(side2.map { it.id }).containsExactly(id2)
    }

    @Test
    fun kan_liste_logg_for_alle_treff_nar_treffId_er_null() {
        val treffId1 = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val treffId2 = testDatabase.opprettRekrutteringstreffIDatabase("B654321").somUuid

        val id1 = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId1,
                feltType = "tittel",
                spørringFraFrontend = "fra1",
                spørringFiltrert = "fil1",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = null,
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 1
            )
        )
        val id2 = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId2,
                feltType = "innlegg",
                spørringFraFrontend = "fra2",
                spørringFiltrert = "fil2",
                systemprompt = null,
                ekstraParametreJson = null,
                bryterRetningslinjer = true,
                begrunnelse = "B",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 2
            )
        )

        val alle = kiLoggRepository.list(treffId = null, feltType = null, limit = 50, offset = 0)

        assertThat(alle.map { it.id }).containsExactly(id2, id1)
        assertThat(alle.map { it.treffId }.toSet())
            .containsExactlyInAnyOrder(treffId1, treffId2)
    }

    @Test
    fun lagrer_og_henter_ekstra_parametre_json() {
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val ekstraJson = """
        {
          "promptVersjonsnummer": "${SystemPrompt.versjonsnummer}",
          "promptEndretTidspunkt": "${SystemPrompt.endretTidspunkt}",
          "promptHash": "${SystemPrompt.hash}"
        }
    """.trimIndent()

        val id = kiLoggRepository.insert(
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
                kiVersjon = "toi-gpt-4.1",
                svartidMs = 42
            )
        )

        val row = kiLoggRepository.findById(id)!!
        assertThat(row.ekstraParametreJson).isNotNull()

        val meta: EkstraMetaDbJson = JacksonConfig.mapper.readValue(row.ekstraParametreJson!!)
        assertThat(meta.promptVersjonsnummer).isEqualTo(SystemPrompt.versjonsnummer)
        assertThat(meta.promptHash).isEqualTo(SystemPrompt.hash)

        val parsed = ZonedDateTime.parse(meta.promptEndretTidspunkt)
        assertThat(parsed.toLocalDate()).isEqualTo(SystemPrompt.endretTidspunkt.toLocalDate())
    }

    @Test
    fun `Ki-logger skal kunne batch-slettes`() {
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val ekstraJson = """
        {
          "promptVersjonsnummer": "${SystemPrompt.versjonsnummer}",
          "promptEndretTidspunkt": "${SystemPrompt.endretTidspunkt}",
          "promptHash": "${SystemPrompt.hash}"
        }
    """.trimIndent()
        val loggId1 = kiLoggRepository.insert(
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
                kiVersjon = "toi-gpt-4.1",
                svartidMs = 42
            )
        )
        val loggId2 = kiLoggRepository.insert(
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
                kiVersjon = "toi-gpt-4.1",
                svartidMs = 42
            )
        )
        val loggId3 = kiLoggRepository.insert(
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
                kiVersjon = "toi-gpt-4.1",
                svartidMs = 42
            )
        )
        val loggIdListe = listOf(loggId1, loggId2, loggId3)
        assertThat(kiLoggRepository.findById(loggId1)).isNotNull()
        assertThat(kiLoggRepository.findById(loggId2)).isNotNull()
        assertThat(kiLoggRepository.findById(loggId3)).isNotNull()
        kiLoggRepository.slettKiLogger(loggIdListe)
        assertThat(kiLoggRepository.findById(loggId1)).isNull()
        assertThat(kiLoggRepository.findById(loggId2)).isNull()
        assertThat(kiLoggRepository.findById(loggId3)).isNull()
    }

    @Test
    fun `Skal hente KI-logger som er eldre enn 6 måneder`() {
        val ANTALL_MÅNEDER_MINUS_LOGG_SOM_SKAL_HENTES: Long = 7
        val ANTALL_MÅNEDER_MINUS_LOGG_SOM_IKKE_SKAL_HENTES: Long = 3
        val ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING = 6
        val treffId = testDatabase.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val ekstraJson = """
        {
          "promptVersjonsnummer": "${SystemPrompt.versjonsnummer}",
          "promptEndretTidspunkt": "${SystemPrompt.endretTidspunkt}",
          "promptHash": "${SystemPrompt.hash}"
        }
    """.trimIndent()
        val loggIdSomSkalHentes = testDatabase.opprettKiLogg(
            KiLoggTestInsert(
                opprettetTidspunkt = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(ANTALL_MÅNEDER_MINUS_LOGG_SOM_SKAL_HENTES),
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = ekstraJson,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "toi-gpt-4.1",
                svartidMs = 42
            )
        )
        val loggIdSomIkkeSkalHentes = testDatabase.opprettKiLogg(
            KiLoggTestInsert(
                opprettetTidspunkt = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(ANTALL_MÅNEDER_MINUS_LOGG_SOM_IKKE_SKAL_HENTES),
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = ekstraJson,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "toi-gpt-4.1",
                svartidMs = 42
            )
        )
        assertThat(kiLoggRepository.findById(loggIdSomSkalHentes)).isNotNull()
        assertThat(kiLoggRepository.findById(loggIdSomIkkeSkalHentes)).isNotNull()
        assertThat(kiLoggRepository.hentKiLoggIderForScheduledSletting(ANTALL_MÅNEDER_ETTER_KI_LOGG_OPPRETTET_FOR_SLETTING)).containsExactly(loggIdSomSkalHentes)
    }
}
