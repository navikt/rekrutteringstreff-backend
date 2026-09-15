package no.nav.toi.rekrutteringstreff.no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.minside.JacksonConfig
import no.nav.toi.rekrutteringstreff.*
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneId
import java.util.*
import javax.sql.DataSource

class TestDatabase {

    private val rekrutteringstreffRepository by lazy { RekrutteringstreffRepository(dataSource) }
    private val jobbsøkerRepository by lazy { JobbsøkerRepository(dataSource, JacksonConfig.mapper) }
    private val arbeidsgiverRepository by lazy { ArbeidsgiverRepository(dataSource, JacksonConfig.mapper) }

    fun opprettRekrutteringstreffIDatabase(
        navIdent: String = "Original navident",
        tittel: String = "Original Tittel",
        kategori: RekrutteringstreffKategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF,
    ): TreffId {
        return dataSource.connection.use { connection ->
            val (treffId, treffDbId) = rekrutteringstreffRepository.opprett(
                connection,
                OpprettRekrutteringstreffInternalDto(
                    tittel = tittel,
                    opprettetAvNavkontorEnhetId = "Original Kontor",
                    opprettetAvPersonNavident = navIdent,
                    opprettetAvTidspunkt = nowOslo().minusDays(10),
                    kategori = kategori
                )
            )
            rekrutteringstreffRepository.leggTilHendelse(
                connection,
                treffDbId,
                RekrutteringstreffHendelsestype.OPPRETTET,
                AktørType.ARRANGØR,
                navIdent
            )
            treffId
        }
    }

    /**
     * Tømmer alle tabeller i public-skjemaet. Funksjonen er komplisert for å oppnå to ting:
     * 1) Bedre ytelse/høyere hastighet i teardown av ytelsestester med mye data
     * 2) Nye tabeller som opprettes i framtida skal også bli slettet, uten at vi trenger å hardkode dem inn i noen
     * liste over tabeller som skal slettes.
     *
     * Tabellista utledes fra skjemaet via pg_tables i stedet for å være hardkodet, fordi en
     * hardkodet liste drifter fra skjemaet: V14 la til åtte tabeller som aldri ble lagt inn her,
     * og fem av dem har fremmednøkkel mot jobbsoker.
     * Første test som tar dem i bruk ville fått FK-brudd (Foreign Key, fremmednøkkel).
     */
    fun slettAlt() = dataSource.connection.use { conn ->
        /**
         * En «slett alt» som utleder tabellene fra skjemaet er farligere enn en hardkodet liste hvis
         * dataSource noen gang peker et annet sted enn Testcontainers.
         */
        fun krevTestdatabase(conn: Connection) {
            val url = conn.metaData.url
            require("localhost" in url || "127.0.0.1" in url) {
                "slettAlt() nektet: forventet testdatabase, men jdbcUrl var $url"
            }
        }

        /*
         * Utenfor en transaksjon er SET LOCAL en stille no-op — Postgres gir bare en WARNING,
         * som JDBC svelger. Da ville slettingen blitt rekkefølgeavhengig igjen og feilet med
         * uforklarlige FK-brudd. Les tilbake verdien så det feiler her i stedet.
         */
        fun verifiserReplicaModusSatt(conn: Connection) {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SHOW session_replication_role").use { rs ->
                    rs.next()
                    check(rs.getString(1) == "replica") {
                        "session_replication_role ble ikke satt. Kjører slettAlt() utenfor en transaksjon?"
                    }
                }
            }
        }

        fun hentAlleTabellnavn(conn: Connection): List<String> =
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    """
                    SELECT tablename
                    FROM pg_tables
                    WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                    """.trimIndent()
                ).use { rs ->
                    generateSequence { if (rs.next()) rs.getString("tablename") else null }.toList()
                }
            }

        krevTestdatabase(conn)
        val opprinneligAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            /*
             * session_replication_role='replica' slår av fremmednøkkel-triggerne for denne
             * transaksjonen. Det gir to ting:
             *   1. Slettingen blir rekkefølgeuavhengig, som er det som gjør det mulig å utlede
             *      tabellista fra skjemaet i det hele tatt.
             *   2. Postgres slipper å kjøre én RI-sjekk per rad per fremmednøkkel. jobbsoker
             *      har sju barnetabeller, så 600 000 rader ga 4,2 millioner trigger-kall.
             *
             * SET LOCAL — ikke SET — er kritisk: verdien nullstilles automatisk ved COMMIT eller
             * ROLLBACK. Med vanlig SET ville «replica» blitt liggende igjen på tilkoblingen når
             * HikariCP resirkulerer den, og neste test ville kjørt uten fremmednøkkelsjekk uten
             * at noen merket det. Krever superbruker, som Testcontainers-brukeren er.
             */
            conn.createStatement().use { it.execute("SET LOCAL session_replication_role = 'replica'") }
            verifiserReplicaModusSatt(conn)

            val tabeller = hentAlleTabellnavn(conn)

            conn.createStatement().use { stmt ->
                tabeller.forEach {
                    stmt.addBatch("""DELETE FROM "$it"""")
                }
                stmt.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = opprinneligAutoCommit
        }
    }

    fun hentAlleRekrutteringstreff(): List<Rekrutteringstreff> =
        rekrutteringstreffRepository.hentAlle().sortedBy { it.id.somString }

    fun leggTilArbeidsgivere(arbeidsgivere: List<Arbeidsgiver>) {
        arbeidsgivere.forEach { ag ->
            dataSource.connection.use { connection ->
                val arbeidsgiverTreffId = arbeidsgiverRepository.opprettArbeidsgiver(
                    connection,
                    LeggTilArbeidsgiver(ag.orgnr, ag.orgnavn, emptyList(), ag.gateadresse, ag.postnummer, ag.poststed),
                    ag.treffId
                )
                arbeidsgiverRepository.leggTilHendelse(
                    connection,
                    arbeidsgiverTreffId,
                    ArbeidsgiverHendelsestype.OPPRETTET,
                    AktørType.ARRANGØR,
                    "testperson"
                )
            }
        }
    }

    fun leggTilJobbsøkere(jobbsøkere: List<Jobbsøker>) {
        jobbsøkere
            .groupBy { it.treffId }
            .forEach { (treffId, jobbsøkereListe) ->
                dataSource.connection.use { connection ->
                    val leggTilJobbsøkere = jobbsøkereListe.map { js ->
                        LeggTilJobbsøker(
                            fødselsnummer = js.fødselsnummer,
                            fornavn = js.fornavn,
                            etternavn = js.etternavn,
                            kontor = js.kontor,
                            veilederNavn = js.veilederNavn,
                            veilederNavIdent = js.veilederNavIdent
                        )
                    }
                    val tidspunkt = Instant.now()
                    val opprettedeJobbsøkere = jobbsøkerRepository.leggTil(connection, leggTilJobbsøkere, treffId)
                    val personTreffIder = opprettedeJobbsøkere.map { it.personTreffId }
                    jobbsøkerRepository.leggTilOpprettetHendelser(connection, personTreffIder, "testperson", tidspunkt)
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
    ).apply {
        Flyway.configure()
            .dataSource(this)
            .load()
            .migrate()
    }
}
