package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import java.time.ZonedDateTime

class TestDatabase {
    fun slettAlt() {
        TODO("Not yet implemented")
    }

    fun hentRekrutteringstreff(): List<TestRekrutteringstreff> {
        TODO("Not yet implemented")
    }

    class TestDatabase {

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

                    (lokalPostgres as PostgreSQLContainer<*>).apply { this.start() }
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
}

class TestRekrutteringstreff(val tittel: String, val fraTid: ZonedDateTime, val tilTid: ZonedDateTime, val sted: String)
