import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    kotlin("plugin.serialization") version "2.3.0"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.clientAndroid)
            implementation("io.ktor:ktor-client-okhttp:3.3.3")

            implementation("com.google.android.gms:play-services-location:21.3.0")
            implementation("org.osmdroid:osmdroid-android:6.1.18")

            // ✅ HEALTH CONNECT (FIX FINAL)
            implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

            // CameraX
            implementation("androidx.camera:camera-camera2:1.4.2")
            implementation("androidx.camera:camera-lifecycle:1.4.2")
            implementation("androidx.camera:camera-view:1.4.2")

            // ML Kit
            implementation("com.google.mlkit:barcode-scanning:17.3.0")

            // Guava (necesario para ListenableFuture de CameraX)
            implementation("com.google.guava:guava:33.4.0-android")

            implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)

            implementation(libs.navigation.compose)

            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)

            implementation(libs.kotlinx.serialization.json)

            implementation(compose.materialIconsExtended)

            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.0")

            implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.clientJava)
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.3.3")
        }
    }
}

android {
    namespace = "com.gymsmart.gymsmart"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.gymsmart.gymsmart"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/gradle/incremental.annotation.processors"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.gymsmart.gymsmart.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.gymsmart.gymsmart"
            packageVersion = "1.0.0"
        }
    }
}