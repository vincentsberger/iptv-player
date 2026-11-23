plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            // Nur die Compose-Laufzeit (State), keine Oberfläche – die bleibt pro Plattform.
            api("org.jetbrains.compose.runtime:runtime:1.9.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        androidMain.dependencies {
            api("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
        }
        iosMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.3")
        }
        val desktopMain by getting {
            dependencies {
                api("com.squareup.okhttp3:okhttp:4.12.0")
                // DPAPI (Windows-Verschlüsselung) für den Speicher der Zugangsdaten
                implementation("net.java.dev.jna:jna-platform:5.11.0")
            }
        }
    }
}

android {
    namespace = "de.vincentsberger.iptvplayer.shared"
    compileSdk = 35
    defaultConfig { minSdk = 22 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
