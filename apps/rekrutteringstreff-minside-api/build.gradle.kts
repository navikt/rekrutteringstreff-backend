plugins {
    id("toi.rapids-and-rivers")
    kotlin("kapt")
}

application {
    mainClass.set("no.nav.toi.minside.AppKt")
}

val javalinVersion = "7.2.3"
val mockOAuth2ServerVersion = "6.0.8"
val flywayVersion = "13.10.0"
val postgresVersion = "42.7.5"
val hikariVersion = "6.3.3"
val testContainerVersion = "1.21.4"
val swaggerParserVersion = "2.1.48"
val nimbusVersion = "10.10"
val tokenValidationVersion = "6.0.13"
val wiremockVersion = "3.13.2"
val opentelemetryLogbackMdcVersion = "2.28.1-alpha" // Følg agentversjonen til Nais

dependencies {
    implementation(project(":technical-libs:logging"))

    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.javalin:javalin:$javalinVersion")

    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:openapi-specification:$javalinVersion")
    implementation("no.nav.security:token-validation-core:$tokenValidationVersion")
    kapt("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:${opentelemetryLogbackMdcVersion}")

    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation(project(":apps:rekrutteringstreff-api"))
    testImplementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("org.flywaydb:flyway-core:${flywayVersion}")
    testImplementation("org.flywaydb:flyway-database-postgresql:${flywayVersion}")
    testImplementation(project(":technical-libs:testrapid"))
    testImplementation(project(":technical-libs:json-karakterisering"))
}
