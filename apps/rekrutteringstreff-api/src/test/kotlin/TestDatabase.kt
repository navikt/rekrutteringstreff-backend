package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.jobbsoker.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneId
import java.time.ZonedDateTime
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

    fun opprettRekrutteringstreffMedAlleFelter(
        navIdent: String = "Z999999",
        tittel: String = "Et komplett rekrutteringstreff",
        beskrivelse: String = "En fin beskrivelse av treffet",
        fraTid: ZonedDateTime = nowOslo().plusDays(7),
        tilTid: ZonedDateTime = nowOslo().plusDays(7).plusHours(2),
        svarfrist: ZonedDateTime = nowOslo().plusDays(3),
        gateadresse: String = "Testgata 123",
        postnummer: String = "0484",
        poststed: String = "OSLO",
        status: String = "ÅPEN",
        opprettetAvNavkontorEnhetId: String = "0315"
    ): TreffId {
        val treffId = opprettRekrutteringstreffIDatabase(
            navIdent = navIdent,
            tittel = tittel
        )

        dataSource.connection.use {
            val sql = """
            UPDATE rekrutteringstreff
            SET beskrivelse = ?,
                fratid = ?,
                tiltid = ?,
                svarfrist = ?,
                gateadresse = ?,
                postnummer = ?,
                poststed = ?,
                status = ?,
                opprettet_av_kontor_enhetid = ?
            WHERE id = ?
        """.trimIndent()
            it.prepareStatement(sql).apply {
                setString(1, beskrivelse)
                setTimestamp(2, Timestamp.from(fraTid.toInstant()))
                setTimestamp(3, Timestamp.from(tilTid.toInstant()))
                setTimestamp(4, Timestamp.from(svarfrist.toInstant()))
                setString(5, gateadresse)
                setString(6, postnummer)
                setString(7, poststed)
                setString(8, status)
                setString(9, opprettetAvNavkontorEnhetId)
                setObject(10, treffId.somUuid)
            }.executeUpdate()
        }
        return treffId
    }

    fun slettAlt() = dataSource.connection.use {
        it.prepareStatement("DELETE FROM aktivitetskort_polling").executeUpdate()
        it.prepareStatement("DELETE FROM jobbsoker_hendelse").executeUpdate()
        it.prepareStatement("DELETE FROM arbeidsgiver_hendelse").executeUpdate()
        it.prepareStatement("DELETE FROM rekrutteringstreff_hendelse").executeUpdate()
        it.prepareStatement("DELETE FROM naringskode").executeUpdate()
        it.prepareStatement("DELETE FROM innlegg").executeUpdate()
        it.prepareStatement("DELETE FROM arbeidsgiver").executeUpdate()
        it.prepareStatement("DELETE FROM jobbsoker").executeUpdate()
        it.prepareStatement("DELETE FROM ki_spørring_logg").executeUpdate()
        it.prepareStatement("DELETE FROM rekrutteringstreff").executeUpdate()
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
            SELECT ag.id, ag.orgnr, ag.orgnavn, rt.id as treff_id
              FROM arbeidsgiver ag
              JOIN rekrutteringstreff rt ON ag.treff_db_id = rt.db_id
             ORDER BY ag.db_id
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
              JOIN jobbsoker js   ON jh.jobbsoker_db_id = js.db_id
              JOIN rekrutteringstreff rt ON js.treff_db_id = rt.db_id
             WHERE rt.id = ?
             ORDER BY jh.tidspunkt
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                generateSequence {
                    if (rs.next()) JobbsøkerHendelse(
                        id = UUID.fromString(rs.getString("id")),
                        tidspunkt = rs.getTimestamp("tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
                        hendelsestype = JobbsøkerHendelsestype.valueOf(rs.getString("hendelsestype")),
                        opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                        aktørIdentifikasjon = rs.getString("aktøridentifikasjon")
                    ) else null
                }.toList()
            }
        }
    }

    fun hentArbeidsgiverHendelser(treff: TreffId): List<ArbeidsgiverHendelse> =
        dataSource.connection.use { connection ->
            val sql = """
            SELECT ah.id,
                   ah.tidspunkt,
                   ah.hendelsestype,
                   ah.opprettet_av_aktortype,
                   ah.aktøridentifikasjon
              FROM arbeidsgiver_hendelse ah
              JOIN arbeidsgiver ag ON ah.arbeidsgiver_db_id = ag.db_id
              JOIN rekrutteringstreff rt ON ag.treff_db_id = rt.db_id
             WHERE rt.id = ?
             ORDER BY ah.tidspunkt
        """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    generateSequence {
                        if (rs.next()) ArbeidsgiverHendelse(
                            id = UUID.fromString(rs.getString("id")),
                            tidspunkt = rs.getTimestamp("tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
                            hendelsestype = ArbeidsgiverHendelsestype.valueOf(rs.getString("hendelsestype")),
                            opprettetAvAktørType = AktørType.valueOf(rs.getString("opprettet_av_aktortype")),
                            aktøridentifikasjon = rs.getString("aktøridentifikasjon")
                        ) else null
                    }.toList()
                }
            }
        }

    fun hentAlleJobbsøkere(): List<Jobbsøker> = dataSource.connection.use {
        val sql = """
            SELECT js.id,
                   js.fodselsnummer,
                   js.kandidatnummer,
                   js.fornavn,
                   js.etternavn,
                   js.navkontor,
                   js.veileder_navn,
                   js.veileder_navident,
                   rt.id as treff_id
              FROM jobbsoker js
              JOIN rekrutteringstreff rt ON js.treff_db_id = rt.db_id
             ORDER BY js.db_id
        """.trimIndent()
        val rs = it.prepareStatement(sql).executeQuery()
        generateSequence { if (rs.next()) konverterTilJobbsøker(rs) else null }.toList()
    }

    fun hentAlleNæringskoder(): List<Næringskode> = dataSource.connection.use {
        val sql = """
            SELECT nk.kode, nk.beskrivelse
              FROM naringskode nk
              JOIN arbeidsgiver ag ON ag.db_id = nk.arbeidsgiver_db_id
             ORDER BY nk.db_id
        """.trimIndent()
        val rs = it.prepareStatement(sql).executeQuery()
        generateSequence { if (rs.next()) konverterTilNæringskoder(rs) else null }.toList()
    }

    fun hentNæringskodeForArbeidsgiverPåTreff(treffId: TreffId, orgnr: Orgnr): List<Næringskode> = dataSource.connection.use {
        val sql = """
            SELECT nk.kode, nk.beskrivelse
              FROM naringskode nk
              JOIN arbeidsgiver ag ON ag.db_id = nk.arbeidsgiver_db_id
              JOIN rekrutteringstreff rt ON ag.treff_db_id = rt.db_id
             WHERE rt.id = ? AND ag.orgnr = ?
             ORDER BY nk.db_id
        """.trimIndent()
        val ps = it.prepareStatement(sql).apply {
            setObject(1, treffId.somUuid)
            setString(2, orgnr.asString)
        }
        val rs = ps.executeQuery()
        generateSequence { if (rs.next()) konverterTilNæringskoder(rs) else null }.toList()
    }

    private fun konverterTilRekrutteringstreff(rs: ResultSet) = Rekrutteringstreff(
        id = TreffId(rs.getObject("id", UUID::class.java)),
        tittel = rs.getString("tittel"),
        beskrivelse = rs.getString("beskrivelse"),
        fraTid = rs.getTimestamp("fratid")?.toInstant()?.atOslo(),
        tilTid = rs.getTimestamp("tiltid")?.toInstant()?.atOslo(),
        svarfrist = rs.getTimestamp("svarfrist")?.toInstant()?.atOslo(),
        gateadresse = rs.getString("gateadresse"),
        postnummer = rs.getString("postnummer"),
        poststed = rs.getString("poststed"),
        status = rs.getString("status"),
        opprettetAvPersonNavident = rs.getString("opprettet_av_person_navident"),
        opprettetAvNavkontorEnhetId = rs.getString("opprettet_av_kontor_enhetid"),
        opprettetAvTidspunkt = rs.getTimestamp("opprettet_av_tidspunkt").toInstant().atOslo()
    )

    private fun konverterTilArbeidsgiver(rs: ResultSet) = Arbeidsgiver(
        arbeidsgiverTreffId = ArbeidsgiverTreffId(rs.getObject("id", UUID::class.java)),
        treffId = TreffId(rs.getString("treff_id")),
        orgnr = Orgnr(rs.getString("orgnr")),
        orgnavn = Orgnavn(rs.getString("orgnavn"))
    )

    private fun konverterTilNæringskoder(rs: ResultSet) = Næringskode(
        kode = rs.getString("kode"),
        beskrivelse = rs.getString("beskrivelse")
    )

    private fun konverterTilJobbsøker(rs: ResultSet) = Jobbsøker(
        personTreffId = PersonTreffId(rs.getObject("id", UUID::class.java)),
        treffId = TreffId(rs.getString("treff_id")),
        fødselsnummer = Fødselsnummer(rs.getString("fodselsnummer")),
        kandidatnummer = rs.getString("kandidatnummer")?.let(::Kandidatnummer),
        fornavn = Fornavn(rs.getString("fornavn")),
        etternavn = Etternavn(rs.getString("etternavn")),
        navkontor = rs.getString("navkontor")?.let(::Navkontor),
        veilederNavn = rs.getString("veileder_navn")?.let(::VeilederNavn),
        veilederNavIdent = rs.getString("veileder_navident")?.let(::VeilederNavIdent)
    )


    fun leggTilArbeidsgivere(
        arbeidsgivere: List<Arbeidsgiver>,
        næringskoderPerOrgnr: Map<Orgnr, List<Næringskode>> = emptyMap()
    ) {
        val repo = ArbeidsgiverRepository(dataSource, JacksonConfig.mapper)
        arbeidsgivere.forEach { ag ->
            val næringskoder = næringskoderPerOrgnr[ag.orgnr].orEmpty()
            repo.leggTil(
                LeggTilArbeidsgiver(
                    ag.orgnr,
                    ag.orgnavn,
                    næringskoder
                ),
                ag.treffId,
                "testperson")
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

    fun leggTilRekrutteringstreffHendelse(
        treffId: TreffId,
        hendelsestype: RekrutteringstreffHendelsestype,
        aktørIdent: String
    ) =
        dataSource.connection.use { c ->
            val treffDbId = c.prepareStatement("SELECT db_id FROM rekrutteringstreff WHERE id = ?").apply {
                setObject(1, treffId.somUuid)
            }.executeQuery().let {
                if (it.next()) it.getLong(1) else error("Treff $treffId finnes ikke i test-DB")
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
                setLong(2, treffDbId)
                setString(3, hendelsestype.name)
                setString(4, AktørType.ARRANGØR.name)
                setString(5, aktørIdent)
            }.executeUpdate()
        }

    fun hentFødselsnummerForJobbsøkerHendelse(hendelseId: UUID): Fødselsnummer? =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
            SELECT js.fodselsnummer
              FROM jobbsoker_hendelse jh
              JOIN jobbsoker js ON jh.jobbsoker_db_id = js.db_id
             WHERE jh.id = ?
            """
            ).use { ps ->
                ps.setObject(1, hendelseId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) Fødselsnummer(rs.getString(1)) else null
                }
            }
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
            jdbcUrl = pg.jdbcUrl
            username = pg.username
            password = pg.password
            driverClassName = "org.postgresql.Driver"
            minimumIdle = 1
            maximumPoolSize = 10
            initializationFailTimeout = 5_000
            validate()
        }
    )

    fun hentHendelser(treff: TreffId): List<RekrutteringstreffHendelse> =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                SELECT  h.id                  AS hendelse_id,
                        h.tidspunkt           AS tidspunkt,
                        h.hendelsestype       AS hendelsestype,
                        h.opprettet_av_aktortype AS aktørtype,
                        h.aktøridentifikasjon AS ident
                FROM    rekrutteringstreff_hendelse h
                JOIN    rekrutteringstreff r ON h.rekrutteringstreff_db_id = r.db_id
                WHERE   r.id = ?
                ORDER BY h.tidspunkt DESC
                """
            ).use { s ->
                s.setObject(1, treff.somUuid)
                s.executeQuery().let { rs ->
                    generateSequence {
                        if (rs.next()) RekrutteringstreffHendelse(
                            id = UUID.fromString(rs.getString("hendelse_id")),
                            tidspunkt = rs.getTimestamp("tidspunkt").toInstant().atOslo(),
                            hendelsestype = RekrutteringstreffHendelsestype.valueOf(rs.getString("hendelsestype")),
                            opprettetAvAktørType = AktørType.valueOf(rs.getString("aktørtype")),
                            aktørIdentifikasjon = rs.getString("ident")
                        ) else null
                    }.toList()
                }
            }
        }
}