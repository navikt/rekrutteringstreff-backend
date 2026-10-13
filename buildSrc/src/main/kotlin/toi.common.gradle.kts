import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

val slf4jVersion = "2.0.18"
val logbackVersion = "1.6.1"
val logstashLogbackEncoderVersion = "9.0"
val junitJupiterVersion = "5.14.4"
val junitPlatformVersion = "1.14.4"
val assertjVersion = "3.27.7"

// Sikkerhetsfikser for transitive avhengigheter (logstash-logback-encoder og rapids-and-rivers).
// Fjern når de som drar dem inn bruker rettede versjoner.
val jackson3Version = "3.2.3"
val micrometerVersion = "1.16.7"
val lz4JavaVersion = "1.11.4"

dependencies {
    constraints {
        // Alternativer til require er strictly og preferred.
        // Se https://docs.gradle.org/current/userguide/dependency_versions.html#sec:rich-version-constraints

        implementation("org.slf4j:slf4j-api") {
            version { require(slf4jVersion) }
        }
        implementation("ch.qos.logback:logback-classic") {
            version { require(logbackVersion) }
        }
        implementation("net.logstash.logback:logstash-logback-encoder") {
            version { require(logstashLogbackEncoderVersion) }
        }
        implementation("tools.jackson.core:jackson-core") {
            version { require(jackson3Version) }
        }
        implementation("tools.jackson.core:jackson-databind") {
            version { require(jackson3Version) }
        }
        implementation("io.micrometer:micrometer-core") {
            version { require(micrometerVersion) }
        }
        implementation("io.micrometer:micrometer-registry-prometheus") {
            version { require(micrometerVersion) }
        }
        implementation("at.yawk.lz4:lz4-java") {
            version { require(lz4JavaVersion) }
        }
        testImplementation("org.junit.jupiter:junit-jupiter-api") {
            version { require(junitJupiterVersion) }
        }
        testImplementation("org.junit.jupiter:junit-jupiter-params") {
            version { require(junitJupiterVersion) }
        }
        testImplementation("org.assertj:assertj-core") {
            version { require(assertjVersion) }
        }
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine") {
            version { require(junitJupiterVersion) }
        }
        testRuntimeOnly("org.junit.platform:junit-platform-launcher") {
            version { require(junitPlatformVersion) }
        }
    }

    implementation(kotlin("stdlib"))

    implementation("org.slf4j:slf4j-api")
    implementation("ch.qos.logback:logback-classic")
    implementation("net.logstash.logback:logstash-logback-encoder")

    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}
