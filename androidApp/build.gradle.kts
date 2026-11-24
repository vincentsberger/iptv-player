plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.vincentsberger.iptvplayer.app"
    compileSdk = 35

    defaultConfig {
        // WICHTIG: vor dem ersten Store-Upload auf eine eigene, eindeutige ID ändern
        // (hier: de.vincentsberger.iptvplayer). Sie kann danach nie mehr geändert werden.
        applicationId = "de.vincentsberger.iptvplayer"
        minSdk = 22          // Fire OS 5 (ältere Fire TV Sticks) und neuer
        targetSdk = 35
        // versionCode aus appVersion ableiten (1.3.0 → 10300): steigt mit jeder Version von selbst,
        // Play Store und Android-Updates verlangen das.
        val appVersion = providers.gradleProperty("appVersion").get()
        versionCode = appVersion.split('.').map(String::toInt).let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release-Signatur: nur über Umgebungsvariablen, die tools/release/build-release.sh aus Proton Pass setzt
    // (Vault „App-Signierung“, Item „FreeIPTVPlayer Signierung“). Schlüssel und Passwörter liegen nie im Repo.
    val keystorePath: String? = System.getenv("IPTV_KEYSTORE")
    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("IPTV_STORE_PASSWORD")
                keyAlias = System.getenv("IPTV_KEY_ALIAS")
                keyPassword = System.getenv("IPTV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Ohne Schlüssel entsteht eine unsignierte APK (z. B. für einen Test-Build in CI).
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        // java.time & Co. auch auf Fire OS 5/6 (Android < 8) – sonst Absturz beim Datumsformat
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint { disable += "UnsafeOptInUsageError" }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.tvprovider:tvprovider:1.0.0")         // Weiterschauen auf dem Startbildschirm (Watch Next)

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")   // MediaSession: Alexa, Medientasten, „Läuft gerade“
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-database:$media3")       // Download-Verzeichnis (offline ansehen)

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")       // QR-Code für die Anmeldung vom Handy

    // UI-Tests auf Emulator oder Gerät: ./gradlew :androidApp:connectedDebugAndroidTest
    androidTestImplementation(project(":testing"))
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
