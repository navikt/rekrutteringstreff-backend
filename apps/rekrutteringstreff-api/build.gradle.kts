import org.gradle.api.tasks.testing.Test

plugins {
    id("toi.rapids-and-rivers")
    kotlin("kapt")
    id("io.github.ben-manes.versions") version "0.64.0"
}

application {
    mainClass.set("no.nav.toi.AppKt")
}

val javalinVersion = "7.2.3"
val mockOAuth2ServerVersion = "6.0.3"
val flywayVersion = "13.8.0"
val postgresVersion = "42.7.13"
val hikariVersion = "7.1.0"
val testContainerVersion = "2.0.5"
val swaggerParserVersion = "2.1.48"
val nimbusVersion = "10.10"
val wiremockVersion = "3.13.2"
val jacksonVersion = "2.22.3"
val resilience4jVersion = "2.4.0"
val navCommonVersion = "4.2026.09.24_06.17-80dfc0eacb29"
val openTelemetryVersion = "1.62.0" // Følg SDK-versjonen til Nais-agenten, se https://doc.nais.io/observability/reference/auto-config/
val opentelemetryLogbackMdcVersion = "2.28.1-alpha" // Følg agentversjonen til Nais
val openTelemetryAnnotationsVersion = "2.28.1" // Følg agentversjonen til Nais
val kotestVersion = "6.2.5"

dependencies {
    implementation(project(":technical-libs:logging"))
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310") // JavaTimeModule
    implementation("io.javalin:javalin:$javalinVersion")

    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:openapi-specification:$javalinVersion")
    implementation("com.auth0:java-jwt:4.6.1")
    implementation("com.auth0:jwks-rsa:0.24.1")
    kapt("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")
    implementation("org.ehcache:ehcache:3.12.0")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")
    implementation("no.nav.common:audit-log:$navCommonVersion")
    implementation("io.opentelemetry:opentelemetry-api:$openTelemetryVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$openTelemetryAnnotationsVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:${opentelemetryLogbackMdcVersion}")

    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testContainerVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.kotest:kotest-assertions-json-jvm:$kotestVersion")
}