// AGP 8.13 bringt ein D8/R8 mit, das Kotlin-2.4-Metadaten nicht lesen kann (Warnflut, Metadaten im Release kaputt).
// Neueres R8 vor AGP auf den Klassenpfad legen – entfällt mit dem Umstieg auf AGP 9.
buildscript {
    repositories { google() }
    dependencies { classpath("com.android.tools:r8:9.5.33") }
}

// Versionen zentral an einer Stelle; die Module wenden die Plugins an.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

// Statische Analyse über alle Module in einem Lauf (ohne Typauflösung – schnell, unabhängig von KMP-Zielen).
// Bestehende Befunde stehen in der Baseline; neue lassen die CI scheitern. Neu einfrieren: ./gradlew detektBaseline
detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
    baseline = file("config/detekt/baseline.xml")
    parallel = true
    source.setFrom(
        fileTree(rootDir) {
            include("*/src/**/*.kt")
            exclude("**/build/**")
        },
    )
}
