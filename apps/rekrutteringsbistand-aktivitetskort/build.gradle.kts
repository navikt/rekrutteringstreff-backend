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
val tbdLibsVersion = "2026.01.28-07.21-5436e475"
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

    // Rapids and rivers fra tbd-libs (uten Ktor)
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers:${tbdLibsVersion}")
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers-api:${tbdLibsVersion}")
    implementation("com.github.navikt.tbd-libs:kafka:${tbdLibsVersion}")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:${tbdLibsVersion}")

    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testContainerVersion")
}
