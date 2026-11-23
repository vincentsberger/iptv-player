pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FreeIPTVPlayer"
include(":shared", ":testing", ":androidApp", ":desktopApp", ":iosApp")
