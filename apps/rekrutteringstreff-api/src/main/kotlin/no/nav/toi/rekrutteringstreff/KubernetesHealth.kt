package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

private const val endepunktReady = "/isready"
private const val endepunktAlive = "/isalive"

@OpenApi(
    summary = "isready",
    tags = [],
    //requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
    path = "/isready",
    methods = [HttpMethod.GET]
)
fun isReadyHandler(ctx: io.javalin.http.Context) {
    ctx.result("isready")
}

@OpenApi(
    summary = "isalive",
    tags = [],
    //requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
    path = "/isalive",
    methods = [HttpMethod.GET]
)
fun isAliveHandler(ctx: io.javalin.http.Context) {
    ctx.result("isalive")
}


fun Javalin.handleHealth() {
    get(endepunktReady, ::isReadyHandler)
    get(endepunktAlive, ::isAliveHandler)
}
