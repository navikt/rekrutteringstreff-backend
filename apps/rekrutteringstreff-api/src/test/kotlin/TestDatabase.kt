package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.AktørType
import no.nav.toi.Hendelsestype
import no.nav.toi.JacksonConfig
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.atOslo
import no.nav.toi.jobbsoker.*
import no.nav.toi.nowOslo
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.ResultSet
import java.time.ZoneId
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
            it.prepareStatement("DELETE FROM jobbsoker").executeUpdate()
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

    fun hentAlleArbeidsgivere(): List<Arbeidsgiver> {
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

    fun hentJobbsøkerHendelser(treff: TreffId): List<JobbsøkerHendelse> {
        val sql = """
        SELECT 
            jh.id,
            jh.tidspunkt,
            jh.hendelsestype,
            jh.opprettet_av_aktortype,
            jh.aktøridentifikasjon
        FROM jobbsoker_hendelse jh
        JOIN jobbsoker js ON jh.jobbsoker_db_id = js.db_id
        JOIN rekrutteringstreff rt ON js.treff_db_id = rt.db_id
        WHERE rt.id = ?
        ORDER BY jh.tidspunkt ASC;
    """.trimIndent()
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    return generateSequence {
                        if (rs.next()) {
                            JobbsøkerHendelse(
                                id = UUID.fromString(rs.getString("id")),
                                tidspunkt = rs.getTimestamp("tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
                                hendelsestype = Hendelsestype.valueOf(rs.getString("hendelsestype")),
                                opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                                aktørIdentifikasjon = rs.getString("aktøridentifikasjon")
                            )
                        } else null
                    }.toList()
                }
            }
        }
    }

    fun hentArbeidsgiverHendelser(treff: TreffId): List<ArbeidsgiverHendelse> {
        val sql = """
        SELECT 
            ah.id,
            ah.tidspunkt,
            ah.hendelsestype,
            ah.opprettet_av_aktortype,
            ah.aktøridentifikasjon
        FROM arbeidsgiver_hendelse ah
        JOIN arbeidsgiver ag ON ah.arbeidsgiver_db_id = ag.db_id
        JOIN rekrutteringstreff rt ON ag.treff_db_id = rt.db_id
        WHERE rt.id = ?
        ORDER BY ah.tidspunkt ASC;
    """.trimIndent()
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    return generateSequence {
                        if (rs.next()) {
                            ArbeidsgiverHendelse(
                                id = UUID.fromString(rs.getString("id")),
                                tidspunkt = rs.getTimestamp("tidspunkt")
                                    .toInstant().atZone(ZoneId.of("Europe/Oslo")),
                                hendelsestype = Hendelsestype.valueOf(rs.getString("hendelsestype")),
                                opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                                aktøridentifikasjon = rs.getString("aktøridentifikasjon")
                            )
                        } else null
                    }.toList()
                }
            }
        }
    }

    fun hentAlleJobbsøkere(): List<Jobbsøker> {
        val sql = """
            SELECT js.fodselsnummer, js.kandidatnummer, js.fornavn, js.etternavn, js.navkontor, js.veileder_navn, js.veileder_navident, rt.id as treff_id
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

    private fun konverterTilRekrutteringstreff(rs: ResultSet) = Rekrutteringstreff(
        id = TreffId(rs.getObject("id", UUID::class.java)),
        tittel = rs.getString("tittel"),
        beskrivelse = rs.getString("beskrivelse"),
        fraTid = rs.getTimestamp("fratid")?.toInstant()?.atOslo(),
        tilTid = rs.getTimestamp("tiltid")?.toInstant()?.atOslo(),
        sted = rs.getString("sted"),
        status = rs.getString("status"),
        opprettetAvPersonNavident = rs.getString("opprettet_av_person_navident"),
        opprettetAvNavkontorEnhetId = rs.getString("opprettet_av_kontor_enhetid"),
        opprettetAvTidspunkt = rs.getTimestamp("opprettet_av_tidspunkt").toInstant().atOslo()
    )

    private fun konverterTilArbeidsgiver(rs: ResultSet) = Arbeidsgiver(
        treffId = TreffId(rs.getString("treff_id")),
        orgnr = Orgnr(rs.getString("orgnr")),
        orgnavn = Orgnavn(rs.getString("orgnavn"))
    )

    private fun konverterTilJobbsøker(rs: ResultSet) = Jobbsøker(
        treffId = TreffId(rs.getString("treff_id")),
        fødselsnummer = Fødselsnummer(rs.getString("fodselsnummer")),
        kandidatnummer = rs.getString("kandidatnummer")?.let(::Kandidatnummer),
        fornavn = Fornavn(rs.getString("fornavn")),
        etternavn = Etternavn(rs.getString("etternavn")),
        navkontor = rs.getString("navkontor")?.let(::Navkontor),
        veilederNavn = rs.getString("veileder_navn")?.let(::VeilederNavn),
        veilederNavIdent = rs.getString("veileder_navident")?.let(::VeilederNavIdent)
    )

    fun leggTilArbeidsgivere(arbeidsgivere: List<Arbeidsgiver>) {
        val repo = ArbeidsgiverRepository(dataSource, JacksonConfig.mapper)
        arbeidsgivere.forEach {
            val arbeidsgiver = LeggTilArbeidsgiver(it.orgnr, it.orgnavn)
            repo.leggTil(arbeidsgiver, it.treffId, "testperson")
        }
    }

    fun leggTilJobbsøkere(jobbsøkere: List<Jobbsøker>) {
        val repo = JobbsøkerRepository(dataSource, JacksonConfig.mapper)
        jobbsøkere.forEach {
            val jobbsøker = LeggTilJobbsøker(
                it.fødselsnummer,
                it.kandidatnummer,
                it.fornavn,
                it.etternavn,
                it.navkontor,
                it.veilederNavn,
                it.veilederNavIdent
            )
            repo.leggTil(jobbsøker, it.treffId, "testperson")
        }
    }

    fun leggTilRekrutteringstreffHendelse(
        treffId: TreffId,
        hendelsestype: Hendelsestype,
        aktørIdent: String
    ) {
        dataSource.connection.use { c ->
            val treffDbId = c.prepareStatement(
                "SELECT db_id FROM rekrutteringstreff WHERE id = ?"
            ).apply {
                setObject(1, treffId.somUuid)
            }.executeQuery().let { rs ->
                if (rs.next()) rs.getLong(1)
                else error("Treff $treffId finnes ikke i test-DB")
            }

            c.prepareStatement(
                """
            INSERT INTO rekrutteringstreff_hendelse
              (id, rekrutteringstreff_db_id, tidspunkt,
               hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
            VALUES (?, ?, now(), ?, ?, ?)
            """.trimIndent()
            ).apply {
                setObject(1, UUID.randomUUID())
                setLong  (2, treffDbId)
                setString(3, hendelsestype.name)
                setString(4, AktørType.ARRANGØR.name)
                setString(5, aktørIdent)
            }.executeUpdate()
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