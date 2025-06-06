package no.nav.toi.rekrutteringstreff.innlegg

import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnleggRepositoryTest {

    private val db   = TestDatabase()
    private val repo = InnleggRepository(db.dataSource)
    private lateinit var treffId: TreffId

    @BeforeAll
    fun migrate() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
    }
    @BeforeEach
    fun setup()  {
        treffId = db.opprettRekrutteringstreffIDatabase()
    }
    @AfterEach
    fun cleanup() {
        db.slettAlt()
    }

    private fun sampleOpprett(now: ZonedDateTime = ZonedDateTime.now()) =
        OpprettInnleggRequestDto(
            tittel = "Tittel",
            opprettetAvPersonNavn = "Ola",
            opprettetAvPersonBeskrivelse = "Veileder",
            sendesTilJobbsokerTidspunkt = now.plusHours(2),
            htmlContent = "<p>x</p>"
        )

    @Test
    fun `opprett persisterer alle felter`() {
        val dto = sampleOpprett()
        val i   = repo.opprett(treffId, dto, "A123456")
        val dbi = repo.hentById(i.id)!!

        assertThat(dbi).usingRecursiveComparison()
            .ignoringFields("opprettetTidspunkt", "sistOppdatertTidspunkt")
            .isEqualTo(i)
    }

    @Test
    fun `oppdater endrer rad`() {
        val original = repo.opprett(treffId, sampleOpprett(), "A123456")

        val updated = repo.oppdater(
            original.id,
            treffId,
            OppdaterInnleggRequestDto(
                tittel = "Ny tittel",
                opprettetAvPersonNavn = "Kari",
                opprettetAvPersonBeskrivelse = "Rådgiver",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = "<p>y</p>"
            )
        )

        assertThat(updated.tittel).isEqualTo("Ny tittel")
        assertThat(updated.opprettetAvPersonNavn).isEqualTo("Kari")
        assertThat(updated.htmlContent).isEqualTo("<p>y</p>")
    }

    @Test
    fun `oppdater kaster feil når treff ikke finnes`() {
        val id = UUID.randomUUID()
        val bogusTreff = TreffId(UUID.randomUUID())
        val ex = assertThrows<IllegalStateException> {
            repo.oppdater(id, bogusTreff, sampleOpprett().let {
                OppdaterInnleggRequestDto(it.tittel, it.opprettetAvPersonNavn, it.opprettetAvPersonBeskrivelse, it.sendesTilJobbsokerTidspunkt, it.htmlContent)
            })
        }
        assertThat(ex).hasMessageContaining("Treff $bogusTreff finnes ikke")
    }

    @Test
    fun `oppdater kaster feil når innlegg ikke finnes`() {
        val ex = assertThrows<IllegalStateException> {
            repo.oppdater(UUID.randomUUID(), treffId, sampleOpprett().let {
                OppdaterInnleggRequestDto(it.tittel, it.opprettetAvPersonNavn, it.opprettetAvPersonBeskrivelse, it.sendesTilJobbsokerTidspunkt, it.htmlContent)
            })
        }
        assertThat(ex).hasMessageContaining("Update failed or not found")
    }

    @Test
    fun `slett fjerner rad`() {
        val id = repo.opprett(treffId, sampleOpprett(), "A123456").id

        assertThat(repo.slett(id)).isTrue
        assertThat(repo.hentById(id)).isNull()
    }
}
