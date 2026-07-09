plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.favasur"
version = "0.1.0"

repositories {
    mavenCentral()
    // Hytale API maven repository (official or community-maintained)
    maven { url = uri("https://maven.hytale.com/releases") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Hytale Server API - adjust version as needed for current Early Access build
    compileOnly("com.hytale:server-api:1.0.0")
    
    // Optional: configuration library
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
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
                "plugin_name" to "Cave Horror - White Eyes",
                "plugin_description" to "A cave-dwelling horror mod - Cave Noise Nightmare, ported to Hytale"
            )
        }
    }

    shadowJar {
        archiveBaseName.set("cave-horror-white-eyes")
        archiveClassifier.set("")
        archiveVersion.set(version.toString())
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}
