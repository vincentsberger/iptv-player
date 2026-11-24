import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("uk.co.caprica:vlcj:4.7.3")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // UI-Tests ohne Fenster: ./gradlew :desktopApp:test
    testImplementation(project(":testing"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "de.vincentsberger.iptvplayer.desktop.MainKt"
        // Version für die Anzeige in den Einstellungen (gilt für `run` und das fertige Paket)
        jvmArgs += "-Diptvplayer.version=${providers.gradleProperty("appVersion").get()}"

        nativeDistributions {
            // Deb: Linux-Paket fürs GitHub-Release; nutzt das VLC des Systems (Paket „vlc“).
            // Portable Windows-Version: `createReleaseDistributable` → Ordner zippen (release.yml), kein eigenes Format.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            // Sichtbarer Name (Programm, Startmenü, Dateinamen). Die Datenordner heißen weiter „FreeIPTVPlayer“
            // (Platform.desktop.kt) – sonst wären alle nach dem Update abgemeldet.
            packageName = "IPTVPlayer"
            packageVersion = providers.gradleProperty("appVersion").get()
            description = "IPTV-Player für Live TV, Filme und Serien"
            vendor = "Vincent Simonsberger"
            copyright = "© 2026 Vincent Simonsberger"
            // Hier liegt die mitgelieferte VLC-Engine (siehe README).
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            modules("jdk.unsupported", "java.naming")

            linux {
                iconFile.set(project.file("packaging/icon.png"))
                // Debian-Paketname bleibt: so ersetzt `apt install ./freeiptvplayer_*.deb` die alte Installation,
                // statt ein zweites Paket daneben zu legen.
                packageName = "freeiptvplayer"
                menuGroup = "AudioVideo"
                appCategory = "video"
                debMaintainer = "Vincent Simonsberger <vincentsberger@proton.me>"
            }

            windows {
                iconFile.set(project.file("packaging/icon.ico"))
                menuGroup = "IPTVPlayer"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Einmalig eine eigene UUID erzeugen (PowerShell: [guid]::NewGuid()) und hier eintragen.
                upgradeUuid = "3f6a2d1e-8b7c-4e2a-9d51-6c0f4b7a1e93"
            }
        }

        // ProGuard aus: verträgt sich nicht gut mit VLC (JNA).
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
