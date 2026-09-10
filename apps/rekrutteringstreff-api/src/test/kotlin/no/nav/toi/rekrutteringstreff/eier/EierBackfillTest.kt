package no.nav.toi.rekrutteringstreff.eier

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EierBackfillTest {
    private val postgres = PostgreSQLContainer("postgres:17.2-alpine")
    private lateinit var dataSource: HikariDataSource
    private val opprettet = Instant.parse("2026-01-01T10:00:00Z")

    // Testen verifiserer backfill-migreringen V16. Senere migreringer holdes utenfor
    // slik at snapshot-sammenligningene ikke plukker opp nye kolonner (f.eks. V17s sok_tsv).
    private val SISTE_MIGRERING_UNDER_TEST = "16"

    @BeforeAll
    fun setup() {
        postgres.start()
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 5
        })
    }

    @BeforeEach
    fun migrerTilFase2() {
        // Egen container: clean skal aldri treffe databasen som andre tester bruker.
        val flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).target("15").load()
        flyway.clean()
        flyway.migrate()
    }

    @AfterAll
    fun teardown() {
        dataSource.close()
        postgres.stop()
    }

    @Test
    fun `backfill bruker siste kontorhendelse og første eierhendelse for dagens eiere`() {
        val treffId = opprettHistoriskTreff(listOf("A123456", "B654321", "B654321", null), listOf("0315", "1201"))
        val annetTreff = opprettHistoriskTreff(listOf("B654321"), listOf("0301"))
        val førsteTillegg = opprettet.plusSeconds(60)
        leggTilHendelse(treffId, "KONTOR_LAGT_TIL", "A123456", "1201", opprettet.plusSeconds(10))
        leggTilHendelse(treffId, "KONTOR_LAGT_TIL", "B654321", "0315", opprettet.plusSeconds(20))
        leggTilHendelse(treffId, "KONTOR_LAGT_TIL", "B654321", "1201", opprettet.plusSeconds(30))
        leggTilHendelse(treffId, "KONTOR_LAGT_TIL", "B654321", null, opprettet.plusSeconds(40))
        leggTilHendelse(annetTreff, "KONTOR_LAGT_TIL", "B654321", "0301", opprettet.plusSeconds(50))
        leggTilHendelse(treffId, "EIER_LAGT_TIL", "Z999999", "B654321", førsteTillegg)
        leggTilHendelse(treffId, "EIER_LAGT_TIL", "B654321", "B654321", opprettet.plusSeconds(120))
        leggTilHendelse(treffId, "EIER_LAGT_TIL", "C987654", "C987654", førsteTillegg)
        val treffFør = snapshot("rekrutteringstreff")
        val hendelserFør = snapshot("rekrutteringstreff_hendelse")

        migrer()

        val eiere = hentEiere(treffId)
        assertThat(eiere.map { it.navIdent }).containsExactly("A123456", "B654321")
        assertThat(eiere.map { it.kontor }).containsExactly("1201", "1201")
        assertThat(eiere.map { it.tidspunkt }).containsExactly(opprettet, førsteTillegg)
        assertThat(eiere).allSatisfy {
            assertThat(it.navn).isNull()
            assertThat(it.lagtTilAv).isEqualTo("migrering-V16")
        }
        assertThat(hentEiere(annetTreff).single().kontor).isEqualTo("0301")
        assertThat(snapshot("rekrutteringstreff")).isEqualTo(treffFør)
        assertThat(snapshot("rekrutteringstreff_hendelse")).isEqualTo(hendelserFør)
    }

    @Test
    fun `backfill bruker oppretterkontor og entydig kontor men lar uavklarte kontorer stå tomme`() {
        val oppretter = opprettHistoriskTreff(listOf("A123456", "B654321"), listOf("0315", "1201"))
        val entydig = opprettHistoriskTreff(listOf("B654321"), listOf(null, "1201"), status = "SLETTET")
        val utenKontor = opprettHistoriskTreff(listOf("B654321"), emptyList())
        val utenOppretterkontor = opprettHistoriskTreff(listOf("A123456"), listOf("1201"), oppretterkontor = null)
        val utenEiere = opprettHistoriskTreff(listOf(null), listOf("0315"))
        val tomtEierarray = opprettHistoriskTreff(emptyList(), listOf("0315"))

        migrer()

        assertThat(hentEiere(oppretter).map { it.kontor }).containsExactly("0315", null)
        assertThat(hentEiere(entydig).single().kontor).isEqualTo("1201")
        assertThat(hentEiere(utenKontor).single().kontor).isNull()
        assertThat(hentEiere(utenOppretterkontor).single().kontor).isEqualTo("1201")
        assertThat(hentEiere(utenEiere)).isEmpty()
        assertThat(hentEiere(tomtEierarray)).isEmpty()
        assertThat(hentEiere(entydig).single().tidspunkt).isEqualTo(opprettet)
    }

    @Test
    fun `backfill bevarer dual write-rader med ID og metadata og kan gjentas uten endringer`() {
        val treffId = opprettHistoriskTreff(listOf("A123456"), listOf("0315"))
        EierRepository(dataSource).leggTil(treffId, "B654321", "1201")
        leggTilHendelse(treffId, "KONTOR_LAGT_TIL", "B654321", "0315", opprettet)
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.executeUpdate("UPDATE rekrutteringstreff_eier SET eier_navn = 'Testnavn'")
            }
        }
        val eksisterende = hentEiere(treffId).single()

        migrer()

        assertThat(hentEiere(treffId).single { it.navIdent == "B654321" }).isEqualTo(eksisterende)
        val etterBackfill = snapshot("rekrutteringstreff_eier")
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            kjørBackfill(connection)
            connection.commit()
        }
        assertThat(snapshot("rekrutteringstreff_eier")).isEqualTo(etterBackfill)
        assertThat(flyway().migrate().migrationsExecuted).isZero()
    }

    @Test
    fun `feil ved utfylling av entydig kontor ruller tilbake hele backfillen`() {
        val treffId = opprettHistoriskTreff(listOf("B654321"), listOf("0315"))
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute("ALTER TABLE rekrutteringstreff_eier ADD CONSTRAINT test_kontor CHECK (kontor_enhetid IS NULL)")
            }
        }

        assertThatThrownBy { migrer() }.isInstanceOf(FlywayException::class.java)

        assertThat(hentEiere(treffId)).isEmpty()
        assertThat(flyway().info().current().version.version).isEqualTo("15")
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute("ALTER TABLE rekrutteringstreff_eier DROP CONSTRAINT test_kontor")
            }
        }
        migrer()
        assertThat(hentEiere(treffId).single().kontor).isEqualTo("0315")
    }

    @Test
    fun `backfill venter på trefflåsen og gjeninnfører ikke en samtidig slettet eier`() {
        val treffId = opprettHistoriskTreff(listOf("A123456", "B654321"), listOf("0315"))
        val repository = EierRepository(dataSource)
        val pool = Executors.newSingleThreadExecutor()
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                repository.hent(connection, treffId, forUpdate = true)
                val backfill = pool.submit { migrer() }
                try {
                    ventPåVentendeLås("rekrutteringstreff", "AccessExclusiveLock")
                    // Migreringen må ikke ha låst eiertabellen før trefftabellen.
                    assertThat(repository.slett(connection, treffId, "B654321")).isTrue()
                    connection.commit()
                    backfill.get(15, TimeUnit.SECONDS)
                } finally {
                    connection.rollback()
                }
            }
        } finally {
            pool.shutdownNow()
            check(pool.awaitTermination(15, TimeUnit.SECONDS))
        }

        assertThat(hentEiere(treffId).map { it.navIdent }).containsExactly("A123456")
    }

    @Test
    fun `dual write venter til backfillen er committed og oppdaterer begge lagringsformer`() {
        val treffId = opprettHistoriskTreff(listOf("A123456", "B654321"), listOf("0315"))
        val service = EierService(EierRepository(dataSource), RekrutteringstreffRepository(dataSource), dataSource)
        val pool = Executors.newSingleThreadExecutor()
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                kjørBackfill(connection)
                val endring = pool.submit {
                    service.slettEier(treffId, "B654321", "A123456")
                    service.leggTilEierMedKontor(treffId, "C987654", "1201")
                }
                try {
                    ventPåVentendeLås("rekrutteringstreff", "RowShareLock")
                    connection.commit()
                    endring.get(15, TimeUnit.SECONDS)
                } finally {
                    connection.rollback()
                }
            }
        } finally {
            pool.shutdownNow()
            check(pool.awaitTermination(15, TimeUnit.SECONDS))
        }

        assertThat(hentEiere(treffId).map { it.navIdent }).containsExactly("A123456", "C987654")
        val treff = RekrutteringstreffRepository(dataSource).hent(treffId)!!
        assertThat(treff.eiere).containsExactlyInAnyOrder("A123456", "C987654")
        assertThat(treff.kontorer).containsExactlyInAnyOrder("0315", "1201")
    }

    private fun flyway(target: String = SISTE_MIGRERING_UNDER_TEST) =
        Flyway.configure().dataSource(dataSource).target(target).load()

    private fun migrer() {
        flyway().migrate()
    }

    private fun kjørBackfill(connection: Connection) {
        val sql = requireNotNull(javaClass.getResourceAsStream("/db/migration/V16__rekrutteringstreff_eier_backfill.sql"))
            .bufferedReader().use { it.readText() }
        connection.createStatement().use { it.execute(sql) }
    }

    private fun opprettHistoriskTreff(
        eiere: List<String?>,
        kontorer: List<String?>,
        status: String = "PUBLISERT",
        oppretterkontor: String? = "0315",
    ): TreffId {
        val treffId = TreffId(UUID.randomUUID())
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO rekrutteringstreff (
                    id, tittel, status, opprettet_av_person_navident, opprettet_av_kontor_enhetid,
                    opprettet_av_tidspunkt, eiere, kontorer
                ) VALUES (?, 'Backfill-test', ?, 'A123456', ?, ?, ?, ?)
                """.trimIndent()
            ).use {
                it.setObject(1, treffId.somUuid)
                it.setString(2, status)
                it.setString(3, oppretterkontor)
                it.setTimestamp(4, Timestamp.from(opprettet))
                it.setArray(5, connection.createArrayOf("text", eiere.toTypedArray()))
                it.setArray(6, connection.createArrayOf("text", kontorer.toTypedArray()))
                it.executeUpdate()
            }
        }
        return treffId
    }

    private fun leggTilHendelse(treffId: TreffId, type: String, aktør: String, subjekt: String?, tidspunkt: Instant) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO rekrutteringstreff_hendelse (
                    id, rekrutteringstreff_id, tidspunkt, hendelsestype,
                    opprettet_av_aktortype, aktøridentifikasjon, subjekt_id
                )
                SELECT gen_random_uuid(), rekrutteringstreff_id, ?, ?, 'ARRANGØR', ?, ?
                FROM rekrutteringstreff WHERE id = ?
                """.trimIndent()
            ).use {
                it.setTimestamp(1, Timestamp.from(tidspunkt))
                it.setString(2, type)
                it.setString(3, aktør)
                it.setString(4, subjekt)
                it.setObject(5, treffId.somUuid)
                it.executeUpdate()
            }
        }
    }

    private data class Eierrad(
        val databaseId: Long,
        val id: UUID,
        val navIdent: String,
        val kontor: String?,
        val navn: String?,
        val tidspunkt: Instant,
        val lagtTilAv: String?,
    )

    private fun hentEiere(treffId: TreffId): List<Eierrad> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT e.* FROM rekrutteringstreff_eier e
            JOIN rekrutteringstreff rt USING (rekrutteringstreff_id)
            WHERE rt.id = ? ORDER BY e.nav_ident
            """.trimIndent()
        ).use {
            it.setObject(1, treffId.somUuid)
            it.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(Eierrad(
                            rs.getLong("rekrutteringstreff_eier_id"), rs.getObject("id", UUID::class.java),
                            rs.getString("nav_ident"), rs.getString("kontor_enhetid"), rs.getString("eier_navn"),
                            rs.getTimestamp("lagt_til_tidspunkt").toInstant(), rs.getString("lagt_til_av"),
                        ))
                    }
                }
            }
        }
    }

    private fun snapshot(tabell: String): List<String> = dataSource.connection.use { connection ->
        connection.createStatement().use {
            it.executeQuery("SELECT to_jsonb(t)::text FROM $tabell t ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }

    private fun ventPåVentendeLås(tabell: String, modus: String) {
        val frist = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_locks WHERE relation = ?::regclass AND mode = ? AND NOT granted)"
            ).use {
                it.setString(1, tabell)
                it.setString(2, modus)
                while (System.nanoTime() < frist) {
                    val venter = it.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
                    if (venter) return
                    Thread.sleep(10)
                }
            }
        }
        error("Ingen ventende $modus på $tabell innen fristen")
    }
}
