package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import java.sql.ResultSet
import java.time.ZonedDateTime

class TestDatabase {
    fun slettAlt() {
        dataSource.connection.use {
            it.prepareStatement("DELETE FROM rekrutteringstreff").executeUpdate()
        }
    }

    fun hentAlleRekrutteringstreff(): List<TestRekrutteringstreff> {
        dataSource.connection.use {
            val resultSet =
                it.prepareStatement("SELECT * FROM rekrutteringstreff ORDER BY id ASC")
                    .executeQuery()
            return generateSequence {
                if (resultSet.next()) konverterTilRekrutteringstreff(resultSet)
                else null
            }.toList()
        }
    }

    private fun konverterTilRekrutteringstreff(resultSet: ResultSet) = TestRekrutteringstreff(
        resultSet.getString("tittel"),
        resultSet.getTimestamp("fratid").toInstant().atOslo(),
        resultSet.getTimestamp("tiltid").toInstant().atOslo(),
        resultSet.getString("sted"),
        resultSet.getString("status"),
        resultSet.getString("opprettet_av_person"),
        resultSet.getString("opprettet_av_kontor")
    )

    companion object {
        private var lokalPostgres: PostgreSQLContainer<*>? = null

        fun getLokalPostgres(): PostgreSQLContainer<*> {
            return if (lokalPostgres != null) {
                lokalPostgres as PostgreSQLContainer<*>
            } else {
                lokalPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17.2-alpine"))
                    .withDatabaseName("dbname")
                    .withUsername("username")
                    .withPassword("pwd")

                (lokalPostgres as PostgreSQLContainer<*>).also(PostgreSQLContainer<*>::start)
            }
        }
    }


        val dataSource: DataSource = HikariDataSource(
            HikariConfig().apply {
                val postgres = getLokalPostgres()
                jdbcUrl = postgres.jdbcUrl
                minimumIdle = 1
                maximumPoolSize = 10
                driverClassName = "org.postgresql.Driver"
                initializationFailTimeout = 5000
                username = postgres.username
                password = postgres.password
                validate()
            })

        init {
            Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate()
        }
}

enum class TestStatus { Utkast }
class TestRekrutteringstreff(
    val tittel: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String,
    val status: String,
    val opprettetAvPerson: String,
    val opprettetAvKontor: String
)
