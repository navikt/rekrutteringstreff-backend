package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import java.time.Instant
import java.time.ZoneId

class App(
    private val port: Int,
    private val repo: RekrutteringstreffRepository,
    private val authConfigs: List<AuthenticationConfiguration>
) {
    lateinit var javalin: Javalin
        private set

    fun start() {
        javalin = Javalin.create()
        javalin.handleHealth()
        javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(authConfigs)
        javalin.handleRekrutteringstreff(repo)
        javalin.start(port)
    }
    fun close() {
        if(::javalin.isInitialized) {
            javalin.stop()
        }
    }
}

fun main() {
    App(8080, RekrutteringstreffRepository(DataSourceFactory.createDataSource()), listOf(
        AuthenticationConfiguration(
            audience = getenv("AZURE_APP_CLIENT_ID"),
            issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
            jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI")
        )
    )).start()
}

fun Instant.atOslo() = this.atZone(ZoneId.of("Europe/Oslo"))

private fun getenv(key: String) =
    System.getenv(key) ?: throw IllegalArgumentException("Det finnes ingen system-variabel ved navn $key")