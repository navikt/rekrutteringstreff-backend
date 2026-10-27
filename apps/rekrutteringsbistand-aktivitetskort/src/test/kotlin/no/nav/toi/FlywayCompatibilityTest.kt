package no.nav.toi

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.JsonNode
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class FlywayCompatibilityTest {

    // Added before upgrading Flyway from 11.20.3 to 13.11.1 to remove its Jackson 2 dependency.
    // Flyway now declares optional Jackson 3 databind. Keep checking migration over existing
    // data, not only startup with an empty database, regardless of its JSON library.
    // This test checks schema progression and restart behavior,
    // but every stage uses the installed Flyway version. Reading history written by an older
    // Flyway binary requires a separate old-version/new-version compatibility check.
    //
    // For future Flyway upgrades, keep the scenario and assertions. Verify the resolved
    // dependencies and update the version explanation above if it no longer applies.
    // Adapt Flyway API calls only if required; do not weaken data-preservation or restart checks
    // to accommodate the upgrade, and do not retain Jackson 2 solely for this test.
    // If later SQL migrations are added, update the expected migration count/history and latest
    // schema assertions to match their intended effects, while preserving the V1-data scenario.
    // Remove this test only if equivalent existing-data migration/restart coverage replaces it,
    // or if the application no longer uses Flyway. Moving Flyway to Jackson 3 is not a reason
    // to delete it. This scenario does not directly exercise Flyway's internal JSON handling.
    @Test
    fun `should preserve existing activity cards when migrating and restarting`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine")).use { postgres ->
            postgres.start()
            val initialFlyway = flywayConfiguration(postgres).target("1").load()

            assertThat(initialFlyway.migrate().migrationsExecuted).isEqualTo(1)
            assertThat(initialFlyway.info().current().version.version).isEqualTo("1")

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                seedVersionOneData(connection)
                val originalData = storedData(connection)

                val upgradedFlyway = flywayConfiguration(postgres).load()
                assertThat(upgradedFlyway.migrate().migrationsExecuted).isEqualTo(2)
                assertMigrationHistory(upgradedFlyway)

                // Compare every original column, including JSON and nulls. The new type column
                // is checked separately because V2 intentionally adds it to the existing row.
                assertThat(storedData(connection)).isEqualTo(originalData)
                assertVersionThreeSchema(connection)

                val restartedFlyway = flywayConfiguration(postgres).load()
                assertThat(restartedFlyway.migrate().migrationsExecuted).isZero()
                assertMigrationHistory(restartedFlyway)
                assertThat(storedData(connection)).isEqualTo(originalData)
                assertVersionThreeSchema(connection)
            }
        }
    }

    private fun flywayConfiguration(postgres: PostgreSQLContainer) = Flyway.configure()
        .loggers("slf4j")
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun assertMigrationHistory(flyway: Flyway) {
        flyway.validate()
        assertThat(flyway.info().applied().map { it.version.version }).containsExactly("1", "2", "3")
        assertThat(flyway.info().current().version.version).isEqualTo("3")
        assertThat(flyway.info().pending()).isEmpty()
    }

    private fun seedVersionOneData(connection: Connection) {
        val cardId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val syntheticFnr = "00000000000"
        connection.prepareStatement(
            """
            INSERT INTO aktivitetskort (
                message_id, aktivitetskort_id, fnr, tittel, aktivitets_status,
                detaljer, handlinger, etiketter, action_type, avtalt_med_nav,
                endret_av, endret_av_type, endret_tidspunkt
            ) VALUES (?, ?, ?, ?, ?, ?::json, ?::json, ?::json, ?, ?, ?, ?, ?::timestamptz)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.fromString("00000000-0000-0000-0000-000000000002"))
            statement.setObject(2, cardId)
            statement.setString(3, syntheticFnr)
            statement.setString(4, "Existing activity card")
            statement.setString(5, "PLANLAGT")
            statement.setString(6, """[{"label":"Location","verdi":"Test venue"}]""")
            statement.setString(7, """[{"tekst":"Open event","subtekst":"","url":"https://example.test/event","lenkeType":"INTERN"}]""")
            statement.setString(8, """[{"tekst":"Invited","sentiment":"NEUTRAL"}]""")
            statement.setString(9, "UPSERT_AKTIVITETSKORT_V1")
            statement.setBoolean(10, true)
            statement.setString(11, "test-system")
            statement.setString(12, "SYSTEM")
            statement.setString(13, "2026-01-15T10:00:00Z")
            assertThat(statement.executeUpdate()).isEqualTo(1)
        }
        connection.prepareStatement(
            "INSERT INTO rekrutteringstreff (aktivitetskort_id, fnr, rekrutteringstreff_id) VALUES (?, ?, ?)"
        ).use { statement ->
            statement.setObject(1, cardId)
            statement.setString(2, syntheticFnr)
            statement.setObject(3, UUID.fromString("00000000-0000-0000-0000-000000000003"))
            assertThat(statement.executeUpdate()).isEqualTo(1)
        }
    }

    private fun storedData(connection: Connection): Pair<JsonNode, JsonNode> {
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT to_jsonb(a) - 'aktivitetskort_type' AS card, to_jsonb(r) AS recruitment_event
                FROM aktivitetskort a
                JOIN rekrutteringstreff r ON r.aktivitetskort_id = a.aktivitetskort_id AND r.fnr = a.fnr
                """.trimIndent()
            ).use { rows ->
                assertThat(rows.next()).isTrue()
                val card = jackson3ObjectMapper.readTree(rows.getString("card"))
                val event = jackson3ObjectMapper.readTree(rows.getString("recruitment_event"))
                assertThat(rows.next()).isFalse()
                return card to event
            }
        }
    }

    private fun assertVersionThreeSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT aktivitetskort_type FROM aktivitetskort").use { rows ->
                assertThat(rows.next()).isTrue()
                assertThat(rows.getString("aktivitetskort_type")).isEqualTo("REKRUTTERINGSTREFF")
                assertThat(rows.next()).isFalse()
            }
            statement.executeQuery(
                """
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'aktivitetskort'
                    AND column_name = 'aktivitetskort_type'
                """.trimIndent()
            ).use { rows ->
                assertThat(rows.next()).isTrue()
                assertThat(rows.getString("column_default")).isNull()
                assertThat(rows.next()).isFalse()
            }
            statement.executeQuery("SELECT count(*) FROM delt_stilling").use { rows ->
                assertThat(rows.next()).isTrue()
                assertThat(rows.getInt(1)).isZero()
            }
        }
    }
}
