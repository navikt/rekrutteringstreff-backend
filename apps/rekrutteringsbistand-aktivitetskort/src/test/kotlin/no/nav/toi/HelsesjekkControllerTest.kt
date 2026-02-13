package no.nav.toi

import no.nav.toi.ubruktPortnrFra11000.ubruktPortnr
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.internals.AutoOffsetResetStrategy.StrategyType
import org.apache.kafka.clients.producer.MockProducer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelsesjekkControllerTest {
    private val appPort = ubruktPortnr()

    private val localEnv = mutableMapOf<String, String>(
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_DATABASE" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_USERNAME" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PASSWORD" to "test"
    )
    private val localPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }
        .also { localConfig ->
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_HOST"] = localConfig.host
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PORT"] = localConfig.getMappedPort(5432).toString()
        }
    private val databaseConfig = DatabaseConfig(localEnv, meterRegistry)
    private val repository = Repository(databaseConfig, "http://url/rekrutteringstreff", "topic")

    private lateinit var app: App

    @AfterEach
    fun teardown() {
        app.stop()
    }

    fun opprettApp(isRunning: () -> Boolean, isReady: () -> Boolean) = App(
        port = appPort,
        rapidsConnection = TestRapid(),
        repository = repository,
        producer = MockProducer(),
        consumer = MockConsumer(StrategyType.EARLIEST.toString()),
        dabAktivitetskortFeilTopic = "topic",
        leaderElection = LeaderElectionMock(),
        meterRegistry = meterRegistry,
        isRunning = isRunning,
        isReady = isReady
    )

    @Test
    fun `skal returnere 200 OK på isalive hvis alt kjører`() {
        app = opprettApp(isRunning = { true }, isReady = { true })
        app.start()

        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$appPort/isalive"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertEquals(200, response.statusCode())
    }

    @Test
    fun `Skal returenere 200 OK på isready hvis alt kjører`() {
        app = opprettApp(isRunning = { true }, isReady = { true })
        app.start()

        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$appPort/isready"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertEquals(200, response.statusCode())
    }

    @Test
    fun `skal returnere 500 server error på isAlive hvis rapid ikke kjører`() {
        app = opprettApp(isRunning = { false }, isReady = { true })
        app.start()

        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$appPort/isalive"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertEquals(500, response.statusCode())
    }

    @Test
    fun `Skal returnere 500 server error på isReady hvis rapid ikke er klar`() {
        app = opprettApp(isRunning = { false }, isReady = { false })
        app.start()

        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$appPort/isready"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertEquals(500, response.statusCode())
    }
}