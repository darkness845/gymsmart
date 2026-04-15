import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    kotlin("plugin.serialization") version "2.3.0"
}

group = "com.gymsmart.gymsmart"
version = "1.0.0"
application {
    mainClass.set("com.gymsmart.gymsmart.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.clientLogging)
    implementation("io.ktor:ktor-server-sessions:3.3.3")
    implementation("io.ktor:ktor-client-core:3.3.3")
    implementation("io.ktor:ktor-client-cio:3.3.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
    implementation("org.mindrot:jbcrypt:0.4")
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

// Leer variables del .env
val envFile = rootProject.file(".env")
val envProps = Properties()
if (envFile.exists()) {
    envFile.readLines()
        .filter { it.contains("=") && !it.startsWith("#") }
        .forEach { line ->
            val (key, value) = line.split("=", limit = 2)
            envProps[key.trim()] = value.trim()
        }
}

tasks.named<JavaExec>("run") {
    environment("TURSO_DB_URL", "https://gymsmart-terockd.aws-eu-west-1.turso.io")
    environment("TURSO_AUTH_TOKEN", "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhIjoicnciLCJpYXQiOjE3NzYyNzUzMTYsImlkIjoiMDE5Y2NkYjUtOTIwMS03ZjA2LTg2N2YtNWUzODM5MmI1YjJmIiwicmlkIjoiMTI0NTRhNjMtZDRhMi00YzljLTlkNjktYTIyZGMzNjQ0MDA2In0.6ojNyBNmlg8X93axj-roHhBSH9WuOnwblnB1RW3ETa1MBlcOD5DM-OFgjfOvcomcfYGWnA1WiLtziYRR8QwZAw")
}