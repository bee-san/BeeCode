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

// Platform-neutral Python execution contracts and the shared harness.
include(":python-api")

// Local SQLite persistence: schema, migrations, and the exactly-once review
// finalization transaction.
include(":persistence")

// Problem content loading, validation, and pack compilation.
include(":content-tools")

// Application services shared by desktop and Android: study loop, statistics,
// achievements.
include(":shared")

// Further modules are added here as each vertical slice lands, so the build
// always configures cleanly rather than referencing planned-but-empty
// directories.
