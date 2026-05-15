package no.nav.toi

import io.javalin.router.JavalinDefaultRoutingApi

interface Registrable {
    fun register(routes: JavalinDefaultRoutingApi)
}

fun JavalinDefaultRoutingApi.register(controller: Registrable) {
    controller.register(this)
}
