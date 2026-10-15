import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(25)
}

val navGithubPakkespeil = "https://github-package-registry-mirror.gc.nav.no/cached/maven-release"

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    // Innholdsfilter: disse gruppene hentes bare fra repoene under, og repoene leverer ikke noe annet.
    exclusiveContent {
        forRepositories(
            maven(navGithubPakkespeil) { name = "navGithubPakkespeil" },
            maven("https://jitpack.io") { name = "jitpack" },
        )
        filter { includeGroupByRegex("""com\.github\.navikt(\..+)?""") }
    }
    exclusiveContent {
        forRepository { maven(navGithubPakkespeil) { name = "navGithubPakkespeilNav" } }
        filter {
            includeGroup("no.nav.common")
            includeModuleByRegex("""no\.nav\.security""", "token-.*")
        }
    }
}

val slf4jVersion = "2.0.18"
val logbackVersion = "1.6.1"
val logstashLogbackEncoderVersion = "9.0"
val junitJupiterVersion = "5.14.4"
val junitPlatformVersion = "1.14.4"
val assertjVersion = "3.27.7"

val jackson3Version = "3.2.3"
// Jackson 2 er bare tillatt via javalin-openapi-plugin (se SjekkJackson2Task). Låst til siste 2.x for sikkerhetsfikser.
val jackson2Version = "2.22.3"

// Sikkerhetsfikser for transitive avhengigheter (logstash-logback-encoder og rapids-and-rivers).
// Fjern når de som drar dem inn bruker rettede versjoner.
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
        listOf(
            "com.fasterxml.jackson.core:jackson-core",
            "com.fasterxml.jackson.core:jackson-databind",
            "com.fasterxml.jackson.module:jackson-module-kotlin",
        ).forEach {
            implementation(it) {
                version { require(jackson2Version) }
            }
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

    implementation(platform("tools.jackson:jackson-bom:$jackson3Version"))
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

val sjekkJackson2 = tasks.register<SjekkJackson2Task>("sjekkJackson2") {
    description = "Feiler hvis Jackson 2 er på klassestien eller brukes i kildekoden."
    group = "verification"
    val klassestier = listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
    klassestier.forEach { navn ->
        configurations.findByName(navn)?.let { konfigurasjon ->
            rotkomponenter.put(navn, konfigurasjon.incoming.resolutionResult.rootComponent)
        }
    }
    kildefiler.from(fileTree("src") { include("**/*.kt") })
}

tasks {
    named("check") { dependsOn(sjekkJackson2) }
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}
