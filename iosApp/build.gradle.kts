// iPhone-App: Oberfläche in Compose Multiplatform, als statisches Framework „IptvApp“ für das Xcode-Projekt
// unter iosApp/xcode (erzeugt per XcodeGen, siehe tools/ios/build-ipa.sh). Baut nur auf macOS.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // MobileVLCKit (tools/ios/fetch-vlckit.sh): je Target das passende Teil des xcframeworks.
    val vlckit = layout.projectDirectory.dir("vlckit/MobileVLCKit.xcframework")
    mapOf(iosArm64() to "ios-arm64_armv7_armv7s", iosSimulatorArm64() to "ios-arm64_x86_64-simulator").forEach { (target, slice) ->
        val frameworks = vlckit.dir(slice).asFile.absolutePath
        target.compilations.getByName("main").cinterops.create("vlckit") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/vlckit.def"))
            compilerOpts("-F$frameworks")
        }
        target.binaries.framework {
            baseName = "IptvApp"
            isStatic = true // VLCKit selbst linkt erst Xcode – als eingebettetes, dynamisches Framework (LGPL)
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
        iosMain.dependencies {
            implementation(project(":iosUi"))
        }
    }
}
