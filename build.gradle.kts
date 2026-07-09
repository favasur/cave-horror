plugins {
    idea
    java
    id("com.azuredoom.hytale-tools") version "1.+"
}

group = "com.favasur"
version = project.findProperty("plugin_version") ?: "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

hytaleTools {
    // Configuration is pulled from gradle.properties automatically
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
    }

    processResources {
        filesMatching("manifest.json") {
            expand(
                "plugin_version" to version,
                "plugin_name" to project.findProperty("plugin_name") ?: "Cave Horror - White Eyes",
                "plugin_description" to project.findProperty("plugin_description") ?: "A cave-dwelling horror mod, ported to Hytale"
            )
        }
    }

    jar {
        archiveBaseName.set("cave-horror-white-eyes")
        archiveVersion.set(version.toString())
    }
}
