package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import java.time.Instant
import java.time.ZoneId

class App(private val port: Int = 8080) {
    private lateinit var javalin: Javalin
    fun start() {
        javalin = Javalin.create()
        javalin.handleHealth()
        javalin.handleRekrutteringstreff()


        javalin.start(port)
    }
    fun close() {
        javalin.stop()
    }
}

fun main() {
    App(

    ).start()
}

fun Instant.atOslo() = this.atZone(ZoneId.of("Europe/Oslo"))