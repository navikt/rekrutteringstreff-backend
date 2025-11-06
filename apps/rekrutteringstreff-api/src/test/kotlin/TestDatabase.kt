package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource

class TestDatabase {

    private val rekrutteringstreffRepository by lazy { RekrutteringstreffRepository(dataSource) }
    private val jobbsøkerRepository by lazy { JobbsøkerRepository(dataSource, JacksonConfig.mapper) }
    private val arbeidsgiverRepository by lazy { ArbeidsgiverRepository(dataSource, JacksonConfig.mapper) }

    fun opprettRekrutteringstreffIDatabase(
        navIdent: String = "Original navident",
        tittel: String = "Original Tittel",
    ): TreffId {
        return rekrutteringstreffRepository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = tittel,
                opprettetAvNavkontorEnhetId = "Original Kontor",
                opprettetAvPersonNavident = navIdent,
                opprettetAvTidspunkt = nowOslo().minusDays(10),
            )
        )
    }

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
        status: RekrutteringstreffStatus = RekrutteringstreffStatus.PUBLISERT,
        opprettetAvNavkontorEnhetId: String = "0315"
    ): TreffId {
        val treffId = opprettRekrutteringstreffIDatabase(
            navIdent = navIdent,
            tittel = tittel
        )

        // Bruk repository-metode for å oppdatere
        rekrutteringstreffRepository.oppdater(
            treffId,
            no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto(
                tittel = tittel,
                beskrivelse = beskrivelse,
                fraTid = fraTid,
                tilTid = tilTid,
                svarfrist = svarfrist,
                gateadresse = gateadresse,
                postnummer = postnummer,
                poststed = poststed
            ),
            oppdatertAv = navIdent
        )

        // Status må oppdateres separat siden det ikke er del av OppdaterRekrutteringstreffDto
        if (status != RekrutteringstreffStatus.UTKAST) {
            dataSource.connection.use {
                it.prepareStatement("UPDATE rekrutteringstreff SET status = ? WHERE id = ?").apply {
                    setString(1, status.name)
                    setObject(2, treffId.somUuid)
                }.executeUpdate()
            }
        }

        // Oppdater opprettet_av_kontor_enhetid hvis nødvendig
        if (opprettetAvNavkontorEnhetId != "Original Kontor") {
            dataSource.connection.use {
                it.prepareStatement("UPDATE rekrutteringstreff SET opprettet_av_kontor_enhetid = ? WHERE id = ?").apply {
                    setString(1, opprettetAvNavkontorEnhetId)
                    setObject(2, treffId.somUuid)
                }.executeUpdate()
            }
        }

        return treffId
    }

    fun slettAlt() = dataSource.executeInTransaction { conn ->
        conn.prepareStatement("DELETE FROM aktivitetskort_polling").executeUpdate()
        conn.prepareStatement("DELETE FROM jobbsoker_hendelse").executeUpdate()
        conn.prepareStatement("DELETE FROM arbeidsgiver_hendelse").executeUpdate()
        conn.prepareStatement("DELETE FROM rekrutteringstreff_hendelse").executeUpdate()
        conn.prepareStatement("DELETE FROM naringskode").executeUpdate()
        conn.prepareStatement("DELETE FROM innlegg").executeUpdate()
        conn.prepareStatement("DELETE FROM arbeidsgiver").executeUpdate()
        conn.prepareStatement("DELETE FROM jobbsoker").executeUpdate()
        conn.prepareStatement("DELETE FROM ki_spørring_logg").executeUpdate()
        conn.prepareStatement("DELETE FROM rekrutteringstreff").executeUpdate()
    }

    fun oppdaterRekrutteringstreff(eiere: List<String>, id: TreffId) = dataSource.connection.use {
        it.prepareStatement("UPDATE rekrutteringstreff SET eiere = ? WHERE id = ?").apply {
            setArray(1, connection.createArrayOf("text", eiere.toTypedArray()))
            setObject(2, id.somUuid)
        }.executeUpdate()
    }

    fun oppdaterRekrutteringstreff(
        id: TreffId,
        tittel: String? = null,
        beskrivelse: String? = null,
        fraTid: ZonedDateTime? = null,
        tilTid: ZonedDateTime? = null,
        gateadresse: String? = null,
        postnummer: String? = null,
        poststed: String? = null
    ) {
        // Hent gjeldende verdier
        val gjeldende = rekrutteringstreffRepository.hent(id)
            ?: throw IllegalArgumentException("Treff $id finnes ikke")

        // Bruk repository.oppdater med merge av nye og gamle verdier
        rekrutteringstreffRepository.oppdater(
            id,
            no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto(
                tittel = tittel ?: gjeldende.tittel,
                beskrivelse = beskrivelse ?: gjeldende.beskrivelse,
                fraTid = fraTid ?: gjeldende.fraTid,
                tilTid = tilTid ?: gjeldende.tilTid,
                svarfrist = gjeldende.svarfrist,
                gateadresse = gateadresse ?: gjeldende.gateadresse,
                postnummer = postnummer ?: gjeldende.postnummer,
                poststed = poststed ?: gjeldende.poststed
            ),
            oppdatertAv = "test"
        )
    }

    fun registrerTreffEndretNotifikasjon(
        treffId: TreffId,
        fnr: Fødselsnummer,
        endringer: no.nav.toi.rekrutteringstreff.dto.EndringerDto
    ) = dataSource.connection.use { conn ->
        // Hent jobbsøker_id
        val jobbsøkerId = conn.prepareStatement(
            """
            SELECT js.jobbsoker_id 
            FROM jobbsoker js
            JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
            WHERE rt.id = ? AND js.fodselsnummer = ?
            """
        ).apply {
            setObject(1, treffId.somUuid)
            setString(2, fnr.asString)
        }.executeQuery().let {
            if (it.next()) it.getLong(1) else error("Fant ikke jobbsøker for treff $treffId og fnr")
        }

        // Legg til hendelse med hendelse_data
        val hendelseDataJson = JacksonConfig.mapper.writeValueAsString(endringer)
        conn.prepareStatement(
            """
            INSERT INTO jobbsoker_hendelse 
              (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon, hendelse_data)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
            """
        ).apply {
            setObject(1, UUID.randomUUID())
            setLong(2, jobbsøkerId)
            setTimestamp(3, Timestamp.from(nowOslo().toInstant()))
            setString(4, JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON.name)
            setString(5, AktørType.ARRANGØR.name)
            setString(6, "Z123456")
            setString(7, hendelseDataJson)
        }.executeUpdate()
    }

    fun hentAlleRekrutteringstreff(): List<Rekrutteringstreff> = rekrutteringstreffRepository.hentAlle()

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
              JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
             ORDER BY js.jobbsoker_id
        """.trimIndent()
        val rs = it.prepareStatement(sql).executeQuery()
        generateSequence { if (rs.next()) konverterTilJobbsøker(rs) else null }.toList()
    }

    fun hentJobbsøkereForTreff(treffId: TreffId): List<Jobbsøker> = dataSource.connection.use {
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
              JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
             WHERE rt.id = ?
             ORDER BY js.jobbsoker_id
        """.trimIndent()
        val ps = it.prepareStatement(sql).apply {
            setObject(1, treffId.somUuid)
        }
        val rs = ps.executeQuery()
        generateSequence { if (rs.next()) konverterTilJobbsøker(rs) else null }.toList()
    }

    fun hentAlleNæringskoder(): List<Næringskode> = dataSource.connection.use {
        val sql = """
            SELECT nk.kode, nk.beskrivelse
              FROM naringskode nk
              JOIN arbeidsgiver ag ON ag.arbeidsgiver_id = nk.arbeidsgiver_id
             ORDER BY nk.naringskode_id
        """.trimIndent()
        val rs = it.prepareStatement(sql).executeQuery()
        generateSequence { if (rs.next()) konverterTilNæringskoder(rs) else null }.toList()
    }

    fun hentNæringskodeForArbeidsgiverPåTreff(treffId: TreffId, orgnr: Orgnr): List<Næringskode> = dataSource.connection.use {
        val sql = """
            SELECT nk.kode, nk.beskrivelse
              FROM naringskode nk
              JOIN arbeidsgiver ag ON ag.arbeidsgiver_id = nk.arbeidsgiver_id
              JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
             WHERE rt.id = ? AND ag.orgnr = ?
             ORDER BY nk.naringskode_id
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
        status = RekrutteringstreffStatus.valueOf(rs.getString("status")),
        opprettetAvPersonNavident = rs.getString("opprettet_av_person_navident"),
        opprettetAvNavkontorEnhetId = rs.getString("opprettet_av_kontor_enhetid"),
        opprettetAvTidspunkt = rs.getTimestamp("opprettet_av_tidspunkt").toInstant().atOslo()
    )

    private fun konverterTilArbeidsgiver(rs: ResultSet) = Arbeidsgiver(
        arbeidsgiverTreffId = ArbeidsgiverTreffId(rs.getObject("id", UUID::class.java)),
        treffId = TreffId(rs.getString("treff_id")),
        orgnr = Orgnr(rs.getString("orgnr")),
        orgnavn = Orgnavn(rs.getString("orgnavn")),
        status = ArbeidsgiverStatus.AKTIV,
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
        arbeidsgivere.forEach { ag ->
            val næringskoder = næringskoderPerOrgnr[ag.orgnr].orEmpty()
            arbeidsgiverRepository.leggTil(
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
        // Kan ikke bruke jobbsøkerRepository.leggTil() her fordi testene trenger å spesifisere PersonTreffId
        // Repository.leggTil() genererer nye UUID-er som gjør at inviter() feiler
        dataSource.executeInTransaction { c ->
            jobbsøkere.forEach { js ->
                // Hent treff_db_id
                val treffDbId = c.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?")
                    .apply { setObject(1, js.treffId.somUuid) }
                    .executeQuery().let {
                        if (it.next()) it.getLong(1) else error("Treff ${js.treffId} finnes ikke")
                    }

                // Legg til jobbsøker MED den spesifiserte PersonTreffId
                val jobbsøkerDbId = c.prepareStatement(
                    """
                    INSERT INTO jobbsoker
                      (id, rekrutteringstreff_id, fodselsnummer, kandidatnummer, fornavn, etternavn,
                       navkontor, veileder_navn, veileder_navident)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING jobbsoker_id
                    """.trimIndent()
                ).apply {
                    setObject(1, js.personTreffId.somUuid) // Bruk den spesifiserte UUID
                    setLong(2, treffDbId)
                    setString(3, js.fødselsnummer.asString)
                    setString(4, js.kandidatnummer?.asString)
                    setString(5, js.fornavn.asString)
                    setString(6, js.etternavn.asString)
                    setString(7, js.navkontor?.asString)
                    setString(8, js.veilederNavn?.asString)
                    setString(9, js.veilederNavIdent?.asString)
                }.executeQuery().let {
                    if (it.next()) it.getLong(1) else error("Kunne ikke legge til jobbsøker")
                }

                // Legg til OPPRETTET hendelse
                c.prepareStatement(
                    """
                    INSERT INTO jobbsoker_hendelse
                      (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
                    VALUES (?, ?, now(), ?, ?, ?)
                    """.trimIndent()
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

    fun leggTilRekrutteringstreffHendelse(
        treffId: TreffId,
        hendelsestype: RekrutteringstreffHendelsestype,
        aktørIdent: String
    ) =
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
              JOIN jobbsoker js ON jh.jobbsoker_id = js.jobbsoker_id
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
        rekrutteringstreffRepository.hentHendelser(treff)
}
