package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

private const val endepunktRekrutteringstreff = "/api/rekrutteringstreff"

@OpenApi(
    summary = endepunktRekrutteringstreff,
    operationId = "todo",
    tags = [],
    //requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
    path = endepunktRekrutteringstreff,
    methods = [HttpMethod.POST]
)
fun opprettRekrutteringstreffHandler(ctx: io.javalin.http.Context) {
    ctx.status(201).result("TODO treffresult post")
}


fun Javalin.handleRekrutteringstreff() {
    post(endepunktRekrutteringstreff, ::opprettRekrutteringstreffHandler)
}