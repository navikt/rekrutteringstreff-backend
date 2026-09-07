package no.nav.toi.rekrutteringstreff

import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Sikrer at slettAlt() tømmer *alle* db-tabeller, ikke bare de som tilfeldigvis ligger i en manuelt hardkodet liste.
 *
 * Den hardkodede lista av tabellnavn som var i bruk til 21. august 2026 avvek etterhvert fra skjemaet:
 * V14 la til åtte tabeller som aldri ble lagt inn i lista, og fem av dem har fremmednøkkel mot jobbsoker.
 * Uten disse testene merkes det ikke før noen skriver den første testen som bruker en av tabellene — og da feiler
 * oppryddingen i en helt annen test enn den som forårsaket problemet.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlettAltTest {

    companion object {
        private val db = TestDatabase()

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
        }
    }

    @AfterEach
    fun tearDown() {
        db.slettAlt()
    }

    @Test
    fun `slettAlt tømmer tabeller som ikke sto i den gamle hardkodede lista`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        db.leggTilJobbsøkereMedHendelse(
            listOf(LeggTilJobbsøker(Fødselsnummer("12345678901"), Fornavn("Ola"), Etternavn("Nordmann"))),
            treffId,
        )
        leggTilRomtildeling()
        assertThat(antallRader("jobbsoker_romtildeling")).isEqualTo(1)

        db.slettAlt()

        assertThat(antallRader("jobbsoker_romtildeling")).isZero()
        assertThat(antallRader("jobbsoker")).isZero()
        assertThat(antallRader("rekrutteringstreff")).isZero()
    }

    @Test
    fun `slettAlt tømmer alle tabeller i skjemaet unntatt flyway_schema_history`() {
        val treffId = db.opprettRekrutteringstreffIDatabase()
        db.leggTilJobbsøkereMedHendelse(
            listOf(LeggTilJobbsøker(Fødselsnummer("10987654321"), Fornavn("Kari"), Etternavn("Hansen"))),
            treffId,
        )

        db.slettAlt()

        val tabellerMedData = tabellnavn().filter { antallRader(it) > 0 }
        assertThat(tabellerMedData)
            .withFailMessage("Disse tabellene var ikke tomme etter slettAlt(): %s", tabellerMedData)
            .isEmpty()
        assertThat(antallRader("flyway_schema_history")).isPositive()
    }

    private fun tabellnavn(): List<String> = db.dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeQuery(
                """
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                """.trimIndent()
            ).use { rs -> generateSequence { if (rs.next()) rs.getString("tablename") else null }.toList() }
        }
    }

    private fun antallRader(tabell: String): Int = db.dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeQuery("""SELECT count(*) FROM "$tabell"""").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    private fun leggTilRomtildeling() = db.dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                INSERT INTO jobbsoker_romtildeling (rekrutteringstreff_id, jobbsoker_id, romnummer)
                SELECT rekrutteringstreff_id, jobbsoker_id, 1 FROM jobbsoker LIMIT 1
                """.trimIndent()
            )
        }
    }
}
