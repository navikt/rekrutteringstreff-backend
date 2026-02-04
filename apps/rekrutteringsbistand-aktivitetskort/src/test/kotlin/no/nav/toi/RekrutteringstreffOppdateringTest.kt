package no.nav.toi

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.producer.MockProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffOppdateringTest {
    private val localEnv = mutableMapOf<String, String>(
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_DATABASE" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_USERNAME" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PASSWORD" to "test"
    )
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val localPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }
        .also { localConfig ->
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_HOST"] = localConfig.host
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PORT"] = localConfig.getMappedPort(5432).toString()
        }

    private val rapid = TestRapid()
    private val databaseConfig = DatabaseConfig(localEnv, meterRegistry)
    private val testRepository = TestRepository(databaseConfig)
    private val app = App(rapid, Repository(databaseConfig, "http://url", "topic"), MockProducer(), MockConsumer(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST), "topic")

    @BeforeEach
    fun setup() {
        rapid.reset()
        testRepository.slettAlt()
        app.start()
    }

    @AfterAll
    fun teardown() {
        localPostgres.close()
        app.stop()
    }

    @Test
    fun `lesing av rekrutteringstreffoppdatering fra rapid skal oppdatere eksisterende aktivitetskort`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val opprinneligTittel = "Original Tittel"
        val opprinneligFraTid = ZonedDateTime.of(2025, 10, 1, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))
        val opprinneligTilTid = opprinneligFraTid.plusHours(2)
        val opprettetAv = "testuser"
        val opprettetTidspunkt = ZonedDateTime.now()
        val opprinneligGateadresse = "Original Gate 1"
        val opprinneligPostnummer = "1234"
        val opprinneligPoststed = "Original By"

        // Opprett først en invitasjon
        rapid.sendTestMessage(
            rapidInvitasjonMelding(
                fnr,
                rekrutteringstreffId,
                opprinneligTittel,
                opprinneligFraTid,
                opprinneligTilTid,
                opprettetAv,
                opprettetTidspunkt,
                opprinneligGateadresse,
                opprinneligPostnummer,
                opprinneligPoststed
            )
        )

        val invitasjoner = testRepository.hentAlle()
        assertThat(invitasjoner).hasSize(1)
        val aktivitetskortId = invitasjoner[0].aktivitetskortId

        // Send oppdatering
        val nyTittel = "Oppdatert Tittel"
        val nyFraTid = ZonedDateTime.of(2025, 10, 2, 10, 0, 0, 0, ZoneId.of("Europe/Oslo"))
        val nyTilTid = nyFraTid.plusHours(3)
        val nyGateadresse = "Ny Gate 2"
        val nyPostnummer = "5678"
        val nyPoststed = "Ny By"

        rapid.sendTestMessage(
            rapidOppdateringMelding(
                fnr,
                rekrutteringstreffId,
                nyTittel,
                nyFraTid,
                nyTilTid,
                nyGateadresse,
                nyPostnummer,
                nyPoststed
            )
        )

        // Hent alle aktivitetskort og verifiser oppdatering
        val aktivitetskort = testRepository.hentAlle()
        assertThat(aktivitetskort).hasSize(2) // Original + oppdatert versjon

        val oppdatertKort = aktivitetskort.last()

        // Verify the update was applied
        assertThat(oppdatertKort.aktivitetskortId).isEqualTo(aktivitetskortId)
        assertThat(oppdatertKort.tittel).isEqualTo(nyTittel)
        assertThat(oppdatertKort.fraTid).isEqualTo(nyFraTid.toLocalDate())
        assertThat(oppdatertKort.tilTid).isEqualTo(nyTilTid.toLocalDate())
        assertThat(oppdatertKort.detaljer).contains("oktober")
        assertThat(oppdatertKort.detaljer).contains("2025")
        assertThat(oppdatertKort.detaljer).contains("10:00")
        assertThat(oppdatertKort.detaljer).contains("13:00")
        assertThat(oppdatertKort.detaljer).contains("Ny Gate 2")
        assertThat(oppdatertKort.detaljer).contains("5678")
        assertThat(oppdatertKort.detaljer).contains("Ny By")
        assertThat(oppdatertKort.opprettetAv).isEqualTo("SYSTEM")
    }

    @Test
    fun `lesing av rekrutteringstreffoppdatering fra rapid skal oppdatere aktivitetskort med flersdagers arrangement`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val tittel = "Test Rekrutteringstreff"
        val fraTid = ZonedDateTime.of(2025, 10, 1, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))
        val tilTid = fraTid.plusHours(2)
        val opprettetAv = "testuser"
        val opprettetTidspunkt = ZonedDateTime.now()
        val gateadresse = "Test Gate"
        val postnummer = "1234"
        val poststed = "Test By"

        // Opprett først en invitasjon
        rapid.sendTestMessage(
            rapidInvitasjonMelding(
                fnr,
                rekrutteringstreffId,
                tittel,
                fraTid,
                tilTid,
                opprettetAv,
                opprettetTidspunkt,
                gateadresse,
                postnummer,
                poststed
            )
        )

        // Send oppdatering med flersdagers arrangement
        val nyFraTid = ZonedDateTime.of(2025, 10, 1, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))
        val nyTilTid = nyFraTid.plusHours(2).plusDays(1)

        rapid.sendTestMessage(
            rapidOppdateringMelding(
                fnr,
                rekrutteringstreffId,
                tittel,
                nyFraTid,
                nyTilTid,
                gateadresse,
                postnummer,
                poststed
            )
        )

        val aktivitetskort = testRepository.hentAlle()
        val oppdatertKort = aktivitetskort.last()

        assertThat(oppdatertKort.fraTid).isEqualTo(nyFraTid.toLocalDate())
        assertThat(oppdatertKort.tilTid).isEqualTo(nyTilTid.toLocalDate())
        assertThat(oppdatertKort.detaljer).contains("oktober")
        assertThat(oppdatertKort.detaljer).contains("2025")
        assertThat(oppdatertKort.detaljer).contains("08:00")
        assertThat(oppdatertKort.detaljer).contains("10:00")
        assertThat(oppdatertKort.detaljer).contains("til")
        assertThat(oppdatertKort.detaljer).contains("Test Gate")
        assertThat(oppdatertKort.detaljer).contains("1234")
        assertThat(oppdatertKort.detaljer).contains("Test By")
    }

    private fun rapidInvitasjonMelding(
        fnr: String,
        rekrutteringstreffId: UUID,
        tittel: String,
        fraTid: ZonedDateTime,
        tilTid: ZonedDateTime,
        opprettetAv: String,
        opprettetTidspunkt: ZonedDateTime,
        gateadresse: String,
        postnummer: String,
        poststed: String
    ): String = """
        {
            "@event_name": "rekrutteringstreffinvitasjon",
            "fnr":"$fnr",
            "rekrutteringstreffId":"$rekrutteringstreffId",
            "tittel": "$tittel",
            "fraTid": "$fraTid",
            "tilTid": "$tilTid",
            "opprettetAv": "$opprettetAv",
            "opprettetTidspunkt": "$opprettetTidspunkt",
            "gateadresse": "$gateadresse",
            "postnummer": "$postnummer",
            "poststed": "$poststed"
        }
    """.trimIndent()

    private fun rapidOppdateringMelding(
        fnr: String,
        rekrutteringstreffId: UUID,
        tittel: String,
        fraTid: ZonedDateTime,
        tilTid: ZonedDateTime,
        gateadresse: String,
        postnummer: String,
        poststed: String
    ): String = """
        {
            "@event_name": "rekrutteringstreffoppdatering",
            "fnr":"$fnr",
            "rekrutteringstreffId":"$rekrutteringstreffId",
            "tittel": "$tittel",
            "fraTid": "$fraTid",
            "tilTid": "$tilTid",
            "gateadresse": "$gateadresse",
            "postnummer": "$postnummer",
            "poststed": "$poststed"
        }
    """.trimIndent()
}

