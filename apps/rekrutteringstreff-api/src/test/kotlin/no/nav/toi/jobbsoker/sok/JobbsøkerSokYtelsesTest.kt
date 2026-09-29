package no.nav.toi.jobbsoker.sok

import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.system.measureTimeMillis

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobbsøkerSokYtelsesTest {
    companion object {
        private const val ANTALL_JOBBSØKERE = 10_000
        private const val WARMUP_TERSKEL_MS = 2_000L
        private const val MALT_TERSKEL_MS = 500L
        private const val SORTERING_TERSKEL_MS = 500L
        private val logger = LoggerFactory.getLogger(JobbsøkerSokYtelsesTest::class.java)

        private val db = TestDatabase()
        private lateinit var repository: JobbsøkerSokRepository
        private lateinit var treffId: TreffId

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()
            repository = JobbsøkerSokRepository(db.dataSource)
            val seedingVarighetMs = measureTimeMillis { treffId = opprettTreffOgSeed() }
            logger.info("Genererte {} jobbsøkere for ytelsestest av jobbsøker-søk på {} ms", ANTALL_JOBBSØKERE, seedingVarighetMs)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            db.slettAlt()
        }

        private fun opprettTreffOgSeed(): TreffId {
            val treffUuid = UUID.randomUUID()
            db.dataSource.connection.use { conn ->
                conn.autoCommit = false

                conn.prepareStatement(
                    """
                    INSERT INTO rekrutteringstreff (
                        id, tittel, status, opprettet_av_person_navident,
                        opprettet_av_kontor_enhetid, opprettet_av_tidspunkt, sist_endret
                    ) VALUES (?, 'Ytelsestest-treff', 'PUBLISERT', 'A123456', '0315', now(), now())
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setObject(1, treffUuid)
                    stmt.executeUpdate()
                }

                val treffDbId = conn.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?")
                    .apply { setObject(1, treffUuid) }
                    .executeQuery().let { it.next(); it.getLong(1) }

                conn.prepareStatement(
                    """
                    INSERT INTO jobbsoker (
                        rekrutteringstreff_id, fodselsnummer, fornavn, etternavn, kontornavn,
                        veileder_navident, veileder_navn, id, status, er_synlig
                    )
                    SELECT
                        ?,
                        lpad((i + 1)::text, 11, '0'),
                        'Fornavn' || i,
                        'Etternavn' || (i % 100),
                        (ARRAY['Nav Oslo', 'Nav Bergen', 'Nav Trondheim', 'Nav Stavanger', 'Nav Tromsø'])[i % 5 + 1],
                        'NAV' || lpad((i % 50)::text, 3, '0'),
                        'Veileder ' || (i % 50),
                        gen_random_uuid(),
                        (ARRAY['LAGT_TIL', 'INVITERT', 'SVART_JA', 'SVART_NEI'])[i % 4 + 1],
                        true
                    FROM generate_series(0, ? - 1) AS i
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setLong(1, treffDbId)
                    stmt.setInt(2, ANTALL_JOBBSØKERE)
                    stmt.executeUpdate()
                }

                // Fødselsnummeret er løpenummeret i + 1, så lagt til-tidspunktet følger samme rekkefølge som før.
                conn.prepareStatement(
                    """
                    INSERT INTO jobbsoker_hendelse (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
                    SELECT
                        gen_random_uuid(),
                        j.jobbsoker_id,
                        timestamptz '2025-01-01 00:00:00+00' + (j.fodselsnummer::int - 1) * interval '1 second',
                        'OPPRETTET',
                        'ARRANGØR',
                        'A123456'
                    FROM jobbsoker j
                    WHERE j.rekrutteringstreff_id = ?
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setLong(1, treffDbId)
                    stmt.executeUpdate()
                }
                conn.commit()
            }
            db.dataSource.connection.use { conn ->
                conn.createStatement().use { it.execute("ANALYZE rekrutteringstreff, jobbsoker, jobbsoker_hendelse") }
            }
            return TreffId(treffUuid.toString())
        }
    }
    @Test
    fun `søk med 10k jobbsøkere holder terskelverdier`() {
        val warmupRequest = JobbsøkerSøkRequest(
            side = 1,
            antallPerSide = 20,
        )
        val filteredRequest = JobbsøkerSøkRequest(
            fritekst = "fornavn42",
            status = listOf(JobbsøkerStatus.INVITERT),
            sorteringsfelt = JobbsøkerSorteringsfelt.NAVN,
            side = 1,
            antallPerSide = 20,
        )
        val sorteringRequest = JobbsøkerSøkRequest(
            sorteringsfelt = JobbsøkerSorteringsfelt.LAGT_TIL,
            side = 1,
            antallPerSide = 20,
        )

        logger.info("Warmup-kall: søk uten filtre")
        val warmupMs = measureTimeMillis { repository.sok(treffId, warmupRequest) }
        logger.info("Warmup-kall fullførte på {} ms", warmupMs)
        assertThat(warmupMs)
            .withFailMessage("Warmup-søk med %s jobbsøkere brukte %s ms, terskel %s ms", ANTALL_JOBBSØKERE, warmupMs, WARMUP_TERSKEL_MS)
            .isLessThanOrEqualTo(WARMUP_TERSKEL_MS)

        logger.info("Målt kall: fritekst + statusfilter")
        val filteredMs = measureTimeMillis { repository.sok(treffId, filteredRequest) }
        logger.info("Filtrert kall fullførte på {} ms", filteredMs)
        assertThat(filteredMs)
            .withFailMessage("Filtrert søk brukte %s ms, terskel %s ms", filteredMs, MALT_TERSKEL_MS)
            .isLessThanOrEqualTo(MALT_TERSKEL_MS)

        logger.info("Sorteringskall: lagt_til")
        val sorteringMs = measureTimeMillis { repository.sok(treffId, sorteringRequest) }
        logger.info("Sorteringskall fullførte på {} ms", sorteringMs)
        assertThat(sorteringMs)
            .withFailMessage("Sorteringssøk brukte %s ms, terskel %s ms", sorteringMs, SORTERING_TERSKEL_MS)
            .isLessThanOrEqualTo(SORTERING_TERSKEL_MS)
    }
}
