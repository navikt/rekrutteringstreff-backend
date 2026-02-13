package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetsStatus
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.internals.AutoOffsetResetStrategy.StrategyType
import org.apache.kafka.clients.producer.MockProducer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffInvitasjonTest {
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
    private val app = App(
        port = 8080,
        rapidsConnection = rapid,
        repository = Repository(databaseConfig, "http://url", "topic"),
        producer = MockProducer(),
        consumer = MockConsumer(StrategyType.EARLIEST.toString()),
        dabAktivitetskortFeilTopic = "topic",
        leaderElection = LeaderElectionMock(),
        meterRegistry = meterRegistry,
        isRunning = {true},
        isReady = {true},
    )

    @BeforeAll
    fun oppstart() {
        app.start()
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
        testRepository.slettAlt()
    }

    @AfterAll
    fun teardown() {
        localPostgres.close()
        app.stop()
    }

    @Test
    fun `lesing av rekrutteringstreffinvitasjon fra rapid skal lagres i database`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val tittel = "Test Rekrutteringstreff"
        val fraTid = ZonedDateTime.of(2025, 10, 1, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))
        val tilTid = fraTid.plusHours(2)
        val opprettetAv = "testuser"
        val opprettetTidspunkt = ZonedDateTime.now()
        val gateadresse = "Test Sted"
        val postnummer = "1234"
        val poststed = "Test Poststed"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
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

        val rekrutteringstreffInvitasjoner = testRepository.hentAlle()
        assertThat(rekrutteringstreffInvitasjoner).hasSize(1)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)

        val expectedDetaljer = """[{"label":"Tid","verdi":"01. oktober 2025, kl. 08:00–10:00"},{"label":"Sted","verdi":"Test Sted, 1234 Test Poststed"}]"""
        rekrutteringstreffInvitasjoner.apply {
            assertThat(this[0].tittel).isEqualTo(tittel)
            assertThat(this[0].beskrivelse).isEqualTo("Nav arrangerer rekrutteringstreff, og vil gjerne ha deg med hvis du vil. På treffet møter du arbeidsgivere som leter etter folk å ansette. Kanskje finner du jobbmuligheten du ikke visste fantes? Følg lenken under for å lese mer om treffet og svare på invitasjonen.")
            assertThat(this[0].fraTid).isEqualTo(fraTid.toLocalDate())
            assertThat(this[0].tilTid).isEqualTo(tilTid.toLocalDate())
            assertThat(this[0].detaljer).isEqualToIgnoringWhitespace( expectedDetaljer)
            assertThat(this[0].aktivitetskortId).isEqualTo(inspektør.message(0)["aktivitetskortuuid"].asText().toUUID())
            assertThat(this[0].rekrutteringstreffId).isEqualTo(rekrutteringstreffId)
            assertThat(this[0].aktivitetsStatus).isEqualTo(AktivitetsStatus.FORSLAG.name)
            assertThat(this[0].opprettetAv).isEqualTo(opprettetAv)
            assertThat(this[0].opprettetTidspunkt).isCloseTo(opprettetTidspunkt, within(10, ChronoUnit.MILLIS))
        }
    }

    @Test
    fun `lesing av rekrutteringstreffinvitasjon fra rapid skal lagres i database når tidsperioden er over flere dager`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val tittel = "Test Rekrutteringstreff"
        val fraTid = ZonedDateTime.of(2025, 10, 1, 8, 0, 0, 0, ZoneId.of("Europe/Oslo"))
        val tilTid = fraTid.plusHours(2).plusDays(1)
        val opprettetAv = "testuser"
        val opprettetTidspunkt = ZonedDateTime.now()
        val gateadresse = "Test Sted"
        val postnummer = "1234"
        val poststed = "Test Poststed"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
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

        val rekrutteringstreffInvitasjoner = testRepository.hentAlle()
        assertThat(rekrutteringstreffInvitasjoner).hasSize(1)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)

        val expectedDetaljer = """[{"label":"Tid","verdi":"01. oktober 2025, kl. 08:00 til 02. oktober 2025, kl. 10:00"},{"label":"Sted","verdi":"Test Sted, 1234 Test Poststed"}]"""
        rekrutteringstreffInvitasjoner.apply {
            assertThat(this[0].fraTid).isEqualTo(fraTid.toLocalDate())
            assertThat(this[0].tilTid).isEqualTo(tilTid.toLocalDate())
            assertThat(this[0].detaljer).isEqualToIgnoringWhitespace( expectedDetaljer)
        }
    }

    @Test
    fun `lesing av rekrutteringsinvitasjon fra rapid med samme kandidat og stilling skal ignoreres`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val fraTid = ZonedDateTime.now().plusDays(1)
        val tilTid = fraTid.plusHours(2)
        val opprettetTidspunkt = ZonedDateTime.now()
        val gateadresse = "Test Sted"
        val postnummer = "1234"
        val poststed = "Test Poststed"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                rekrutteringstreffId,
                "Tittel 1",
                fraTid,
                tilTid,
                "Z0000001",
                opprettetTidspunkt,
                gateadresse,
                postnummer,
                poststed
            )
        )
        val expectedRekrutteringstreffInvitasjoner = testRepository.hentAlle()
        assertThat(expectedRekrutteringstreffInvitasjoner).hasSize(1)
        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                rekrutteringstreffId,
                "Tittel 2",
                fraTid.plusDays(1),
                tilTid.plusDays(1),
                "Z0000002",
                opprettetTidspunkt.plusHours(1),
                gateadresse,
                postnummer,
                poststed
            )
        )

        val actualRekrutteringstreffInvitasjoner = testRepository.hentAlle()
        assertThat(actualRekrutteringstreffInvitasjoner).hasSize(1)
        assertThat(actualRekrutteringstreffInvitasjoner.first()).usingRecursiveComparison().isEqualTo(expectedRekrutteringstreffInvitasjoner.first())
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
    }

    @Test
    fun `lesing av rekrutteringstreffinvitasjon skal returnere aktivitetskortuuid på rapid`() {
        val fnr = "01010012345"
        val rekrutteringstreffId = UUID.randomUUID()
        val tittel = "Test Rekrutteringstreff"
        val beskrivelse = "Beskrivelse av rekrutteringstreff"
        val fraTid = ZonedDateTime.now().plusDays(1)
        val tilTid = fraTid.plusHours(2)
        val opprettetAv = "testuser"
        val opprettetTidspunkt = ZonedDateTime.now()
        val gateadresse = "Test Sted"
        val postnummer = "1234"
        val poststed = "Test Poststed"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
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

        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
        inspektør.message(0).also { message ->
            assertThat(message["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
            assertThat(message["fnr"].asText()).isEqualTo(fnr)
            assertThat(message["rekrutteringstreffId"].asText()).isEqualTo(rekrutteringstreffId.toString())
            assertThat(message["tittel"].asText()).isEqualTo(tittel)
            assertThat(message["fraTid"].asText()).isEqualTo(fraTid.toString())
            assertThat(message["tilTid"].asText()).isEqualTo(tilTid.toString())
            assertThat(message["opprettetAv"].asText()).isEqualTo(opprettetAv)
            assertThat(message["opprettetTidspunkt"].asText()).isEqualTo(opprettetTidspunkt.toString())
            assertThat(message["aktivitetskortuuid"].isMissingOrNull()).isFalse
            assertThat(message["gateadresse"].asText()).isEqualTo(gateadresse)
            assertThat(message["postnummer"].asText()).isEqualTo(postnummer)
            assertThat(message["poststed"].asText()).isEqualTo(poststed)
        }
    }
    private fun rapidPeriodeMelding(
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
}