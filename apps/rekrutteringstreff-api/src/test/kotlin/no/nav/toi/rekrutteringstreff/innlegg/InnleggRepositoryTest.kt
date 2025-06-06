package no.nav.toi.rekrutteringstreff.innlegg

import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class InnleggRepositoryTest {

    private val db = TestDatabase()
    private lateinit var repo: InnleggRepository
    private lateinit var treffId: TreffId

    @BeforeEach
    fun setup() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        repo = InnleggRepository(db.dataSource)
        treffId = db.opprettRekrutteringstreffIDatabase()
    }

    @AfterEach
    fun cleanup() {
        db.slettAlt()
    }

    @Test
    fun `oppdater skal oppdatere et eksisterende innlegg`() {
        val innlegg = repo.opprett(
            treffId,
            OpprettInnleggRequestDto(
                tittel = "Original Title",
                opprettetAvPersonNavident = "A123456",
                opprettetAvPersonNavn = "Original Name",
                opprettetAvPersonBeskrivelse = "Original Description",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = "<p>Original Content</p>"
            ),
            "A123456"
        )

        val updated = repo.oppdater(
            innleggId = innlegg.id,
            treffId = treffId,
            dto = OpprettInnleggRequestDto(
                tittel = "Updated Title",
                opprettetAvPersonNavident = "A123456",
                opprettetAvPersonNavn = "Updated Name",
                opprettetAvPersonBeskrivelse = "Updated Description",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = "<p>Updated Content</p>"
            )
        )

        assertThat(updated.id).isEqualTo(innlegg.id)
        assertThat(updated.tittel).isEqualTo("Updated Title")
        assertThat(updated.opprettetAvPersonNavn).isEqualTo("Updated Name")
        assertThat(updated.htmlContent).isEqualTo("<p>Updated Content</p>")
    }

    @Test
    fun `oppdater skal kaste feil for ikke-eksisterende treffId`() {
        val nonExistentTreffId = TreffId(UUID.randomUUID())
        val innleggId = UUID.randomUUID()

        val exception = assertThrows<IllegalStateException> {
            repo.oppdater(
                innleggId = innleggId,
                treffId = nonExistentTreffId,
                dto = OpprettInnleggRequestDto(
                    tittel = "Test Title",
                    opprettetAvPersonNavident = "A123456",
                    opprettetAvPersonNavn = "Test Name",
                    opprettetAvPersonBeskrivelse = "Test Description",
                    sendesTilJobbsokerTidspunkt = null,
                    htmlContent = "<p>Test Content</p>"
                )
            )
        }
        assertThat(exception.message).contains("Treff $nonExistentTreffId finnes ikke")
    }

    @Test
    fun `oppdater skal kaste feil for ikke-eksisterende innleggId`() {
        val nonExistentInnleggId = UUID.randomUUID()

        val exception = assertThrows<IllegalStateException> {
            repo.oppdater(
                innleggId = nonExistentInnleggId,
                treffId = treffId,
                dto = OpprettInnleggRequestDto(
                    tittel = "Test Title",
                    opprettetAvPersonNavident = "A123456",
                    opprettetAvPersonNavn = "Test Name",
                    opprettetAvPersonBeskrivelse = "Test Description",
                    sendesTilJobbsokerTidspunkt = null,
                    htmlContent = "<p>Test Content</p>"
                )
            )
        }
        assertThat(exception.message).contains("Update failed or not found")
    }
}