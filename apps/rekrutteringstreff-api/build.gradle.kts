plugins {
    id("toi.rapids-and-rivers")
    kotlin("kapt")
}

val javalinVersion = "6.4.0"
val mockOAuth2ServerVersion = "2.1.0"
val flywayVersion = "11.3.0"
val postgresVersion = "42.7.5"
val hikariVersion = "6.2.1"
val testContainerVersion = "1.21.3"
val swaggerParserVersion = "2.1.12"
val nimbusVersion = "10.0.1"
val wiremockVersion = "3.12.0"
val jacksonDatatypeJsr310Version = "2.18.2" // JavaTimeModule, se https://github.com/FasterXML/jackson-modules-java8
val resilience4jVersion = "2.2.0"
val navCommonVersion = "3.2024.11.26_16.35-432a29107830"

dependencies {
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonDatatypeJsr310Version")
    implementation("io.javalin:javalin:$javalinVersion")

    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:openapi-specification:$javalinVersion")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    kapt("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")
    implementation("org.ehcache:ehcache:3.10.8")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")
    implementation("no.nav.common:audit-log:$navCommonVersion")

    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation("io.mockk:mockk:1.13.17")
}
