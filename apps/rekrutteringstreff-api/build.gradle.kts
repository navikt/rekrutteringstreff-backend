plugins {
    id("toi.common")
    kotlin("kapt")
}

val javalinVersion = "6.4.0"
val swaggerParserVersion = "2.1.12"

dependencies {
    implementation("io.javalin:javalin:$javalinVersion")
    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:openapi-specification:$javalinVersion")
    kapt("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion")
}