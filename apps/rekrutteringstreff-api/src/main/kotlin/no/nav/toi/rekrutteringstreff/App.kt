package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import java.time.Instant
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS

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
        if (::javalin.isInitialized) {
            javalin.stop()
        }
    }
}

fun main() {
    App(
        8080, RekrutteringstreffRepository(DataSourceFactory.createDataSource()), listOf(
            AuthenticationConfiguration(
                audience = getenv("AZURE_APP_CLIENT_ID"),
                issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI")
            )
        )
    ).start()
}

/**
 * Tidspunkt uten nanosekunder, for å unngå at to like tidspunkter blir ulike pga at database og Microsoft Windws håndterer nanos annerledes enn Mac og Linux.
 */
fun nowOslo(): ZonedDateTime = ZonedDateTime.now().atOslo()

fun ZonedDateTime.atOslo(): ZonedDateTime = this.withZoneSameInstant(of("Europe/Oslo")).truncatedTo(MILLIS)

fun Instant.atOslo(): ZonedDateTime = this.atZone(of("Europe/Oslo")).truncatedTo(MILLIS)


private fun getenv(key: String) =
    System.getenv(key) ?: throw IllegalArgumentException("Det finnes ingen system-variabel ved navn $key")
