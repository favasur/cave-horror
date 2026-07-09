pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "AzureDoom Maven"
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "cave-horror-white-eyes"
