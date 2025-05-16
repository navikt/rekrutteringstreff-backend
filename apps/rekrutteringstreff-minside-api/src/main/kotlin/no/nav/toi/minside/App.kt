package no.nav.toi.minside

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.json.JavalinJackson
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import no.nav.arbeid.cv.felles.token.AzureKlient
import no.nav.toi.minside.rekrutteringstreff.RekrutteringstreffKlient
import no.nav.toi.minside.rekrutteringstreff.rekrutteringstreffendepunkt


class App(
    private val port: Int,
    private val rekrutteringstreffUrl: String,
    private val azureKlient: AzureKlient,
    private val authConfigs: List<AuthenticationConfiguration>
) {
    private lateinit var javalin: Javalin

    fun start() {
        javalin = Javalin.create { config ->
            config.jsonMapper(JavalinJackson(JacksonConfig.mapper))
            configureOpenApi(config)
            log.info("Javalin opprettet")
        }
        javalin.handleHealth()
        javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(authConfigs)
        javalin.rekrutteringstreffendepunkt(RekrutteringstreffKlient(rekrutteringstreffUrl, azureKlient))
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
    App(
        port = 8080,
        rekrutteringstreffUrl = "http://rekrutteringstreff-api",
        azureKlient = AzureKlient(
            clientId = env("AZURE_APP_CLIENT_ID"),
            clientSecret = env("AZURE_APP_CLIENT_ID"),
            tokenEndpoint = env("AZURE_APP_CLIENT_SECRET"),
            scope = env("REKRUTTERINGSTREFF_SCOPE")
        ),
        authConfigs = listOf(
            AuthenticationConfiguration(
                audience = env("AZURE_APP_CLIENT_ID"),
                issuer = env("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri = env("AZURE_OPENID_CONFIG_JWKS_URI")
            )
        )
    ).start()
}

private fun env(key: String) = System.getenv(key) ?: throw RuntimeException("Missing environment variable $key")
