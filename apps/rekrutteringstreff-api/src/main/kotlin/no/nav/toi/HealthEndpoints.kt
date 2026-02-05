package no.nav.toi

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

private const val endepunktReady = "/isready"
private const val endepunktAlive = "/isalive"

@OpenApi(
    summary = "Er applikasjonen klar?",
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = String::class, example = "isready")]
        )
    ],
    path = endepunktReady,
    methods = [HttpMethod.GET]
)
private fun isReadyHandler(ctx: Context, isReady: () -> Boolean) {
    if (isReady()) {
        ctx.result("isready")
    } else {
        ctx.status(500)
    }
}

@OpenApi(
    summary = "Er applikasjonen levende?",
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = String::class, example = "isalive")]
        )
    ],
    path = endepunktAlive,
    methods = [HttpMethod.GET]
)
private fun isAliveHandler(ctx: Context, isRunning: ()-> Boolean) {
    if (isRunning()) {
        ctx.result("isalive")
    } else {
        ctx.status(500)
    }
}

fun Javalin.handleHealth(isRunning: ()-> Boolean, isReady: ()-> Boolean) {
    get(endepunktReady) { isReadyHandler(it, isReady) }
    get(endepunktAlive) { isAliveHandler(it, isRunning) }
}
