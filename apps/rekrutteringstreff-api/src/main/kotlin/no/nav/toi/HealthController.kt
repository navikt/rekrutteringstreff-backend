package no.nav.toi

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

class HealthController(val javalin: Javalin, val isAliveRepository: IsAliveRepository)  {
    companion object {
        private const val endepunktReady = "/isready"
        private const val endepunktAlive = "/isalive"
    }

    init {
        javalin.get(endepunktReady, isReadyHandler() )
        javalin.get(endepunktAlive, isAliveHandler() )
    }

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
    fun isReadyHandler(): (Context) -> Unit = { ctx ->
        if (isAliveRepository.fårKontaktMedDatabasen()) {
            ctx.result("isready")
        } else {
            log.info("isReady FEIL")
            ctx.status(500).result("db not ready")
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
    fun isAliveHandler(): (Context) -> Unit = { ctx ->
        if (isAliveRepository.fårKontaktMedDatabasen()) {
            ctx.status(200).result("isalive")
        } else {
            log.info("isAliveHandler FEIL")
            ctx.status(500).result("db not alive")
        }
    }
}
