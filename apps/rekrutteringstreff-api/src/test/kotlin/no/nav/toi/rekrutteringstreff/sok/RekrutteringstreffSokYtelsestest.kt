package no.nav.toi.rekrutteringstreff.sok

import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffSokYtelsestest {
    companion object {
        private const val ANTALL_TREFF = 5_000
        private const val ANTALL_ARBEIDSGIVERE_PER_TREFF = 5
        private const val ANTALL_JOBBSOKERE_PER_TREFF = 30
        private const val WARMUP_TERSKEL_MS = 2_000L
        private const val MALT_TERSKEL_MS = 500L
        private val logger: Logger = LoggerFactory.getLogger(RekrutteringstreffSokYtelsestest::class.java)

        private val db = TestDatabase()
        private lateinit var repository: RekrutteringstreffSokRepository
        private lateinit var service: RekrutteringstreffSokService

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()
            repository = RekrutteringstreffSokRepository(db.dataSource)
            service = RekrutteringstreffSokService(repository)
            val seedingVarighetMs = measureTimeMillis { seedTreff(ANTALL_TREFF) }
            logger.info(
                "Genererte {} testtreff med {} arbeidsgivere og {} jobbsøkere per treff på {} ms",
                ANTALL_TREFF,
                ANTALL_ARBEIDSGIVERE_PER_TREFF,
                ANTALL_JOBBSOKERE_PER_TREFF,
                seedingVarighetMs,
            )
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            db.slettAlt()
        }

        /**
         * Genererer testdata på databaseserveren med generate_series. Å sende hver rad fra JDBC
         * gjorde seedingen treg, mens selve søket bare tok noen hundre millisekunder.
         */
        private fun seedTreff(antall: Int) {
            db.dataSource.connection.use { conn ->
                conn.autoCommit = false

                conn.prepareStatement(
                    """
                    WITH treff AS (
                        SELECT
                            i,
                            CASE i % 5
                                WHEN 1 THEN 'UTKAST'
                                WHEN 2 THEN 'AVLYST'
                                WHEN 3 THEN 'FULLFØRT'
                                ELSE 'PUBLISERT'
                            END AS status,
                            timestamptz '2025-01-01 00:00:00+00' AS start_tid
                        FROM generate_series(0, ? - 1) AS i
                    )
                    INSERT INTO rekrutteringstreff (
                        id,
                        tittel,
                        status,
                        opprettet_av_person_navident,
                        opprettet_av_kontor_enhetid,
                        opprettet_av_tidspunkt,
                        svarfrist,
                        eiere,
                        kontorer,
                        sist_endret
                    )
                    SELECT
                        gen_random_uuid(),
                        'Treff ' || i,
                        status,
                        'A123456',
                        CASE WHEN i % 3 = 0 THEN '0315' ELSE '1201' END,
                        start_tid + i * interval '1 second',
                        CASE
                            WHEN status = 'PUBLISERT' AND i % 2 = 0 THEN start_tid - interval '1 day'
                            ELSE start_tid + interval '1 day'
                        END,
                        ARRAY['A123456', 'B654321'],
                        ARRAY[CASE WHEN i % 2 = 0 THEN '0315' ELSE '1201' END],
                        start_tid + i * interval '1 second' + interval '1 hour'
                    FROM treff
                    """.trimIndent()
                ).use {
                    it.setInt(1, antall)
                    it.executeUpdate()
                }

                conn.createStatement().use {
                    it.executeUpdate(
                        """
                        INSERT INTO rekrutteringstreff_eier (rekrutteringstreff_id, nav_ident, kontor_enhetid)
                        SELECT rt.rekrutteringstreff_id, e.nav_ident, rt.kontorer[1]
                        FROM rekrutteringstreff rt
                        CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
                        """.trimIndent()
                    )
                }

                conn.prepareStatement(
                    """
                    INSERT INTO arbeidsgiver (rekrutteringstreff_id, orgnr, orgnavn, id)
                    SELECT rt.rekrutteringstreff_id, '99999999' || a, 'Bedrift ' || a, gen_random_uuid()
                    FROM rekrutteringstreff rt
                    CROSS JOIN generate_series(0, ? - 1) AS a
                    """.trimIndent()
                ).use {
                    it.setInt(1, ANTALL_ARBEIDSGIVERE_PER_TREFF)
                    it.executeUpdate()
                }

                conn.prepareStatement(
                    """
                    INSERT INTO jobbsoker (rekrutteringstreff_id, fodselsnummer, id)
                    SELECT rt.rekrutteringstreff_id, '1234567' || lpad(j::text, 4, '0'), gen_random_uuid()
                    FROM rekrutteringstreff rt
                    CROSS JOIN generate_series(0, ? - 1) AS j
                    """.trimIndent()
                ).use {
                    it.setInt(1, ANTALL_JOBBSOKERE_PER_TREFF)
                    it.executeUpdate()
                }

                conn.commit()
            }

            db.dataSource.connection.use { conn ->
                conn.createStatement().use {
                    it.execute("ANALYZE rekrutteringstreff, rekrutteringstreff_eier, arbeidsgiver, jobbsoker")
                }
            }
        }
    }

    @Test
    fun `sok med genererte testtreff logger tid for warmup og endret kall`() {
        val warmupRequest = RekrutteringstreffSokRequest(
            visning = Visning.ALLE,
            sortering = Sortering.SIST_OPPDATERTE,
            side = 1,
            antallPerSide = 20,
        )
        val maltRequest = RekrutteringstreffSokRequest(
            statuser = listOf(SokStatus.PUBLISERT),
            kontorer = listOf("0315"),
            visning = Visning.VALGTE_KONTORER,
            sortering = Sortering.NYESTE,
            side = 2,
            antallPerSide = 20,
        )

        logger.info("Starter warmup-kall for ytelsestest med {} genererte testtreff", ANTALL_TREFF)
        val warmupVarighetMs = målSøk(warmupRequest)
        logger.info("Warmup-kall fullførte på {} ms", warmupVarighetMs)

        assertThat(warmupVarighetMs)
            .withFailMessage(
                "Warmup-søk med %s treff brukte %s ms, som er over terskelen på %s ms",
                ANTALL_TREFF,
                warmupVarighetMs,
                WARMUP_TERSKEL_MS,
            )
            .isLessThanOrEqualTo(WARMUP_TERSKEL_MS)

        logger.info("Starter endret kall for ytelsestest med andre filter- og sorteringsparametre")
        val varighetMs = målSøk(maltRequest)
        logger.info("Endret kall fullførte på {} ms", varighetMs)

        assertThat(varighetMs)
            .withFailMessage(
                "Endret søk med %s treff brukte %s ms, som er over terskelen på %s ms",
                ANTALL_TREFF,
                varighetMs,
                MALT_TERSKEL_MS,
            )
            .isLessThanOrEqualTo(MALT_TERSKEL_MS)
    }

    private fun målSøk(request: RekrutteringstreffSokRequest): Long =
        measureTimeMillis {
            val resultat = service.sok(
                request = request,
                navIdent = "A123456",
                kontorId = "0315",
            )
            assertThat(resultat.treff).hasSize(request.antallPerSide)
            assertThat(resultat.treff).allSatisfy {
                assertThat(it.eierOgKontor.map { eier -> eier.navIdent }).containsExactlyInAnyOrder("A123456", "B654321")
                assertThat(it.eierOgKontor.map { eier -> eier.kontorEnhetId }.distinct()).hasSize(1)
            }
        }
}
