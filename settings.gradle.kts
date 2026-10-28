rootProject.name = "rekrutteringstreff-backend"

// https://docs.gradle.org/current/userguide/configuration_cache_enabling.html#config_cache:stable
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

include(
    "apps:rekrutteringstreff-api", "apps:rekrutteringstreff-minside-api", "apps:rekrutteringsbistand-aktivitetskort", "technical-libs:testrapid", "technical-libs:logging"
)
