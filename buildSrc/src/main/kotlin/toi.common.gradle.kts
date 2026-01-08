import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

val jupiterVersion = "5.11.4"

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$jupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

tasks {
    named<Jar>("jar") {
        if (!projectDir.absoluteFile.toString().contains("technical-libs")) {
            archiveBaseName.set("app")

            manifest {
                val stiTilApplicationClass = File("${projectDir}/src/main/kotlin")
                    .walk()
                    .find { it.name == "App.kt" }
                    ?.path?.removePrefix("${project.projectDir}/src/main/kotlin/")
                    ?.replace("/", ".")
                    ?.replace(".kt", "Kt")
                    ?: throw Exception("Finner ingen App.kt i prosjektet ${project.name}")
                attributes["Main-Class"] = stiTilApplicationClass
                attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                    it.name
                }
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("$buildDir/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}
