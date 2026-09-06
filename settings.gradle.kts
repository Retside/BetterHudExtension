pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.typewritermc.com/beta")
        maven("https://maven.typewritermc.com/external")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.typewritermc.com/beta")
        maven("https://maven.typewritermc.com/external")
    }
}

rootProject.name = "BetterHudExtension"

