plugins {
    alias(libs.plugins.kotlin.jvm)
}

// This module is the only place BeeCode touches bee-fsrs. Everything above it
// speaks in BeeCode's own ReviewRating and ProblemSchedule types, so the
// vendored engine can be upgraded, replaced, or extracted to its own repository
// without a change rippling into the UI or persistence layers.

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":domain"))
    // Not `api`: dev.bee.fsrs types must not leak into consumers of this module.
    implementation(project(":bee-fsrs"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(project(":bee-fsrs"))
}

tasks.test {
    useJUnit()
}
