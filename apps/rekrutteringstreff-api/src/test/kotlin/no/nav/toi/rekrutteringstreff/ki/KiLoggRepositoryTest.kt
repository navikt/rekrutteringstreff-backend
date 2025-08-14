package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.rekrutteringstreff.*
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.sql.Connection
import java.sql.ResultSet
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KiLoggRepositoryTest {

    private val db = TestDatabase()
    private lateinit var repo: KiLoggRepository
    private lateinit var rtRepo: RekrutteringstreffRepository

    @BeforeAll
    fun setup() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        repo = KiLoggRepository(db.dataSource)
        rtRepo = RekrutteringstreffRepository(db.dataSource)
    }

    @AfterEach
    fun cleanup() {
        db.slettAlt()
    }

    @Test
    fun `insert, findById, setLagret, setManuellKontroll, listByTreff`() {
        // Arrange rekrutteringstreff and get its db_id
        val treffId = db.opprettRekrutteringstreffIDatabase("A123456", "KI-test")
        val treffDbId = hentTreffDbId(treffId)

        // Insert first entry
        val id1 = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "tittel",
                sporringFraFrontend = "Original tekst",
                sporringFiltrert = "Filtrert tekst",
                systemprompt = "system prompt",
                ekstraParametre = mapOf("temperature" to 0.2, "reasoning" to true),
                bryterRetningslinjer = true,
                begrunnelse = "Forklaring 1",
                kiNavn = "toi-gpt-4o",
                kiVersjon = "2024-12-01-preview",
                svartidMs = 123
            )
        )

        val id2 = repo.insert(
            KiLoggInsert(
                treffDbId = treffDbId,
                feltType = "innlegg",
                sporringFraFrontend = "Tekst 2",
                sporringFiltrert = "Filtrert 2",
                systemprompt = null,
                ekstraParametre = null,
                bryterRetningslinjer = false,
                begrunnelse = "Forklaring 2",
                kiNavn = "toi-gpt-4o",
                kiVersjon = "2024-12-01-preview",
                svartidMs = 321
            )
        )

        val row1 = repo.findById(id1)!!
        assertThat(row1.id).isEqualTo(id1)
        assertThat(row1.treffDbId).isEqualTo(treffDbId)
        assertThat(row1.feltType).isEqualTo("tittel")
        assertThat(row1.sporringFraFrontend).isEqualTo("Original tekst")
        assertThat(row1.sporringFiltrert).isEqualTo("Filtrert tekst")
        assertThat(row1.systemprompt).isEqualTo("system prompt")
        assertThat(row1.ekstraParametreJson).isNotNull()
        assertThat(row1.ekstraParametreJson).contains("temperature")
        assertThat(row1.bryterRetningslinjer).isTrue()
        assertThat(row1.begrunnelse).isEqualTo("Forklaring 1")
        assertThat(row1.kiNavn).isEqualTo("toi-gpt-4o")
        assertThat(row1.kiVersjon).isEqualTo("2024-12-01-preview")
        assertThat(row1.svartidMs).isEqualTo(123)
        assertThat(row1.lagret).isFalse()
        assertThat(row1.manuellKontrollBryterRetningslinjer).isNull()
        assertThat(row1.manuellKontrollUtfortAv).isNull()
        assertThat(row1.manuellKontrollTidspunkt).isNull()

        val u1 = repo.setLagret(id1, true)
        assertThat(u1).isEqualTo(1)
        assertThat(repo.findById(id1)!!.lagret).isTrue()

        val ident = "A123456"
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val u2 = repo.setManuellKontroll(id1, bryterRetningslinjer = false, utfortAv = ident, tidspunkt = now)
        assertThat(u2).isEqualTo(1)
        val afterManuell = repo.findById(id1)!!
        assertThat(afterManuell.manuellKontrollBryterRetningslinjer).isFalse()
        assertThat(afterManuell.manuellKontrollUtfortAv).isEqualTo(ident)
        assertThat(afterManuell.manuellKontrollTidspunkt).isNotNull()

        val all = repo.listByTreff(treffDbId, feltType = null, limit = 50, offset = 0)
        assertThat(all.map { it.id }).containsExactly(id2, id1) // id2 inserted last -> newest first

        val onlyTittel = repo.listByTreff(treffDbId, feltType = "tittel", limit = 50, offset = 0)
        assertThat(onlyTittel.map { it.id }).containsExactly(id1)

        val paged = repo.listByTreff(treffDbId, feltType = null, limit = 1, offset = 1)
        assertThat(paged).hasSize(1)
        assertThat(paged.first().id).isEqualTo(id1)
    }

    private fun hentTreffDbId(treffId: TreffId): Long =
        db.dataSource.connection.use { c: Connection ->
            c.prepareStatement("SELECT db_id FROM rekrutteringstreff WHERE id = ?").use { ps ->
                ps.setObject(1, treffId.somUuid)
                ps.executeQuery().use { rs: ResultSet ->
                    assertThat(rs.next()).isTrue()
                    rs.getLong(1)
                }
            }
        }
}