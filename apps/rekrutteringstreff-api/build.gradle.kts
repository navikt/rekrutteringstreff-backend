plugins {
    kotlin("jvm")
    application
}

val javalinVersion = "6.4.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:$javalinVersion")
}