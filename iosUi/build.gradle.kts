// iPhone-Oberfläche (Compose Multiplatform) ohne VLCKit: kompiliert auch unter Linux
// (`./gradlew :iosUi:compileKotlinIosArm64`), damit Fehler nicht erst in der CI auffallen.
// Die Wiedergabe kommt über PlayerEngine aus :iosApp (MobileVLCKit).
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
        iosMain.dependencies {
            api(project(":shared"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            implementation(compose.materialIconsExtended)
            // Senderlogos: Coil wie in der PC-App, Netz über Ktor mit NSURLSession (Darwin).
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
            implementation("io.ktor:ktor-client-darwin:3.0.1")
        }
    }
}
