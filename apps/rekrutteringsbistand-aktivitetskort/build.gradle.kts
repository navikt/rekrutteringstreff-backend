plugins {
    id("toi.common")
    kotlin("kapt")
}

val flywayVersion = "11.3.0"
val postgresVersion = "42.7.5"
val hikariVersion = "6.2.1"
val testContainerVersion = "1.20.4"

repositories {
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    
    testImplementation("org.testcontainers:testcontainers:$testContainerVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    implementation("com.github.navikt:rapids-and-rivers:2025010715371736260653.d465d681c420")
    testImplementation(project(":technical-libs:testrapid"))
    implementation("io.micrometer:micrometer-registry-prometheus:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
