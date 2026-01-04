package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.rekrutteringstreff.Rekrutteringstreff
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TreffId
import org.flywaydb.core.Flyway
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
        val nyTreffId = TreffId(UUID.randomUUID())
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO rekrutteringstreff(id, tittel, status, opprettet_av_person_navident,
                                     opprettet_av_kontor_enhetid, opprettet_av_tidspunkt, eiere, sist_endret, sist_endret_av)
                VALUES (?,?,?,?,?,?,?,?,?)
                """
            ).apply {
                var i = 0
                setObject(++i, nyTreffId.somUuid)
                setString(++i, tittel)
                setString(++i, RekrutteringstreffStatus.UTKAST.name)
                setString(++i, navIdent)
                setString(++i, "Original Kontor")
                setTimestamp(++i, java.sql.Timestamp.from(nowOslo().minusDays(10).toInstant()))
                setArray(++i, connection.createArrayOf("text", arrayOf(navIdent)))
                setTimestamp(++i, java.sql.Timestamp.from(java.time.Instant.now()))
                setString(++i, navIdent)
            }.executeUpdate()

            // Legg til OPPRETTET-hendelse
            val treffDbId = connection.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?")
                .apply { setObject(1, nyTreffId.somUuid) }
                .executeQuery()
                .let { rs -> if (rs.next()) rs.getLong(1) else error("Treff ikke funnet") }

            connection.prepareStatement(
                """
                INSERT INTO rekrutteringstreff_hendelse
                  (id, rekrutteringstreff_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
                VALUES (?, ?, now(), ?, ?, ?)
                """
            ).apply {
                setObject(1, UUID.randomUUID())
                setLong(2, treffDbId)
                setString(3, RekrutteringstreffHendelsestype.OPPRETTET.name)
                setString(4, AktørType.ARRANGØR.name)
                setString(5, navIdent)
            }.executeUpdate()
        }
        return nyTreffId
    }

    fun slettAlt() = dataSource.connection.use {c ->
        listOf(
            "DELETE FROM aktivitetskort_polling",
            "DELETE FROM jobbsoker_hendelse",
            "DELETE FROM arbeidsgiver_hendelse",
            "DELETE FROM rekrutteringstreff_hendelse",
            "DELETE FROM naringskode",
            "DELETE FROM innlegg",
            "DELETE FROM arbeidsgiver",
            "DELETE FROM jobbsoker",
            "DELETE FROM ki_spørring_logg",
            "DELETE FROM rekrutteringstreff"
        ).forEach { c.prepareStatement(it).executeUpdate() }
    }

    fun oppdaterRekrutteringstreff(eiere: List<String>, id: TreffId) = dataSource.connection.use {
        it.prepareStatement("UPDATE rekrutteringstreff SET eiere = ? WHERE id = ?").apply {
            setArray(1, connection.createArrayOf("text", eiere.toTypedArray()))
            setObject(2, id.somUuid)
        }.executeUpdate()
    }

    fun hentAlleRekrutteringstreff(): List<Rekrutteringstreff> = dataSource.connection.use {
        val rs = it.prepareStatement("SELECT * FROM rekrutteringstreff ORDER BY id ASC").executeQuery()
        generateSequence { if (rs.next()) konverterTilRekrutteringstreff(rs) else null }.toList()
    }

    fun hentEiere(id: TreffId): List<String> = dataSource.connection.use {
        val rs = it.prepareStatement("SELECT eiere FROM rekrutteringstreff WHERE id = ?").apply {
            setObject(1, id.somUuid)
        }.executeQuery()
        if (rs.next()) (rs.getArray("eiere").array as Array<*>).map(Any?::toString) else emptyList()
    }

    fun hentAlleArbeidsgivere(): List<Arbeidsgiver> = dataSource.connection.use {
        val sql = """
            SELECT ag.orgnr, ag.orgnavn, rt.id as treff_id
              FROM arbeidsgiver ag
              JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
             ORDER BY ag.arbeidsgiver_id
        """.trimIndent()
        val rs = it.prepareStatement(sql).executeQuery()
        generateSequence { if (rs.next()) konverterTilArbeidsgiver(rs) else null }.toList()
    }

    fun hentJobbsøkerHendelser(treff: TreffId): List<JobbsøkerHendelse> = dataSource.connection.use { connection ->
        val sql = """
            SELECT jh.id,
                   jh.tidspunkt,
                   jh.hendelsestype,
                   jh.opprettet_av_aktortype,
                   jh.aktøridentifikasjon
              FROM jobbsoker_hendelse jh
              JOIN jobbsoker js   ON jh.jobbsoker_id = js.jobbsoker_id
              JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
             WHERE rt.id = ?
             ORDER BY jh.tidspunkt
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                generateSequence {
                    if (rs.next()) JobbsøkerHendelse(
                        id                    = UUID.fromString(rs.getString("id")),
                        tidspunkt             = rs.getTimestamp("tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
                        hendelsestype         = JobbsøkerHendelsestype.valueOf(rs.getString("hendelsestype")),
                        opprettetAvAktørType  = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                        aktørIdentifikasjon   = rs.getString("aktøridentifikasjon")
                    ) else null
                }.toList()
            }
        }
    }

    fun hentArbeidsgiverHendelser(treff: TreffId): List<ArbeidsgiverHendelse> = dataSource.connection.use { connection ->
        val sql = """
            SELECT ah.id,
                   ah.tidspunkt,
                   ah.hendelsestype,
                   ah.opprettet_av_aktortype,
                   ah.aktøridentifikasjon
              FROM arbeidsgiver_hendelse ah
              JOIN arbeidsgiver ag ON ah.arbeidsgiver_id = ag.arbeidsgiver_id
              JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
             WHERE rt.id = ?
             ORDER BY ah.tidspunkt
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                generateSequence {
                    if (rs.next()) ArbeidsgiverHendelse(
                        id                    = UUID.fromString(rs.getString("id")),
                        tidspunkt             = rs.getTimestamp("tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
                        hendelsestype         = ArbeidsgiverHendelsestype.valueOf(rs.getString("hendelsestype")),
                        opprettetAvAktørType  = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                        aktøridentifikasjon   = rs.getString("aktøridentifikasjon")
                    ) else null
                }.toList()
            }
        }
    }

    fun hentAlleJobbsøkere(): List<Jobbsøker> = dataSource.connection.use {
        val sql = """
            SELECT js.fodselsnummer,
                   js.fornavn,
                   js.etternavn,
                   js.navkontor,
                   js.veileder_navn,
                   js.veileder_navident,
                   rt.id as treff_id
              FROM jobbsoker js
              JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
             ORDER BY js.jobbsoker_id
        """.trimIndent()
        val rs = it.prepareStatement(sql).executeQuery()
        generateSequence { if (rs.next()) konverterTilJobbsøker(rs) else null }.toList()
    }

    private fun konverterTilRekrutteringstreff(rs: ResultSet) = Rekrutteringstreff(
        id                       = TreffId(rs.getObject("id", UUID::class.java)),
        tittel                   = rs.getString("tittel"),
        beskrivelse             = rs.getString("beskrivelse"),
        fraTid                   = rs.getTimestamp("fratid")?.toInstant()?.atOslo(),
        tilTid                   = rs.getTimestamp("tiltid")?.toInstant()?.atOslo(),
        svarfrist                   = rs.getTimestamp("svarfrist")?.toInstant()?.atOslo(),
        gateadresse               = rs.getString("gateadresse"),
        postnummer                = rs.getString("postnummer"),
        poststed                  = rs.getString("poststed"),
        kommune = rs.getString("kommune"),
        kommunenummer = rs.getString("kommunenummer"),
        fylke = rs.getString("fylke"),
        fylkesnummer = rs.getString("fylkesnummer"),
        status                   = RekrutteringstreffStatus.valueOf(rs.getString("status")),
        opprettetAvPersonNavident= rs.getString("opprettet_av_person_navident"),
        opprettetAvNavkontorEnhetId = rs.getString("opprettet_av_kontor_enhetid"),
        opprettetAvTidspunkt     = rs.getTimestamp("opprettet_av_tidspunkt").toInstant().atOslo(),
        eiere = (rs.getArray("eiere").array as Array<String>).toList(),
        sistEndret = rs.getTimestamp("sist_endret").toInstant().atOslo(),
        sistEndretAv = rs.getString("sist_endret_av") ?: "Ukjent"
    )

    private fun konverterTilArbeidsgiver(rs: ResultSet) = Arbeidsgiver(
        arbeidsgiverTreffId      = ArbeidsgiverTreffId(UUID.fromString(rs.getString("id"))),
        treffId = TreffId(rs.getString("treff_id")),
        orgnr   = Orgnr(rs.getString("orgnr")),
        orgnavn = Orgnavn(rs.getString("orgnavn")),
        status = ArbeidsgiverStatus.valueOf(rs.getString("status")),
        gateadresse = rs.getString("gateadresse"),
        postnummer  = rs.getString("postnummer"),
        poststed    = rs.getString("poststed"),
    )

    private fun konverterTilJobbsøker(rs: ResultSet) = Jobbsøker(
        personTreffId             = PersonTreffId(UUID.fromString(rs.getString("fodselsnummer"))),
        treffId        = TreffId(rs.getString("treff_id")),
        fødselsnummer  = Fødselsnummer(rs.getString("fodselsnummer")),
        fornavn        = Fornavn(rs.getString("fornavn")),
        etternavn      = Etternavn(rs.getString("etternavn")),
        navkontor      = rs.getString("navkontor")?.let(::Navkontor),
        veilederNavn   = rs.getString("veileder_navn")?.let(::VeilederNavn),
        veilederNavIdent = rs.getString("veileder_navident")?.let(::VeilederNavIdent),
        status = JobbsøkerStatus.LAGT_TIL,
    )

    fun leggTilArbeidsgivere(arbeidsgivere: List<Arbeidsgiver>) {
        dataSource.connection.use { connection ->
            arbeidsgivere.forEach { ag ->
                // Hent treff_db_id
                val treffDbId = connection.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?")
                    .apply { setObject(1, ag.treffId.somUuid) }
                    .executeQuery().let {
                        if (it.next()) it.getLong(1) else error("Treff ${ag.treffId} finnes ikke")
                    }

                // Legg til arbeidsgiver
                val arbeidsgiverDbId = connection.prepareStatement(
                    "INSERT INTO arbeidsgiver (id, rekrutteringstreff_id, orgnr, orgnavn, status, gateadresse, postnummer, poststed) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING arbeidsgiver_id"
                ).apply {
                    setObject(1, ag.arbeidsgiverTreffId.somUuid)
                    setLong(2, treffDbId)
                    setString(3, ag.orgnr.asString)
                    setString(4, ag.orgnavn.asString)
                    setString(5, ArbeidsgiverStatus.AKTIV.name)
                    setString(6, ag.gateadresse)
                    setString(7, ag.postnummer)
                    setString(8, ag.poststed)
                }.executeQuery().let {
                    if (it.next()) it.getLong(1) else error("Kunne ikke legge til arbeidsgiver")
                }

                // Legg til OPPRETTET-hendelse
                connection.prepareStatement(
                    """
                    INSERT INTO arbeidsgiver_hendelse
                      (id, arbeidsgiver_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
                    VALUES (?, ?, now(), ?, ?, ?)
                    """
                ).apply {
                    setObject(1, UUID.randomUUID())
                    setLong(2, arbeidsgiverDbId)
                    setString(3, ArbeidsgiverHendelsestype.OPPRETTET.name)
                    setString(4, AktørType.ARRANGØR.name)
                    setString(5, "testperson")
                }.executeUpdate()
            }
        }
    }

    fun leggTilJobbsøkere(jobbsøkere: List<Jobbsøker>) {
        dataSource.connection.use { connection ->
            jobbsøkere.forEach { js ->
                // Hent treff_db_id
                val treffDbId = connection.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?")
                    .apply { setObject(1, js.treffId.somUuid) }
                    .executeQuery().let {
                        if (it.next()) it.getLong(1) else error("Treff ${js.treffId} finnes ikke")
                    }

                // Legg til jobbsøker
                val jobbsøkerDbId = connection.prepareStatement(
                    """
                    INSERT INTO jobbsoker
                      (id, rekrutteringstreff_id, fodselsnummer, fornavn, etternavn,
                       navkontor, veileder_navn, veileder_navident, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING jobbsoker_id
                    """
                ).apply {
                    setObject(1, js.personTreffId.somUuid)
                    setLong(2, treffDbId)
                    setString(3, js.fødselsnummer.asString)
                    setString(4, js.fornavn.asString)
                    setString(5, js.etternavn.asString)
                    setString(6, js.navkontor?.asString)
                    setString(7, js.veilederNavn?.asString)
                    setString(8, js.veilederNavIdent?.asString)
                    setString(9, JobbsøkerStatus.LAGT_TIL.name)
                }.executeQuery().let {
                    if (it.next()) it.getLong(1) else error("Kunne ikke legge til jobbsøker")
                }

                // Legg til OPPRETTET-hendelse
                connection.prepareStatement(
                    """
                    INSERT INTO jobbsoker_hendelse
                      (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
                    VALUES (?, ?, now(), ?, ?, ?)
                    """
                ).apply {
                    setObject(1, UUID.randomUUID())
                    setLong(2, jobbsøkerDbId)
                    setString(3, JobbsøkerHendelsestype.OPPRETTET.name)
                    setString(4, AktørType.ARRANGØR.name)
                    setString(5, "testperson")
                }.executeUpdate()
            }
        }
    }

    fun leggTilRekrutteringstreffHendelse(treffId: TreffId, hendelsestype: RekrutteringstreffHendelsestype, aktørIdent: String) =
        dataSource.connection.use { c ->
            val treffDbId = c.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?").apply {
                setObject(1, treffId.somUuid)
            }.executeQuery().let {
                if (it.next()) it.getLong(1) else error("Treff $treffId finnes ikke i test-DB")
            }

            c.prepareStatement(
                """
                INSERT INTO rekrutteringstreff_hendelse
                  (id, rekrutteringstreff_id, tidspunkt,
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


    companion object {
        private var lokalPostgres: PostgreSQLContainer<*>? = null
        private fun getLokalPostgres(): PostgreSQLContainer<*> =
            lokalPostgres ?: PostgreSQLContainer(DockerImageName.parse("postgres:17.2-alpine"))
                .withDatabaseName("dbname")
                .withUsername("username")
                .withPassword("pwd")
                .also { it.start(); lokalPostgres = it }
    }

    val dataSource: DataSource = HikariDataSource(
        HikariConfig().apply {
            val pg = getLokalPostgres()
            jdbcUrl                   = pg.jdbcUrl
            username                  = pg.username
            password                  = pg.password
            driverClassName           = "org.postgresql.Driver"
            minimumIdle               = 1
            maximumPoolSize           = 10
            initializationFailTimeout = 5_000
            validate()
        }
    ).apply {
        Flyway.configure()
            .dataSource(this)
            .load()
            .migrate()
    }
}
