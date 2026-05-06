package no.nav.toi

import io.javalin.router.JavalinDefaultRoutingApi
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat

class HelsesjekkController(
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
    routes: JavalinDefaultRoutingApi,
    isReady: () -> Boolean,
    isRunning: () -> Boolean
) {
    init {
        routes.get("/isready") {
            if(isReady()) {
                it.status(200)
            } else {
                it.status(500)
            }
        }
        routes.get("/isalive") {
            if(isRunning()) {
                it.status(200)
            } else {
                it.status(500)
            }
        }
        routes.get(
            "/metrics",
        ) { it.contentType(TextFormat.CONTENT_TYPE_004).result(prometheusMeterRegistry.scrape()) }
    }
}