package no.nav.toi.rekrutteringstreff


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.atOslo
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource
import java.sql.ResultSet
import java.util.UUID


class TestDatabase {
    fun slettAlt() {
        dataSource.connection.use {
            it.prepareStatement("DELETE FROM rekrutteringstreff").executeUpdate()
        }
    }

    fun oppdaterRekrutteringstreff(eiere: List<String>, id: TreffId) {
        dataSource.connection.use {
            it.prepareStatement("UPDATE rekrutteringstreff SET eiere = ? WHERE id = ?").apply {
                setArray(1, connection.createArrayOf("text", eiere.toTypedArray()))
                setObject(2, id.somUuid)
            }.executeUpdate()
        }
    }

    fun hentAlleRekrutteringstreff(): List<Rekrutteringstreff> {
        dataSource.connection.use {
            val resultSet = it.prepareStatement("SELECT * FROM rekrutteringstreff ORDER BY id ASC").executeQuery()
            return generateSequence {
                if (resultSet.next()) konverterTilRekrutteringstreff(resultSet)
                else null
            }.toList()
        }
    }

    fun hentEiere(id: TreffId): List<String> = dataSource.connection.use {
        val resultSet = it.prepareStatement("SELECT eiere FROM rekrutteringstreff WHERE id = ?").apply {
            setObject(1, id.somUuid)
        }.executeQuery()
        if (resultSet.next()) (resultSet.getArray("eiere").array as Array<*>).map(Any?::toString)
        else emptyList()
    }

    private fun konverterTilRekrutteringstreff(resultSet: ResultSet) = Rekrutteringstreff(
        id = TreffId(resultSet.getObject("id", UUID::class.java)),
        tittel = resultSet.getString("tittel"),
        beskrivelse = resultSet.getString("beskrivelse"),
        fraTid = resultSet.getTimestamp("fratid")?.toInstant()?.atOslo(),
        tilTid = resultSet.getTimestamp("tiltid")?.toInstant()?.atOslo(),
        sted = resultSet.getString("sted"),
        status = resultSet.getString("status"),
        opprettetAvPersonNavident = resultSet.getString("opprettet_av_person_navident"),
        opprettetAvNavkontorEnhetId = resultSet.getString("opprettet_av_kontor_enhetid"),
        opprettetAvTidspunkt = resultSet.getTimestamp("opprettet_av_tidspunkt").toInstant().atOslo()
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
}
