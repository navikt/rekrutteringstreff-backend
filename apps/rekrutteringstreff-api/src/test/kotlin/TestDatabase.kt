package no.nav.toi.rekrutteringstreff


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.atOslo
import no.nav.toi.jobbsoker.*
import no.nav.toi.nowOslo
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource


class TestDatabase {

    fun opprettRekrutteringstreffIDatabase(
        navIdent: String = "Original navident",
        tittel: String = "Original Tittel",
    ): TreffId {
        val originalDto = OpprettRekrutteringstreffInternalDto(
            tittel = tittel,
            opprettetAvNavkontorEnhetId = "Original Kontor",
            opprettetAvPersonNavident = navIdent,
            opprettetAvTidspunkt = nowOslo().minusDays(10),
        )
        return RekrutteringstreffRepository(dataSource).opprett(originalDto)
    }

    fun slettAlt() {
        dataSource.connection.use {
            it.prepareStatement("DELETE FROM arbeidsgiver").executeUpdate()
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

    fun hentAlleArbeidsgviere(): List<Arbeidsgiver> {
        val sql = """
            SELECT ag.orgnr, ag.orgnavn, rt.id as treff_id
            FROM arbeidsgiver ag
            JOIN rekrutteringstreff rt ON ag.treff_db_id = rt.db_id
            ORDER BY ag.db_id ASC;
        """.trimIndent()
        dataSource.connection.use {
            val resultSet = it.prepareStatement(sql).executeQuery()
            return generateSequence {
                if (resultSet.next()) konverterTilArbeidsgiver(resultSet)
                else null
            }.toList()
        }
    }

    fun hentAlleJobbsøkere(): List<Jobbsøker> {
        val sql = """
            SELECT js.fodselsnummer, js.fornavn, js.etternavn, rt.id as treff_id
            FROM jobbsoker js
            JOIN rekrutteringstreff rt ON js.treff_db_id = rt.db_id
            ORDER BY js.db_id ASC;
        """.trimIndent()
        dataSource.connection.use {
            val resultSet = it.prepareStatement(sql).executeQuery()
            return generateSequence {
                if (resultSet.next()) konverterTilJobbsøker(resultSet)
                else null
            }.toList()
        }
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

    private fun konverterTilArbeidsgiver(resultSet: ResultSet) = Arbeidsgiver(
        treffId = TreffId(resultSet.getString("treff_id")),
        orgnr = Orgnr(resultSet.getString("orgnr")),
        orgnavn = Orgnavn(resultSet.getString("orgnavn"))
    )

    private fun konverterTilJobbsøker(resultSet: ResultSet) = Jobbsøker(
        treffId = TreffId(resultSet.getString("treff_id")),
        fødselsnummer = Fødselsnummer(resultSet.getString("fodselsnummer")),
        fornavn = Fornavn(resultSet.getString("fornavn")),
        etternavn = Etternavn(resultSet.getString("etternavn"))
    )


    fun leggTilArbeidsgivere(arbeidsgivere: List<Arbeidsgiver>) {
        val repo = ArbeidsgiverRepository(dataSource)
        arbeidsgivere.forEach {
            val arbeidsgiver = LeggTilArbeidsgiver(it.orgnr, it.orgnavn)
            repo.leggTil(arbeidsgiver, it.treffId)
        }
    }

    fun leggTilJobbsøkere(jobbsøkere: List<Jobbsøker>) {
        val repo = JobbsøkerRepository(dataSource)
        jobbsøkere.forEach {
            val jobbsøker = LeggTilJobbsøker(it.fødselsnummer, it.fornavn, it.etternavn)
            repo.leggTil(jobbsøker, it.treffId)
        }
    }

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
