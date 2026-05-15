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
    implementation("com.sun.mail:jakarta.mail:2.0.1")
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
    environment("TURSO_DB_URL", envProps["TURSO_DB_URL"] ?: "")
    environment("TURSO_AUTH_TOKEN", envProps["TURSO_AUTH_TOKEN"] ?: "")
    environment("RESEND_API_KEY", envProps["RESEND_API_KEY"] ?: "")
    environment("GMAIL_USER", envProps["GMAIL_USER"] ?: "")
    environment("GMAIL_PASS", envProps["GMAIL_PASS"] ?: "")
    environment("OPENAI_API_KEY", envProps["OPENAI_API_KEY"] ?: "")
    environment("STRIPE_SECRET_KEY", envProps["STRIPE_SECRET_KEY"] ?: "")
    environment("BREVO_USER", envProps["BREVO_USER"] ?: "")
    environment("BREVO_SMTP_KEY", envProps["BREVO_SMTP_KEY"] ?: "")
}