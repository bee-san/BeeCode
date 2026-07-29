pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "BeeCode"

// Pure Kotlin/JVM: FSRS memory math, vendored with provenance from kanji_anki.
include(":bee-fsrs")

// Modules are added here as each vertical slice lands, so the build always
// configures cleanly rather than referencing planned-but-empty directories.
