import ca.cutterslade.gradle.analyze.AnalyzeDependenciesTask

plugins {
    `kotlin-dsl`
    id("ca.cutterslade.analyze") version "1.9.0" apply true
}

group = "no.nav.toi"
version = "unspecified"

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    withType<Wrapper> {
        gradleVersion = "8.11.1"
    }

    withType<AnalyzeDependenciesTask> {
        warnUsedUndeclared = true
        warnUnusedDeclared = true
    }
}
