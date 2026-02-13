package no.nav.toi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.rapids_and_rivers.KafkaRapid
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.javalin.Javalin
import io.javalin.json.JavalinJackson
import io.javalin.micrometer.MicrometerPlugin
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.scheduler
import no.nav.toi.rekrutteringstreff.RekrutteringstreffInvitasjonLytter
import no.nav.toi.rekrutteringstreff.RekrutteringstreffOppdateringLytter
import no.nav.toi.rekrutteringstreff.RekrutteringstreffSvarOgStatusLytter
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class App(
    private val port: Int,
    private val rapidsConnection: RapidsConnection,
    private val repository: Repository,
    private val producer: Producer<String, String>,
    private val consumer: Consumer<String, String>,
    private val dabAktivitetskortFeilTopic: String,
    private val leaderElection: LeaderElectionInterface,
    private val meterRegistry: PrometheusMeterRegistry,
    private val isRunning: () -> Boolean,
    private val isReady: () -> Boolean,
) {
    private lateinit var javalin: Javalin
    private val secureLog = SecureLog(log)

    fun start() {
        startJavalin()
        scheduler(0, 0, repository, producer, consumer, rapidsConnection, dabAktivitetskortFeilTopic, leaderElection)
        startRapidsAndRivers()
    }

    private fun startJavalin() {
        log.info("Starter app")
        secureLog.info("Starter app. Dette er ment å logges til Securelogs. Hvis du ser dette i den ordinære apploggen er noe galt, og sensitive data kan havne i feil logg.")

        val micrometerPlugin = MicrometerPlugin { micrometerConfig ->
            micrometerConfig.registry = meterRegistry
        }

        javalin = Javalin.create { config ->
            config.jsonMapper(JavalinJackson(jacksonObjectMapper()))
            config.registerPlugin(micrometerPlugin)
        }

        HelsesjekkController(prometheusMeterRegistry = meterRegistry, javalin = javalin, isReady = isReady, isRunning = isRunning)

        javalin.start(port)
    }

    private fun startRapidsAndRivers() {
        log.info("Starter RapidsConnection")
        RekrutteringstreffInvitasjonLytter(rapidsConnection, repository)
        RekrutteringstreffSvarOgStatusLytter(rapidsConnection, repository)
        RekrutteringstreffOppdateringLytter(rapidsConnection, repository)
        Thread {
            try {
                rapidsConnection.start()
            } catch (e: Exception) {
                log.error("RapidsConnection feilet, avslutter applikasjonen", e)
                System.exit(1)
            }
        }.start()

    }

    fun stop() {
        rapidsConnection.stop()
        if (::javalin.isInitialized) javalin.stop()
    }
}

fun main() {
    val env = System.getenv()
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val rapidsConnection = KafkaRapid(
        factory = ConsumerProducerFactory(AivenConfig.default),
        groupId = env.variable("KAFKA_CONSUMER_GROUP_ID"),
        rapidTopic = env.variable("KAFKA_RAPID_TOPIC"),
        meterRegistry = meterRegistry
    )
    val app = App(
        port = 8080,
        rapidsConnection = rapidsConnection,
        repository = Repository(
            DatabaseConfig(env),
            env.variable("MIN_SIDE_URL"),
            env.variable("DAB_AKTIVITETSKORT_TOPIC")
        ),
        producer = KafkaProducer(producerConfig(env)),
        consumer = KafkaConsumer(consumerConfig(env)),
        dabAktivitetskortFeilTopic = env.variable("DAB_AKTIVITETSKORT_FEIL_TOPIC"),
        leaderElection = LeaderElection(),
        meterRegistry = meterRegistry,
        isRunning = rapidsConnection::isRunning,
        isReady = rapidsConnection::isReady
    )
    app.start()
}

fun producerConfig(env: Map<String, String>) = mapOf(
    CommonClientConfigs.CLIENT_ID_CONFIG to "rekrutteringsbistand-aktivitetskort",
    CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to env.variable("KAFKA_BROKERS"),
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
    SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to env.variable("KAFKA_KEYSTORE_PATH"),
    SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to env.variable("KAFKA_CREDSTORE_PASSWORD"),
    SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to env.variable("KAFKA_CREDSTORE_PASSWORD"),
    SslConfigs.SSL_KEY_PASSWORD_CONFIG to env.variable("KAFKA_CREDSTORE_PASSWORD"),
    SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "JKS",
    SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
    SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to env.variable("KAFKA_TRUSTSTORE_PATH"),
).toProperties()

fun consumerConfig(env: Map<String, String>) = Properties().apply {
    val trustStorePath = env.variable("KAFKA_TRUSTSTORE_PATH")
    val keyStorePath = env.variable("KAFKA_KEYSTORE_PATH")

    put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100)
    put(ConsumerConfig.GROUP_ID_CONFIG, "rekrutteringsbistand-aktivitetskort-feil-4")
    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true)
    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
    put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)

    put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.variable("KAFKA_BROKERS"))
    put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
    put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
    put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")

    if(trustStorePath.isNotEmpty()) {
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env.variable("KAFKA_TRUSTSTORE_PATH"))
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env.variable("KAFKA_CREDSTORE_PASSWORD"))
    }

    if(keyStorePath.isNotEmpty()) {
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, env.variable("KAFKA_KEYSTORE_PATH"))
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, env.variable("KAFKA_CREDSTORE_PASSWORD"))
    }
}

private fun Map<String, String>.variable(felt: String) = this[felt] ?: error("$felt er ikke angitt")