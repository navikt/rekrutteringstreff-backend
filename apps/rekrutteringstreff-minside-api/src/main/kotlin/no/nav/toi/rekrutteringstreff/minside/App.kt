package no.nav.toi.rekrutteringstreff.minside

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.json.JavalinJackson
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin


class App(
    private val port: Int
) {
    private lateinit var javalin: Javalin

    fun start() {
        javalin = Javalin.create { config ->
            config.jsonMapper(JavalinJackson(JacksonConfig.mapper))
            configureOpenApi(config)
            log.info("Javalin opprettet")
        }
        javalin.start(port)
    }

    fun close() {
        if (::javalin.isInitialized) {
            javalin.stop()
        }
    }
}

private fun configureOpenApi(config: JavalinConfig) {
    val openApiPlugin = OpenApiPlugin { openApiConfig ->
        openApiConfig.withDefinitionConfiguration { _, definition ->
            definition.withInfo { info ->
                info.title = "Minside API"
                info.version = "1.0.0"
            }
            definition.withSecurity { security ->
                security.withBearerAuth()
            }
        }
    }
    config.registerPlugin(openApiPlugin)
    config.registerPlugin(SwaggerPlugin { swaggerConfiguration ->
        swaggerConfiguration.validatorUrl = null
    })
}

private val log = noClassLogger()

fun main() {

}
