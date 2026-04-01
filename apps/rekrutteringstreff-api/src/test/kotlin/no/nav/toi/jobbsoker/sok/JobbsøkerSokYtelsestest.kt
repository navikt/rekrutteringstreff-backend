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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.system.measureTimeMillis

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobbsøkerSokYtelsestest {
    companion object {
        private const val ANTALL_JOBBSØKERE = 10_000
        private const val WARMUP_TERSKEL_MS = 2_000L
        private const val MALT_TERSKEL_MS = 500L
        private const val SORTERING_TERSKEL_MS = 500L
        private val logger = LoggerFactory.getLogger(JobbsøkerSokYtelsestest::class.java)

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
            treffId = opprettTreffOgSeed()
            logger.info("Genererte {} jobbsøkere for ytelsestest av jobbsøker-søk", ANTALL_JOBBSØKERE)
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
                        opprettet_av_kontor_enhetid, opprettet_av_tidspunkt, eiere, kontorer, sist_endret
                    ) VALUES (?, 'Ytelsestest-treff', 'PUBLISERT', 'A123456', '0315', now(), ARRAY['A123456'], ARRAY['0315'], now())
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setObject(1, treffUuid)
                    stmt.executeUpdate()
                }

                val treffDbId = conn.prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?")
                    .apply { setObject(1, treffUuid) }
                    .executeQuery().let { it.next(); it.getLong(1) }

                val navkontorer = listOf("NAV Oslo", "NAV Bergen", "NAV Trondheim", "NAV Stavanger", "NAV Tromsø")
                val innsatsgrupper = listOf("STANDARD_INNSATS", "SITUASJONSBESTEMT_INNSATS", "SPESIELT_TILPASSET_INNSATS", "VARIG_TILPASSET_INNSATS", null)
                val fylker = listOf("Oslo", "Vestland", "Trøndelag", "Rogaland", "Troms og Finnmark")
                val kommuner = listOf("Oslo", "Bergen", "Trondheim", "Stavanger", "Tromsø")
                val statuser = listOf("LAGT_TIL", "INVITERT", "SVART_JA", "SVART_NEI")
                val baseTime = Instant.parse("2025-01-01T00:00:00Z")

                conn.prepareStatement(
                    """
                    INSERT INTO jobbsoker (rekrutteringstreff_id, fodselsnummer, fornavn, etternavn, navkontor, veileder_navident, veileder_navn, id, status, er_synlig)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                    RETURNING jobbsoker_id
                    """.trimIndent()
                ).use { jobbsokerStmt ->
                    conn.prepareStatement(
                        """
                        INSERT INTO jobbsoker_sok (jobbsoker_id, rekrutteringstreff_id, status, er_synlig, fornavn, etternavn, fylke, kommune, poststed, navkontor, veileder_navident, veileder_navn, innsatsgruppe, invitert_dato)
                        VALUES (?, ?, ?, true, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    ).use { sokStmt ->
                        repeat(ANTALL_JOBBSØKERE) { i ->
                            val status = statuser[i % statuser.size]
                            val fnr = String.format("%011d", i + 1)
                            val fornavn = "Fornavn$i"
                            val etternavn = "Etternavn${i % 100}"
                            val navkontor = navkontorer[i % navkontorer.size]
                            val innsatsgruppe = innsatsgrupper[i % innsatsgrupper.size]
                            val fylke = fylker[i % fylker.size]
                            val kommune = kommuner[i % kommuner.size]
                            val veilederIdent = "NAV${String.format("%03d", i % 50)}"
                            val veilederNavn = "Veileder ${i % 50}"
                            val invitertDato = if (status != "LAGT_TIL") Timestamp.from(baseTime.plusSeconds(i.toLong())) else null
                            val uuid = UUID.randomUUID()

                            jobbsokerStmt.setLong(1, treffDbId)
                            jobbsokerStmt.setString(2, fnr)
                            jobbsokerStmt.setString(3, fornavn)
                            jobbsokerStmt.setString(4, etternavn)
                            jobbsokerStmt.setString(5, navkontor)
                            jobbsokerStmt.setString(6, veilederIdent)
                            jobbsokerStmt.setString(7, veilederNavn)
                            jobbsokerStmt.setObject(8, uuid)
                            jobbsokerStmt.setString(9, status)
                            val rs = jobbsokerStmt.executeQuery()
                            rs.next()
                            val jobbsøkerId = rs.getLong(1)

                            sokStmt.setLong(1, jobbsøkerId)
                            sokStmt.setLong(2, treffDbId)
                            sokStmt.setString(3, status)
                            sokStmt.setString(4, fornavn)
                            sokStmt.setString(5, etternavn)
                            sokStmt.setString(6, fylke)
                            sokStmt.setString(7, kommune)
                            sokStmt.setString(8, "$kommune sentrum")
                            sokStmt.setString(9, navkontor)
                            sokStmt.setString(10, veilederIdent)
                            sokStmt.setString(11, veilederNavn)
                            sokStmt.setString(12, innsatsgruppe)
                            sokStmt.setTimestamp(13, invitertDato)
                            sokStmt.executeUpdate()
                        }
                    }
                }
                conn.commit()
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
