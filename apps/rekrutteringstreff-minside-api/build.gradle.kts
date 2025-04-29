plugins {
    id("toi.common")
    kotlin("kapt")
}

val javalinVersion = "6.4.0"
val fuelVersion = "2.3.1"
val mockOAuth2ServerVersion = "2.1.0"
val flywayVersion = "11.3.0"
val postgresVersion = "42.7.5"
val hikariVersion = "6.2.1"
val testContainerVersion = "1.20.4"
val swaggerParserVersion = "2.1.12"
val nimbusVersion = "10.0.1"
val wiremockVersion = "3.12.0"

dependencies {
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-jackson:$fuelVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("io.javalin:javalin:$javalinVersion")

    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:openapi-specification:$javalinVersion")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    kapt("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")

    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation(project(":apps:rekrutteringstreff-api"))
    testImplementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")


}