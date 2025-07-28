package no.nav.toi.minside

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.json.JavalinJackson
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import no.nav.toi.minside.arbeidsgiver.arbeidsgiverendepunkt
import no.nav.toi.minside.rekrutteringstreff.RekrutteringstreffKlient
import no.nav.toi.minside.rekrutteringstreff.rekrutteringstreffendepunkt
import no.nav.toi.minside.svar.BorgerKlient
import no.nav.toi.minside.svar.rekrutteringstreffSvarEndepunkt
import java.net.http.HttpClient


class App(
    private val port: Int,
    private val rekrutteringstreffUrl: String,
    private val rekrutteringstreffAudience: String,
    private val tokenXKlient: TokenXKlient,
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
        val rekrutteringstreffKlient = RekrutteringstreffKlient(rekrutteringstreffUrl, tokenXKlient, rekrutteringstreffAudience)
        val borgerKlient = BorgerKlient(rekrutteringstreffUrl, tokenXKlient, rekrutteringstreffAudience)
        javalin.rekrutteringstreffendepunkt(rekrutteringstreffKlient)
        javalin.rekrutteringstreffSvarEndepunkt(rekrutteringstreffKlient, borgerKlient)
        javalin.arbeidsgiverendepunkt(rekrutteringstreffKlient)
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
    val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    App(
        port = 8080,
        rekrutteringstreffUrl = "http://rekrutteringstreff-api",
        rekrutteringstreffAudience = env("REKRUTTERINGSTREFF_AUDIENCE"),
        tokenXKlient = TokenXKlient(
            clientId = env("TOKEN_X_CLIENT_ID"),
            privateJwk = env("TOKEN_X_PRIVATE_JWK"),
            tokenEndpoint = env("TOKEN_X_TOKEN_ENDPOINT"),
            issuer = env("TOKEN_X_ISSUER"),
            httpClient = httpClient
        ),
        authConfigs = listOf(
            AuthenticationConfiguration(
                audience = env("TOKEN_X_CLIENT_ID"),
                issuer = env("TOKEN_X_ISSUER"),
                jwksUri = env("TOKEN_X_JWKS_URI")
            )
        )
    ).start()
}

private fun env(key: String) = System.getenv(key) ?: throw RuntimeException("Missing environment variable $key")
