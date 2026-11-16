// Test-Hilfen (Attrappen für HTTP und Speicher, Beispieldaten), die von den Tests
// aller Module genutzt werden: shared (commonTest), androidApp (androidTest), desktopApp (test).
// Wird nie in eine App eingebaut.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
    }
}

android {
    namespace = "de.tvplayer.testing"
    compileSdk = 35
    defaultConfig { minSdk = 22 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
