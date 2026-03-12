package no.nav.toi

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

class HealthEndpoints {
    companion object {
        const val ENDEPUNKT_READY = "/isready"
        const val ENDEPUNKT_ALIVE = "/isalive"
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
    fun isReadyHandler(ctx: Context, isAliveRepository: IsAliveRepository) {
        if (isAliveRepository.fårKontaktMedDatabasen()) {
            log.info("isReady OK")
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
        path = ENDEPUNKT_ALIVE,
        methods = [HttpMethod.GET]
    )
    fun isAliveHandler(ctx: Context, isAliveRepository: IsAliveRepository) {
        if (isAliveRepository.fårKontaktMedDatabasen()) {
            log.info("isAliveHandler OK")
            ctx.status(200).result("isalive")
        } else {
            log.info("isAliveHandler FEIL")
            ctx.status(500).result("db not alive")
        }
    }
}


fun Javalin.handleHealth(isAliveRepository: IsAliveRepository) {
    get(HealthEndpoints.ENDEPUNKT_READY) { ctx -> HealthEndpoints().isReadyHandler(ctx, isAliveRepository) }
    get(HealthEndpoints.ENDEPUNKT_ALIVE) { ctx -> HealthEndpoints().isAliveHandler(ctx, isAliveRepository) }
}
