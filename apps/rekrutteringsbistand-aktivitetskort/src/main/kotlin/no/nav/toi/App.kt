package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication

class App(private val rapidsConnection: RapidsConnection, repository: Repository) {
    init {
        RekrutteringstreffInvitasjonLytter(rapidsConnection, repository)
    }
    fun start() {
        rapidsConnection.start()
    }

    fun stop() {
        rapidsConnection.stop()
    }
}

fun main() {
    val app = App(RapidApplication.create(System.getenv()), Repository(DatabaseConfig(System.getenv())))
    app.start()
}