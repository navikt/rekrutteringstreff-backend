package no.nav.toi.rekrutteringstreff.sok

import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.system.measureTimeMillis

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffSokYtelsestest {
    companion object {
        private const val ANTALL_TREFF = 50_000
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
            seedTreff(ANTALL_TREFF)
            logger.info("Genererte {} testtreff for manuell ytelsestest av rekrutteringstreff-søk", ANTALL_TREFF)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            db.slettAlt()
        }

        private fun seedTreff(antall: Int) {
            val startTid = Instant.parse("2025-01-01T00:00:00Z")
            db.dataSource.connection.use { conn ->
                conn.autoCommit = false
                conn.prepareStatement(
                    """
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
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    repeat(antall) { index ->
                        val status = when (index % 5) {
                            0 -> "PUBLISERT"
                            1 -> "UTKAST"
                            2 -> "AVLYST"
                            3 -> "FULLFØRT"
                            else -> "PUBLISERT"
                        }
                        val opprettetTid = startTid.plusSeconds(index.toLong())
                        val svarfrist = if (status == "PUBLISERT" && index % 2 == 0) {
                            Timestamp.from(startTid.minusSeconds(86_400))
                        } else {
                            Timestamp.from(startTid.plusSeconds(86_400))
                        }

                        statement.setObject(1, UUID.randomUUID())
                        statement.setString(2, "Treff $index")
                        statement.setString(3, status)
                        statement.setString(4, "A123456")
                        statement.setString(5, if (index % 3 == 0) "0315" else "1201")
                        statement.setTimestamp(6, Timestamp.from(opprettetTid))
                        statement.setTimestamp(7, svarfrist)
                        statement.setArray(8, conn.createArrayOf("text", arrayOf("A123456", "B654321")))
                        statement.setArray(9, conn.createArrayOf("text", arrayOf(if (index % 2 == 0) "0315" else "1201")))
                        statement.setTimestamp(10, Timestamp.from(opprettetTid.plusSeconds(3_600)))
                        statement.addBatch()

                        if ((index + 1) % 1_000 == 0) {
                            statement.executeBatch()
                        }
                    }
                    statement.executeBatch()
                }
                conn.commit()
            }
        }
    }

    @Test
    fun `sok med 10k genererte testtreff logger tid for warmup og endret kall`() {
        val warmupRequest = RekrutteringstreffSokRequest(
            visning = Visning.ALLE,
            sortering = Sortering.SIST_OPPDATERTE,
            side = 1,
            antallPerSide = 20,
        )
        val maltRequest = RekrutteringstreffSokRequest(
            statuser = listOf(Visningsstatus.PUBLISERT),
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
            service.sok(
                request = request,
                navIdent = "A123456",
                kontorId = "0315",
            )
        }
}