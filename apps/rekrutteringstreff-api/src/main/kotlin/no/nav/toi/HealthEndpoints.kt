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
fun isReadyHandler(ctx: Context) {
    ctx.result("isready")
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
fun isAliveHandler(ctx: Context) {
    ctx.result("isalive")
}

fun Javalin.handleHealth() {
    get(endepunktReady, ::isReadyHandler)
    get(endepunktAlive, ::isAliveHandler)
}
