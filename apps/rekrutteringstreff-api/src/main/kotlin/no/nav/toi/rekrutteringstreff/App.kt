package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import java.time.Instant
import java.time.ZoneId

class App(
    private val port: Int = 8080,
    private val repo: RekrutteringstreffRepository? = null,
    // I produksjon settes authConfigs for reell verifisering, men i test kan vi la den være null.
    private val authConfigs: List<Any>? = null
) {
    lateinit var javalin: Javalin
        private set

    fun start() {
        javalin = Javalin.create()
        javalin.handleHealth()
        if (authConfigs == null) {
            javalin.testAuthentication()
        }
        repo?.let { repository ->
            javalin.handleRekrutteringstreff(repository)
        }
        javalin.start(port)
    }
    fun close() {
        if(::javalin.isInitialized) {
            javalin.stop()
        }
    }
}

fun main() {
    App().start()
}

fun Instant.atOslo() = this.atZone(ZoneId.of("Europe/Oslo"))

fun Javalin.testAuthentication(): Javalin {
    before("/api/rekrutteringstreff") { ctx ->
        val dummyNavIdent = "A123456"
        ctx.attribute("authenticatedUser", AuthenticatedUser(dummyNavIdent, "dummy-token"))
    }
    return this
}
