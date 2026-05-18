package no.nav.toi

import io.javalin.router.JavalinDefaultRoutingApi

interface RuteRegistrerer {
    fun registrer(routes: JavalinDefaultRoutingApi)
}

fun JavalinDefaultRoutingApi.registrer(controller: RuteRegistrerer) {
    controller.registrer(this)
}
