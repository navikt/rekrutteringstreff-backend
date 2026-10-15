package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers_api.FailedMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.rapids_and_rivers_api.SentMessage
import tools.jackson.databind.JsonNode
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class TestRapid(private val meterRegistry: io.micrometer.core.instrument.MeterRegistry = io.micrometer.core.instrument.simple.SimpleMeterRegistry(), private val maxTriggedeMeldinger: Int = 10) :
    RapidsConnection() {
    private companion object {
        private val objectMapper = JsonMapper.builderWithJackson2Defaults()
            .addModule(kotlinModule())
            .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()
    }

    private val messages = mutableListOf<Pair<String?, String>>()
    val inspektør get() = RapidInspector(messages.toList())

    fun reset() {
        messages.clear()
    }

    fun sendTestMessage(message: String) {
        notifyMessage(message, this,
            com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata("test.message", -1, -1, null, emptyMap()), meterRegistry)
        sjekkForLoop()
    }

    fun sendTestMessage(message: String, key: String) {
        notifyMessage(message,
            com.github.navikt.tbd_libs.rapids_and_rivers_api.KeyMessageContext(this, key),
            com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata("test.message", -1, -1, key, emptyMap()), meterRegistry)
        sjekkForLoop()
    }

    private fun sjekkForLoop() {
        val meldingerFørLoopTest = keyOgMeldinger()
        sjekkForLoopRecursive(0)
        messages.clear()
        meldingerFørLoopTest.forEach { (key, message) ->
            if (key == null) publish(message.toString()) else publish(key, message.toString())
        }
    }

    private fun sjekkForLoopRecursive(kjørFra: Int) {
        val messages = keyOgMeldinger()
        messages.filterIndexed { index, _ -> index>=kjørFra }.forEach { (key, message) ->
            if(key == null) {
                notifyMessage(message.toString(), this,
                    com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata(
                        "test.message",
                        -1,
                        -1,
                        null,
                        emptyMap()
                    ), meterRegistry)
            } else {
                notifyMessage(message.toString(),
                    com.github.navikt.tbd_libs.rapids_and_rivers_api.KeyMessageContext(this, key),
                    com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata(
                        "test.message",
                        -1,
                        -1,
                        key,
                        emptyMap()
                    ), meterRegistry)
            }
        }
        val newMessages = keyOgMeldinger()
        if (newMessages.size > maxTriggedeMeldinger) {
            throw IllegalStateException("Loop antatt med ${newMessages.size} meldinger: $newMessages")
        } else if(newMessages.size > messages.size) {
            sjekkForLoopRecursive(messages.size)
        } else if(newMessages.size < messages.size) {
            throw IllegalStateException("Skal ikke være mulig å nå denne linjen. Feil i implementasjonen av ${this::class.java.simpleName}")
        }
    }

    private fun keyOgMeldinger() = inspektør.let { inspektør ->
        (0 until inspektør.size)
            .map { inspektør.key(it) to inspektør.message(it) }
    }

    override fun publish(message: String) {
        messages.add(null to message)
    }

    override fun publish(key: String, message: String) {
        messages.add(key to message)
    }

    override fun publish(messages: List<OutgoingMessage>): Pair<List<SentMessage>, List<FailedMessage>> {
       val sendteMeldinger = mutableListOf<SentMessage>()
        messages.forEachIndexed { index, message ->
            this.messages.add(message.key to message.body)
            sendteMeldinger.add(SentMessage(index = index, message = message, partition = 1, offset = index.toLong()))
        }
        return Pair(sendteMeldinger, emptyList())
    }

    override fun rapidName(): String {
        return "testRapid"
    }

    override fun start() {}
    override fun stop() {}

    class RapidInspector(private val messages: List<Pair<String?, String>>) {
        private val jsonMessages = mutableMapOf<Int, JsonNode>()
        val size get() = messages.size

        fun key(index: Int) = messages[index].first
        fun rawMessage(index: Int) = messages[index].second
        fun message(index: Int) = jsonMessages.getOrPut(index) { objectMapper.readTree(messages[index].second) }
        fun field(index: Int, field: String) =
            requireNotNull(message(index).path(field).takeUnless { it.isMissingNode || it.isNull }) {
                "Message does not contain field '$field'"
            }
    }
}