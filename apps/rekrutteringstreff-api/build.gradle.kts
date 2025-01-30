plugins {
    id("toi.common")
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply true

}

val javalinVersion = "6.4.0"


dependencies {
    implementation("io.javalin:javalin:$javalinVersion")
    ksp("io.javalin.community.openapi:openapi-annotation-processor:6.4.0")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:6.4.0")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:6.4.0")
    implementation("io.javalin.community.openapi:openapi-specification:6.4.0")
}