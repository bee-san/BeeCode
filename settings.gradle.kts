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

// Pure domain: models, state machines, and policy. No UI, SQL, HTTP, or Python.
include(":domain")

// BeeCode's review policy wrapped around the bee-fsrs engine. The only module
// that depends on dev.bee.fsrs.
include(":fsrs-adapter")

// Further modules are added here as each vertical slice lands, so the build
// always configures cleanly rather than referencing planned-but-empty
// directories.
