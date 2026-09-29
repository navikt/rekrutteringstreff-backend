plugins {
    id("toi.rapids-and-rivers")
    id("io.github.ben-manes.versions") version "0.64.0"
}

application {
    mainClass.set("no.nav.toi.AppKt")
}

val flywayVersion = "11.20.3"
val postgresVersion = "42.7.13"
val hikariVersion = "6.2.1"
val testContainerVersion = "2.0.5"
val javalinVersion = "7.2.3"
val micrometerVersion = "1.17.1"
val rapidsAndRiversVersion = "2026042913501777463400" // Må være lik versjonen i buildSrc/toi.rapids-and-rivers.gradle.kts
val opentelemetryLogbackMdcVersion = "2.26.0-alpha"
val openTelemetryAnnotationsVersion = "2.26.1"

dependencies {
    implementation(project(":technical-libs:logging"))
    implementation("io.javalin:javalin:${javalinVersion}")
    implementation("io.javalin:javalin-micrometer:${javalinVersion}")
    implementation("io.prometheus:simpleclient_common:0.16.0")
    implementation("io.micrometer:micrometer-core:${micrometerVersion}")
    implementation("io.micrometer:micrometer-registry-prometheus:${micrometerVersion}")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("com.auth0:java-jwt:4.6.1")
    implementation("com.auth0:jwks-rsa:0.24.1")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:${opentelemetryLogbackMdcVersion}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:${openTelemetryAnnotationsVersion}")

    // Rapids and rivers uten Ktor.
    implementation("com.github.navikt.rapids-and-rivers:rapids-and-rivers-impl:${rapidsAndRiversVersion}")
    implementation("com.github.navikt.rapids-and-rivers:rapids-and-rivers-api:${rapidsAndRiversVersion}")
    implementation("com.github.navikt.rapids-and-rivers:kafka:${rapidsAndRiversVersion}")
    testImplementation("com.github.navikt.rapids-and-rivers:rapids-and-rivers-test:${rapidsAndRiversVersion}")

    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testContainerVersion")
}
