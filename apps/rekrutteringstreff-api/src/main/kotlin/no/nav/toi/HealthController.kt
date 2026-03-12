package no.nav.toi

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

class HealthController(val javalin: Javalin, val healthRepository: HealthRepository)  {
    companion object {
        private const val ENDEPUNKT_READY = "/isready"
        private const val ENDEPUNKT_ALIVE = "/isalive"
    }

    init {
        javalin.get(ENDEPUNKT_READY, isReadyHandler())
        javalin.get(ENDEPUNKT_ALIVE, isAliveHandler())
    }

    @OpenApi(
        summary = "Er applikasjonen klar?",
        responses = [
            OpenApiResponse(
                status = "200",
                content = [OpenApiContent(from = String::class, example = "isready")]
            )
        ],
        path = ENDEPUNKT_READY,
        methods = [HttpMethod.GET]
    )
    fun isReadyHandler(): (Context) -> Unit = { ctx ->
        if (healthRepository.fårKontaktMedDatabasen()) {
            ctx.result("isready")
        } else {
            log.info("isReady får ikke kontakt med databasen")
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
        path = ENDEPUNKT_ALIVE,
        methods = [HttpMethod.GET]
    )
    fun isAliveHandler(): (Context) -> Unit = { ctx ->
        if (healthRepository.fårKontaktMedDatabasen()) {
            ctx.result("isalive")
        } else {
            log.info("isAliveHandler får ikke kontakt med databasen")
            ctx.status(500).result("db not alive")
        }
    }
}
