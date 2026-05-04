package no.nav.toi.config

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.json.JavalinJackson
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import no.nav.toi.ExceptionMapping.exceptionMapping
import no.nav.toi.HealthController
import no.nav.toi.HealthRepository
import no.nav.toi.JacksonConfig
import no.nav.toi.leggTilAutensieringPåRekrutteringstreffEndepunkt
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import org.koin.dsl.module
import javax.sql.DataSource

val javalinModule get() = module {
    single { HealthRepository(get<DataSource>()) }
    single { HealthController(get<Javalin>(), get<HealthRepository>()) }

    single {
        val config = get<AppConfig>()
        Javalin.create { javalinConfig ->
            javalinConfig.jsonMapper(JavalinJackson(JacksonConfig.mapper))
            configureOpenApi(javalinConfig)
        }.also { javalin ->
            javalin.exceptionMapping()
            javalin.leggTilAutensieringPåRekrutteringstreffEndepunkt(
                authConfigs = config.authConfigs,
                rolleUuidSpesifikasjon = config.rolleUuidSpesifikasjon,
                modiaKlient = get<ModiaKlient>(),
                pilotkontorer = config.pilotkontorer,
            )
        }
    }
}

private fun configureOpenApi(config: JavalinConfig) {
    val openApiPlugin = OpenApiPlugin { openApiConfig ->
        openApiConfig.withDefinitionConfiguration { _, definition ->
            definition.withInfo { info ->
                info.title = "Rekrutteringstreff API"
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
