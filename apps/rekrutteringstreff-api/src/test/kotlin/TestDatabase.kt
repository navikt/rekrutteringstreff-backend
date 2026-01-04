package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
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

    /**
     * Legger til jobbsøkere med OPPRETTET-hendelse via repository.
     */
    fun leggTilJobbsøkereMedHendelse(jobbsøkere: List<LeggTilJobbsøker>, treffId: TreffId, opprettetAv: String = "testperson") {
        dataSource.connection.use { connection ->
            val jobbsøkerDbIds = jobbsøkerRepository.leggTil(connection, jobbsøkere, treffId)
            jobbsøkerRepository.leggTilOpprettetHendelserForJobbsøkereDbId(connection, jobbsøkerDbIds, opprettetAv)
        }
    }

    /**
     * Inviterer jobbsøkere via repository - legger til INVITERT-hendelse og oppdaterer status.
     */
    fun inviterJobbsøkere(personTreffIds: List<PersonTreffId>, treffId: TreffId, inviterende: String = "testperson") {
        dataSource.connection.use { connection ->
            jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                connection,
                JobbsøkerHendelsestype.INVITERT,
                personTreffIds,
                inviterende,
                AktørType.ARRANGØR
            )
            personTreffIds.forEach { personTreffId ->
                jobbsøkerRepository.endreStatus(personTreffId.somUuid, JobbsøkerStatus.INVITERT)
            }
        }
    }

    /**
     * Registrerer svar ja via repository - legger til hendelse og oppdaterer status.
     */
    fun svarJaTilInvitasjon(fnr: Fødselsnummer, treffId: TreffId, svarAv: String) {
        dataSource.connection.use { connection ->
            val personTreffId = jobbsøkerRepository.hentPersonTreffIdFraFødselsnummer(treffId, fnr)
                ?: throw IllegalArgumentException("Jobbsøker ikke funnet for fnr og treffId")
            jobbsøkerRepository.leggTilHendelserForJobbsøkere(
                connection,
                JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
                listOf(personTreffId),
                svarAv,
                AktørType.JOBBSØKER
            )
            jobbsøkerRepository.endreStatus(personTreffId.somUuid, JobbsøkerStatus.SVART_JA)
        }
    }

    /**
     * Henter jobbsøkere via repository.
     */
    fun hentJobbsøkereViaRepository(treffId: TreffId): List<Jobbsøker> {
        return jobbsøkerRepository.hentJobbsøkere(treffId)
    }

    /**
     * Legger til arbeidsgiver med OPPRETTET-hendelse via repository.
     */
    fun leggTilArbeidsgiverMedHendelse(input: LeggTilArbeidsgiver, treffId: TreffId, opprettetAv: String = "testperson") {
        dataSource.connection.use { connection ->
            val arbeidsgiverDbId = arbeidsgiverRepository.opprettArbeidsgiver(connection, input, treffId)
            arbeidsgiverRepository.leggTilNaringskoder(connection, arbeidsgiverDbId, input.næringskoder)
            arbeidsgiverRepository.leggTilHendelse(
                connection,
                arbeidsgiverDbId,
                ArbeidsgiverHendelsestype.OPPRETTET,
                AktørType.ARRANGØR,
                opprettetAv
            )
        }
    }

    /**
     * Markerer arbeidsgiver som slettet via repository.
     */
    fun markerArbeidsgiverSlettet(arbeidsgiverId: UUID, treffId: TreffId, navIdent: String = "testperson"): Boolean {
        val arbeidsgiver = arbeidsgiverRepository.hentArbeidsgivere(treffId)
            .find { it.arbeidsgiverTreffId.somUuid == arbeidsgiverId }
            ?: return false

        dataSource.connection.use { connection ->
            arbeidsgiverRepository.markerSlettet(connection, arbeidsgiverId, navIdent)
        }
        return true
    }

    fun opprettRekrutteringstreffIDatabase(
        navIdent: String = "Original navident",
        tittel: String = "Original Tittel",
    ): TreffId {
        return dataSource.connection.use { connection ->
            val (id, _) = rekrutteringstreffRepository.opprett(
                connection,
                OpprettRekrutteringstreffInternalDto(
                    tittel = tittel,
                    opprettetAvNavkontorEnhetId = "Original Kontor",
                    opprettetAvPersonNavident = navIdent,
                    opprettetAvTidspunkt = nowOslo().minusDays(10),
                )
            )
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, id, RekrutteringstreffHendelsestype.OPPRETTET, navIdent
            )
            id
        }
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
        kommune: String = "Oslo",
        kommunenummer: String = "0301",
        fylke: String = "Oslo",
        fylkesnummer: String = "03",
        status: RekrutteringstreffStatus = RekrutteringstreffStatus.PUBLISERT,
        opprettetAvNavkontorEnhetId: String = "0315"
    ): TreffId {
        val treffId = opprettRekrutteringstreffIDatabase(
            navIdent = navIdent,
            tittel = tittel
        )

        // Bruk repository-metode for å oppdatere
        dataSource.connection.use { connection ->
            rekrutteringstreffRepository.oppdater(
                connection,
                treffId,
                no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto(
                    tittel = tittel,
                    beskrivelse = beskrivelse,
                    fraTid = fraTid,
                    tilTid = tilTid,
                    svarfrist = svarfrist,
                    gateadresse = gateadresse,
                    postnummer = postnummer,
                    poststed = poststed,
                    kommune = kommune,
                    kommunenummer = kommunenummer,
                    fylke = fylke,
                    fylkesnummer = fylkesnummer
                ),
                navIdent
            )
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.OPPDATERT, navIdent
            )

            // Status oppdateres via repository
            if (status != RekrutteringstreffStatus.UTKAST) {
                rekrutteringstreffRepository.endreStatus(connection, treffId, status)
            }
        }

        // Oppdater opprettet_av_kontor_enhetid hvis nødvendig (ingen repository-metode for dette)
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

    fun slettAlt() = dataSource.connection.use { conn ->
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
        dataSource.connection.use { connection ->
            rekrutteringstreffRepository.oppdater(
                connection,
                id,
                no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto(
                    tittel = tittel ?: gjeldende.tittel,
                    beskrivelse = beskrivelse ?: gjeldende.beskrivelse,
                    fraTid = fraTid ?: gjeldende.fraTid,
                    tilTid = tilTid ?: gjeldende.tilTid,
                    svarfrist = gjeldende.svarfrist,
                    gateadresse = gateadresse ?: gjeldende.gateadresse,
                    postnummer = postnummer ?: gjeldende.postnummer,
                    poststed = poststed ?: gjeldende.poststed,
                    kommune = gjeldende.kommune,
                    kommunenummer = gjeldende.kommunenummer,
                    fylke = gjeldende.fylke,
                    fylkesnummer = gjeldende.fylkesnummer,
                ),
                "test"
            )
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, id, RekrutteringstreffHendelsestype.OPPDATERT, "test"
            )
        }
    }

    fun registrerTreffEndretNotifikasjon(
        treffId: TreffId,
        fnr: Fødselsnummer,
        endringer: no.nav.toi.rekrutteringstreff.Rekrutteringstreffendringer
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

    fun hentAlleRekrutteringstreffSomIkkeErSlettet(): List<Rekrutteringstreff> = rekrutteringstreffRepository.hentAlleSomIkkeErSlettet()

    fun hentEiere(id: TreffId): List<String> =
        rekrutteringstreffRepository.hent(id)?.eiere ?: emptyList()

    fun hentAlleArbeidsgivere(): List<Arbeidsgiver> = dataSource.connection.use {
        // Bruker SQL her fordi repository.hentArbeidsgivere() filtrerer bort slettede
        val sql = """
            SELECT ag.id, ag.orgnr, ag.orgnavn, ag.status, ag.gateadresse, ag.postnummer, ag.poststed, rt.id as treff_id
              FROM arbeidsgiver ag
              JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
             ORDER BY ag.arbeidsgiver_id
        """.trimIndent()
        val rs = it.prepareStatement(sql).executeQuery()
        generateSequence {
            if (rs.next()) Arbeidsgiver(
                arbeidsgiverTreffId = ArbeidsgiverTreffId(rs.getObject("id", UUID::class.java)),
                treffId = TreffId(rs.getString("treff_id")),
                orgnr = Orgnr(rs.getString("orgnr")),
                orgnavn = Orgnavn(rs.getString("orgnavn")),
                status = ArbeidsgiverStatus.valueOf(rs.getString("status")),
                gateadresse = rs.getString("gateadresse"),
                postnummer = rs.getString("postnummer"),
                poststed = rs.getString("poststed"),
            ) else null
        }.toList()
    }

    fun hentJobbsøkerHendelser(treff: TreffId): List<JobbsøkerHendelse> =
        jobbsøkerRepository.hentJobbsøkere(treff)
            .flatMap { it.hendelser }
            .sortedBy { it.tidspunkt }

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

    fun hentAlleJobbsøkere(): List<Jobbsøker> =
        hentAlleRekrutteringstreff().flatMap { jobbsøkerRepository.hentJobbsøkere(it.id) }

    fun hentJobbsøkereForTreff(treffId: TreffId): List<Jobbsøker> = jobbsøkerRepository.hentJobbsøkere(treffId)

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

    private fun konverterTilNæringskoder(rs: ResultSet) = Næringskode(
        kode = rs.getString("kode"),
        beskrivelse = rs.getString("beskrivelse")
    )

    fun leggTilArbeidsgivere(
        arbeidsgivere: List<Arbeidsgiver>,
        næringskoderPerOrgnr: Map<Orgnr, List<Næringskode>> = emptyMap()
    ) {
        arbeidsgivere.forEach { ag ->
            val næringskoder = næringskoderPerOrgnr[ag.orgnr].orEmpty()
            leggTilArbeidsgiverMedHendelse(
                LeggTilArbeidsgiver(
                    ag.orgnr,
                    ag.orgnavn,
                    næringskoder,
                    ag.gateadresse,
                    ag.postnummer,
                    ag.poststed
                ),
                ag.treffId,
                "testperson")
        }
    }

    fun leggTilJobbsøkere(jobbsøkere: List<Jobbsøker>) {
        // Kan ikke bruke jobbsøkerRepository.leggTil() her fordi testene trenger å spesifisere PersonTreffId
        // Repository.leggTil() genererer nye UUID-er som gjør at inviter() feiler
        dataSource.connection.use { c ->
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
                      (id, rekrutteringstreff_id, fodselsnummer, fornavn, etternavn,
                       navkontor, veileder_navn, veileder_navident)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING jobbsoker_id
                    """.trimIndent()
                ).apply {
                    setObject(1, js.personTreffId.somUuid) // Bruk den spesifiserte UUID
                    setLong(2, treffDbId)
                    setString(3, js.fødselsnummer.asString)
                    setString(4, js.fornavn.asString)
                    setString(5, js.etternavn.asString)
                    setString(6, js.navkontor?.asString)
                    setString(7, js.veilederNavn?.asString)
                    setString(8, js.veilederNavIdent?.asString)
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
    ) = dataSource.connection.use { connection ->
        rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, hendelsestype, aktørIdent)
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

    fun endreTilTidTilPassert(treffId: TreffId, navIdent: String) {
        val rekrutteringstreff = rekrutteringstreffRepository.hent(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Treff $treffId finnes ikke")
        val oppdaterRekrutteringstreffTilTidPassert = OppdaterRekrutteringstreffDto.opprettFra(
            rekrutteringstreff.tilRekrutteringstreffDto(1,1)).copy(tilTid = nowOslo().minusDays(1)
        )
        dataSource.connection.use { connection ->
            rekrutteringstreffRepository.oppdater(connection, treffId, oppdaterRekrutteringstreffTilTidPassert, navIdent)
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.OPPDATERT, navIdent
            )
        }
    }

    fun publiser(treffId: TreffId, navIdent: String) {
        dataSource.connection.use { connection ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.PUBLISERT, navIdent
            )
            rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.PUBLISERT)
        }
    }
}
