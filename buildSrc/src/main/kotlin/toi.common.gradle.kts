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

// Versjonene ligger i gradle/libs.versions.toml, så Dependabot kan oppdatere dem
val libs = the<VersionCatalogsExtension>().named("libs")
fun versjon(navn: String): String = libs.findVersion(navn).get().requiredVersion

val slf4jVersion = versjon("slf4j")
val logbackVersion = versjon("logback")
val logstashLogbackEncoderVersion = versjon("logstash-logback-encoder")
val junitJupiterVersion = versjon("junit-jupiter")
val junitPlatformVersion = versjon("junit-platform")
val assertjVersion = versjon("assertj")

// Sikkerhetsfikser for transitive avhengigheter, se kommentar i gradle/libs.versions.toml
val jackson3Version = versjon("jackson3")
val micrometerVersion = versjon("micrometer")
val lz4JavaVersion = versjon("lz4-java")
val snappyJavaVersion = versjon("snappy-java")
val guavaVersion = versjon("guava")

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
        implementation("org.xerial.snappy:snappy-java") {
            version { require(snappyJavaVersion) }
        }
        implementation("com.google.guava:guava") {
            version { require(guavaVersion) }
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
