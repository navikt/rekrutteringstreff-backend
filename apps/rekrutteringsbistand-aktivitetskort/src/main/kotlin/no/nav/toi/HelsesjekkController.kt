package no.nav.toi

import io.javalin.Javalin
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat

class HelsesjekkController(
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    javalin: Javalin,
    isReady: () -> Boolean,
    isRunning: () -> Boolean
) {
    init {
        javalin.get("/isready") {
            if(isReady()) {
                it.status(200)
            } else {
                it.status(500)
            }
        }
        javalin.get("/isalive") {
            if(isRunning()) {
                it.status(200)
            } else {
                it.status(500)
            }
        }
        javalin.get(
            "/internal/prometheus",
        ) { it.contentType(TextFormat.CONTENT_TYPE_004).result(prometheusMeterRegistry.scrape()) }
    }
}