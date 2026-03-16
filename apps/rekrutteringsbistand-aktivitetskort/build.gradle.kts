plugins {
    id("toi.rapids-and-rivers")
    kotlin("kapt")
}

val flywayVersion = "11.3.0"
val postgresVersion = "42.7.5"
val hikariVersion = "6.2.1"
val testContainerVersion = "1.21.4"
val opentelemetryLogbackMdcVersion = "2.26.0-alpha"
val openTelemetryAnnotationsVersion = "2.26.0"

dependencies {
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:${opentelemetryLogbackMdcVersion}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:${openTelemetryAnnotationsVersion}")
    
    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
}
