package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

private const val endepunktReady = "/isready"
private const val endepunktAlive = "/isalive"

@OpenApi(
    summary = "isReady",
    operationId = "todo",
    tags = [],
    //requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
    path = "/isReady",
    methods = [HttpMethod.POST]
)
fun isReadyHandler(ctx: io.javalin.http.Context) {
    ctx.result("isReady")
}

@OpenApi(
    summary = "isAlive",
    operationId = "todo",
    tags = [],
    //requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
    path = "/isReady",
    methods = [HttpMethod.POST]
)
fun isAliveHandler(ctx: io.javalin.http.Context) {
    ctx.result("isAlive")
}


fun Javalin.handleHealth() {
    get(endepunktReady, ::isReadyHandler)
    get(endepunktAlive, ::isAliveHandler)
}