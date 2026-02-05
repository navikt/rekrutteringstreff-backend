package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.exception.KiValideringsException
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KiValideringsServiceTest {

    private val db = TestDatabase()
    private lateinit var kiLoggRepository: KiLoggRepository
    private lateinit var service: KiValideringsService

    @BeforeAll
    fun setup() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        kiLoggRepository = KiLoggRepository(db.dataSource)
        service = KiValideringsService(kiLoggRepository)
    }

    @AfterEach
    fun cleanup() {
        db.slettAlt()
    }

    @Test
    fun `tom tekst hopper over validering`() {
        // Skal ikke kaste exception
        service.verifiserKiValidering(
            tekst = "",
            kiLoggId = null,
            lagreLikevel = false,
            feltType = "tittel"
        )

        service.verifiserKiValidering(
            tekst = "   ",
            kiLoggId = null,
            lagreLikevel = false,
            feltType = "tittel"
        )
    }

    @Test
    fun `manglende loggId gir KI_VALIDERING_MANGLER`() {
        assertThatThrownBy {
            service.verifiserKiValidering(
                tekst = "Noe tekst",
                kiLoggId = null,
                lagreLikevel = false,
                feltType = "tittel"
            )
        }
            .isInstanceOf(KiValideringsException::class.java)
            .hasMessageContaining("KI_VALIDERING_MANGLER")
    }

    @Test
    fun `tom loggId gir KI_VALIDERING_MANGLER`() {
        assertThatThrownBy {
            service.verifiserKiValidering(
                tekst = "Noe tekst",
                kiLoggId = "",
                lagreLikevel = false,
                feltType = "tittel"
            )
        }
            .isInstanceOf(KiValideringsException::class.java)
            .hasMessageContaining("KI_VALIDERING_MANGLER")
    }

    @Test
    fun `ugyldig UUID-format gir KI_LOGG_ID_UGYLDIG`() {
        assertThatThrownBy {
            service.verifiserKiValidering(
                tekst = "Noe tekst",
                kiLoggId = "ikke-en-uuid",
                lagreLikevel = false,
                feltType = "tittel"
            )
        }
            .isInstanceOf(KiValideringsException::class.java)
            .hasMessageContaining("KI_LOGG_ID_UGYLDIG")
    }

    @Test
    fun `loggId som ikke finnes i database gir KI_LOGG_ID_UGYLDIG`() {
        assertThatThrownBy {
            service.verifiserKiValidering(
                tekst = "Noe tekst",
                kiLoggId = UUID.randomUUID().toString(),
                lagreLikevel = false,
                feltType = "tittel"
            )
        }
            .isInstanceOf(KiValideringsException::class.java)
            .hasMessageContaining("KI_LOGG_ID_UGYLDIG")
    }

    @Test
    fun `endret tekst etter validering gir KI_TEKST_ENDRET`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
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

        assertThatThrownBy {
            service.verifiserKiValidering(
                tekst = "Endret tekst",
                kiLoggId = loggId.toString(),
                lagreLikevel = false,
                feltType = "tittel"
            )
        }
            .isInstanceOf(KiValideringsException::class.java)
            .hasMessageContaining("KI_TEKST_ENDRET")
    }

    @Test
    fun `bryterRetningslinjer uten lagreLikevel gir KI_KREVER_BEKREFTELSE`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst med brudd",
                spørringFiltrert = "Tekst med brudd",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = true,
                begrunnelse = "Brudd på retningslinjer",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        assertThatThrownBy {
            service.verifiserKiValidering(
                tekst = "Tekst med brudd",
                kiLoggId = loggId.toString(),
                lagreLikevel = false,
                feltType = "tittel"
            )
        }
            .isInstanceOf(KiValideringsException::class.java)
            .hasMessageContaining("KI_KREVER_BEKREFTELSE")
    }

    @Test
    fun `bryterRetningslinjer med lagreLikevel tillates`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst med brudd",
                spørringFiltrert = "Tekst med brudd",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = true,
                begrunnelse = "Brudd på retningslinjer",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        // Skal ikke kaste exception
        service.verifiserKiValidering(
            tekst = "Tekst med brudd",
            kiLoggId = loggId.toString(),
            lagreLikevel = true,
            feltType = "tittel"
        )
    }

    @Test
    fun `gyldig validering uten brudd tillates`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Gyldig tekst",
                spørringFiltrert = "Gyldig tekst",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        // Skal ikke kaste exception
        service.verifiserKiValidering(
            tekst = "Gyldig tekst",
            kiLoggId = loggId.toString(),
            lagreLikevel = false,
            feltType = "tittel"
        )
    }

    @Test
    fun `HTML-tagger normaliseres ved sammenligning`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "<p>Tekst med HTML</p>",
                spørringFiltrert = "Tekst med HTML",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        // Skal ikke kaste exception - HTML-tagger fjernes ved sammenligning
        service.verifiserKiValidering(
            tekst = "<div>Tekst med HTML</div>",
            kiLoggId = loggId.toString(),
            lagreLikevel = false,
            feltType = "innlegg"
        )
    }

    @Test
    fun `whitespace normaliseres ved sammenligning`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst   med   whitespace",
                spørringFiltrert = "Tekst med whitespace",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        // Skal ikke kaste exception - whitespace kollapses
        service.verifiserKiValidering(
            tekst = "Tekst  med\nwhitespace",
            kiLoggId = loggId.toString(),
            lagreLikevel = false,
            feltType = "tittel"
        )
    }

    @Test
    fun `erTekstEndret returnerer true for ulik tekst`() {
        assertThat(service.erTekstEndret("Tekst 1", "Tekst 2")).isTrue()
    }

    @Test
    fun `erTekstEndret returnerer false for lik tekst`() {
        assertThat(service.erTekstEndret("Samme tekst", "Samme tekst")).isFalse()
    }

    @Test
    fun `erTekstEndret normaliserer HTML og whitespace`() {
        assertThat(service.erTekstEndret("<p>Tekst</p>", "Tekst")).isFalse()
        assertThat(service.erTekstEndret("Tekst  med   space", "Tekst med space")).isFalse()
    }

    @Test
    fun `erTekstEndret handterer null`() {
        assertThat(service.erTekstEndret(null, null)).isFalse()
        assertThat(service.erTekstEndret(null, "")).isFalse()
        assertThat(service.erTekstEndret("", null)).isFalse()
        assertThat(service.erTekstEndret(null, "Tekst")).isTrue()
        assertThat(service.erTekstEndret("Tekst", null)).isTrue()
    }

    @Test
    fun `feil feltType gir KI_FEIL_FELT_TYPE`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "innlegg",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        assertThatThrownBy {
            service.verifiserKiValidering(
                tekst = "Tekst",
                kiLoggId = loggId.toString(),
                lagreLikevel = false,
                feltType = "tittel" // Forventet tittel, men loggen er for innlegg
            )
        }
            .isInstanceOf(KiValideringsException::class.java)
            .hasMessageContaining("KI_FEIL_FELT_TYPE")
    }

    @Test
    fun `feil treffId gir KI_FEIL_TREFF`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val annetTreffId = db.opprettRekrutteringstreffIDatabase("B654321").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        assertThatThrownBy {
            service.verifiserKiValidering(
                tekst = "Tekst",
                kiLoggId = loggId.toString(),
                lagreLikevel = false,
                feltType = "tittel",
                forventetTreffId = annetTreffId // Forventet annet treff
            )
        }
            .isInstanceOf(KiValideringsException::class.java)
            .hasMessageContaining("KI_FEIL_TREFF")
    }

    @Test
    fun `riktig treffId valideres ok`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        // Skal ikke kaste exception
        service.verifiserKiValidering(
            tekst = "Tekst",
            kiLoggId = loggId.toString(),
            lagreLikevel = false,
            feltType = "tittel",
            forventetTreffId = treffId
        )
    }

    @Test
    fun `null forventetTreffId hopper over treffId-validering`() {
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456").somUuid
        val loggId = kiLoggRepository.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = "tittel",
                spørringFraFrontend = "Tekst",
                spørringFiltrert = "Tekst",
                systemprompt = "prompt",
                ekstraParametreJson = null,
                bryterRetningslinjer = false,
                begrunnelse = "OK",
                kiNavn = "azure-openai",
                kiVersjon = "2025-01-01",
                svartidMs = 123
            )
        )

        // Skal ikke kaste exception - forventetTreffId er null
        service.verifiserKiValidering(
            tekst = "Tekst",
            kiLoggId = loggId.toString(),
            lagreLikevel = false,
            feltType = "tittel",
            forventetTreffId = null
        )
    }
}
