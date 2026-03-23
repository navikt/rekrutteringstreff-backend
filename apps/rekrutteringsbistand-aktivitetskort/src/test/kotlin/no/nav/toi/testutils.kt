package no.nav.toi

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

val httpClient: HttpClient = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.ALWAYS)
    .connectTimeout(Duration.ofSeconds(5))
    .build()


object ubruktPortnrFra11000 {
    private val portnr = AtomicInteger(11000)
    fun ubruktPortnr(): Int = portnr.andIncrement
}

val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
