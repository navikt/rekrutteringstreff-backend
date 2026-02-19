plugins {
    id("toi.rapids-and-rivers")
}

val flywayVersion = "11.3.0"
val postgresVersion = "42.7.10"
val hikariVersion = "6.2.1"
val testContainerVersion = "2.0.3"
val postgresTestContainerVersion = "1.21.4"
val javalinVersion = "6.7.0"
val micrometerVersion = "1.15.2"
val tbdLibsVersion = "2026.01.28-07.21-5436e475"

dependencies {
    implementation("io.javalin:javalin:${javalinVersion}")
    implementation("io.javalin:javalin-micrometer:${javalinVersion}")
    implementation("io.prometheus:simpleclient_common:0.16.0")
    implementation("io.micrometer:micrometer-core:${micrometerVersion}")
    implementation("io.micrometer:micrometer-registry-prometheus:${micrometerVersion}")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")

    // Rapids and rivers fra tbd-libs (uten Ktor)
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers:${tbdLibsVersion}")
    implementation("com.github.navikt.tbd-libs:rapids-and-rivers-api:${tbdLibsVersion}")
    implementation("com.github.navikt.tbd-libs:kafka:${tbdLibsVersion}")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:${tbdLibsVersion}")

    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:postgresql:$postgresTestContainerVersion")
}
