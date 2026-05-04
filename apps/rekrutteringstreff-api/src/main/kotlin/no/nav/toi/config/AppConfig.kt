package no.nav.toi.config

import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.RolleUuidSpesifikasjon
import java.util.UUID

data class AppConfig(
    val port: Int,
    val authConfigs: List<AuthenticationConfiguration>,
    val rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
    val pilotkontorer: List<String>,
    val azureClientId: String,
    val azureClientSecret: String,
    val azureTokenEndpoint: String,
    val kandidatsokApiUrl: String,
    val kandidatsokScope: String,
    val modiaContextHolderUrl: String,
    val modiaContextHolderScope: String,
) {
    companion object {
        fun fromEnv(): AppConfig {
            fun getenv(key: String) = System.getenv(key)
                ?: throw NullPointerException("Det finnes ingen miljøvariabel med navn [$key]")

            val azureClientId = getenv("AZURE_APP_CLIENT_ID")

            return AppConfig(
                port = 8080,
                authConfigs = listOfNotNull(
                    AuthenticationConfiguration(
                        audience = azureClientId,
                        issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                        jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI"),
                    ),
                    AuthenticationConfiguration(
                        audience = getenv("TOKEN_X_CLIENT_ID"),
                        issuer = getenv("TOKEN_X_ISSUER"),
                        jwksUri = getenv("TOKEN_X_JWKS_URI"),
                    ),
                    if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp")
                        AuthenticationConfiguration(
                            audience = "dev-gcp:toi:rekrutteringstreff-api",
                            issuer = "https://fakedings.intern.dev.nav.no/fake",
                            jwksUri = "https://fakedings.intern.dev.nav.no/fake/jwks",
                        ) else null,
                ),
                rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
                    jobbsøkerrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_JOBBSOKERRETTET")),
                    arbeidsgiverrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET")),
                    utvikler = UUID.fromString(getenv("REKRUTTERINGSBISTAND_UTVIKLER")),
                ),
                pilotkontorer = getenv("PILOTKONTORER").split(",").map { it.trim() },
                azureClientId = azureClientId,
                azureClientSecret = getenv("AZURE_APP_CLIENT_SECRET"),
                azureTokenEndpoint = getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
                kandidatsokApiUrl = getenv("KANDIDATSOK_API_URL"),
                kandidatsokScope = getenv("KANDIDATSOK_API_SCOPE"),
                modiaContextHolderUrl = getenv("MODIACONTEXTHOLDER_URL"),
                modiaContextHolderScope = getenv("MODIACONTEXTHOLDER_SCOPE"),
            )
        }
    }
}