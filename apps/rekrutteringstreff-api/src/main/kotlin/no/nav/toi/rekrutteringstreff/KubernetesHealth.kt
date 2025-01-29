package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin

private const val endepunktReady = "/isready"
private const val endepunktAlive = "/isalive"

fun Javalin.handleHealth() {
    get(endepunktReady) { ctx->
        ctx.result("isReady")
    }
    get(endepunktAlive) { ctx->
        ctx.result("isAlive")
    }
}