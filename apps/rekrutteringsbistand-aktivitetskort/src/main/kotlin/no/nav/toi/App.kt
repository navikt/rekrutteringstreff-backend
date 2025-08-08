package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.toi.aktivitetskort.scheduler
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

class App(private val rapidsConnection: RapidsConnection, private val repository: Repository, private val producer: Producer<String, String>, private val consumer: Consumer<String, String>) {
    init {
        RekrutteringstreffInvitasjonLytter(rapidsConnection, repository)
    }
    fun start() {
        scheduler(0, 0, repository, producer, consumer, rapidsConnection)
        rapidsConnection.start()
    }

    fun stop() {
        rapidsConnection.stop()
    }
}

fun main() {
    val env = System.getenv()
    val app = App(RapidApplication.create(env), Repository(DatabaseConfig(env), env.variable("MIN_SIDE_URL")),
        KafkaProducer(producerConfig(env)),
        KafkaConsumer(consumerConfig(env)),
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
    put(ConsumerConfig.GROUP_ID_CONFIG, "rekrutteringsbistand-aktivitetskort-feil-2")
    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
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