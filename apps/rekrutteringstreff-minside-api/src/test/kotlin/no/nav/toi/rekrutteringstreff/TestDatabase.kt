package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.rekrutteringstreff.Rekrutteringstreff
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
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
    ): TreffId =
        RekrutteringstreffRepository(dataSource).opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = tittel,
                opprettetAvNavkontorEnhetId = "Original Kontor",
                opprettetAvPersonNavident = navIdent,
                opprettetAvTidspunkt = nowOslo().minusDays(10),
            )
        )

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
                   js.kandidatnummer,
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
        status                   = RekrutteringstreffStatus.valueOf(rs.getString("status")),
        opprettetAvPersonNavident= rs.getString("opprettet_av_person_navident"),
        opprettetAvNavkontorEnhetId = rs.getString("opprettet_av_kontor_enhetid"),
        opprettetAvTidspunkt     = rs.getTimestamp("opprettet_av_tidspunkt").toInstant().atOslo(),
    )

    private fun konverterTilArbeidsgiver(rs: ResultSet) = Arbeidsgiver(
        arbeidsgiverTreffId      = ArbeidsgiverTreffId(UUID.fromString(rs.getString("id"))),
        treffId = TreffId(rs.getString("treff_id")),
        orgnr   = Orgnr(rs.getString("orgnr")),
        orgnavn = Orgnavn(rs.getString("orgnavn")),
        status = ArbeidsgiverStatus.valueOf(rs.getString("status")),
    )

    private fun konverterTilJobbsøker(rs: ResultSet) = Jobbsøker(
        personTreffId             = PersonTreffId(UUID.fromString(rs.getString("fodselsnummer"))),
        treffId        = TreffId(rs.getString("treff_id")),
        fødselsnummer  = Fødselsnummer(rs.getString("fodselsnummer")),
        kandidatnummer = rs.getString("kandidatnummer")?.let(::Kandidatnummer),
        fornavn        = Fornavn(rs.getString("fornavn")),
        etternavn      = Etternavn(rs.getString("etternavn")),
        navkontor      = rs.getString("navkontor")?.let(::Navkontor),
        veilederNavn   = rs.getString("veileder_navn")?.let(::VeilederNavn),
        veilederNavIdent = rs.getString("veileder_navident")?.let(::VeilederNavIdent),
        status = JobbsøkerStatus.LAGT_TIL,
    )

    fun leggTilArbeidsgivere(arbeidsgivere: List<Arbeidsgiver>) {
        val repo = ArbeidsgiverRepository(dataSource, JacksonConfig.mapper)
        arbeidsgivere.forEach {
            repo.leggTil(LeggTilArbeidsgiver(it.orgnr, it.orgnavn), it.treffId, "testperson")
        }
    }

    fun leggTilJobbsøkere(jobbsøkere: List<Jobbsøker>) {
        val repo = JobbsøkerRepository(dataSource, JacksonConfig.mapper)
        jobbsøkere
            .groupBy { it.treffId }
            .forEach { (treffId, jsListe) ->
                repo.leggTil(
                    jsListe.map {
                        LeggTilJobbsøker(
                            it.fødselsnummer,
                            it.kandidatnummer,
                            it.fornavn,
                            it.etternavn,
                            it.navkontor,
                            it.veilederNavn,
                            it.veilederNavIdent
                        )
                    },
                    treffId,
                    "testperson"
                )
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
